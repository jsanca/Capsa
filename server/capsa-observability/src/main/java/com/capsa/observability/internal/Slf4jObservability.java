package com.capsa.observability.internal;

import com.capsa.observability.api.Observability;
import com.capsa.observability.api.ObservabilityEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Default {@link Observability} implementation.
 *
 * <p>Emits each event through a dedicated SLF4J logger so observability
 * output can be routed to a separate stream from ordinary application logs.
 *
 * <h2>Transaction-aware dispatch</h2>
 *
 * <p>Decision is made from {@code txRegistry.getTransactionStatus()} on every
 * {@link #emit} call:
 *
 * <ul>
 *   <li>{@link Status#STATUS_ACTIVE STATUS_ACTIVE} — the event is buffered
 *       and an interposed {@link Synchronization} (once per transaction) is
 *       registered. {@code afterCompletion(STATUS_COMMITTED)} releases the
 *       buffered events for asynchronous dispatch in emission order.
 *       Rollback and any other completion status silently discard them.</li>
 *   <li>{@link Status#STATUS_NO_TRANSACTION STATUS_NO_TRANSACTION} — the
 *       event is dispatched immediately, preserving the existing
 *       best-effort behavior.</li>
 *   <li>Any other status (notably {@link Status#STATUS_MARKED_ROLLBACK
 *       STATUS_MARKED_ROLLBACK}, but also the transitional and completion
 *       statuses: {@code STATUS_PREPARING}, {@code STATUS_PREPARED},
 *       {@code STATUS_COMMITTING}, {@code STATUS_COMMITTED},
 *       {@code STATUS_ROLLING_BACK}, {@code STATUS_ROLLEDBACK}, and
 *       {@code STATUS_UNKNOWN}) — the event is silently discarded.</li>
 * </ul>
 *
 * <p>The discard branch reflects the invariant:
 *
 * <blockquote>An event associated with business work that has not
 * successfully committed must never be emitted as if that work
 * occurred.</blockquote>
 *
 * <p>Callers never need to know whether a transaction is active.
 *
 * <h2>Asynchronous dispatch</h2>
 *
 * <p>A virtual-thread-per-task executor is the dispatcher. After commit (or
 * immediately when outside a transaction) each event is submitted as an
 * independent task. JSON-B serialization and the sink write both occur inside
 * that task, after the caller's business thread has returned. The caller
 * thread never blocks on logging I/O or serialization.
 *
 * <h2>Best-effort delivery</h2>
 *
 * <p>Three failure modes are absorbed:
 * <ul>
 *   <li><b>Buffer failure</b> — if the transaction registry raises when
 *       buffering, the event falls back to immediate dispatch and the
 *       unexpected situation is logged at {@code WARN}.</li>
 *   <li><b>Enqueue failure</b> — if the executor rejects a task, the
 *       failure is logged at {@code ERROR}. {@link #emit} returns normally.</li>
 *   <li><b>Serialization or sink failure</b> — caught inside the virtual
 *       thread; logged at {@code ERROR} via the application logger; event
 *       dropped. The already-committed business operation is unaffected.</li>
 * </ul>
 *
 * <p>Failures are reported through the ordinary application logger, not
 * through this observability port. Observability failures must not recurse
 * back into the observability path.
 *
 * <h2>Test seam</h2>
 *
 * <p>Tests instantiate this class via
 * {@link #Slf4jObservability(Logger, ExecutorService, Consumer, TransactionSynchronizationRegistry, Jsonb)}
 * with a {@link SynchronousSinkExecutor} and a {@code TestTransactionRegistry}
 * to control transaction lifecycle and observe sink writes synchronously.
 */
@ApplicationScoped
public class Slf4jObservability implements Observability {

    /**
     * Dedicated logger category for observability events. Operators route
     * this category separately from ordinary application logs.
     */
    public static final String LOGGER_NAME = "capsa.observability";

    private static final Object PENDING_KEY = new Object();

    private final Logger errorLog;
    private final ExecutorService dispatcher;
    private final Consumer<String> sinkWriter;
    private final Jsonb jsonb;
    private final TransactionSynchronizationRegistry txRegistry;

    /**
     * Production constructor — wired by CDI.
     */
    @Inject
    public Slf4jObservability(TransactionSynchronizationRegistry txRegistry) {
        this(
            LoggerFactory.getLogger(Slf4jObservability.class),
            Executors.newVirtualThreadPerTaskExecutor(),
            line -> LoggerFactory.getLogger(LOGGER_NAME).info(line),
            txRegistry,
            JsonbBuilder.create(new JsonbConfig()
                .withFormatting(false)
                .withNullValues(true))
        );
    }

    /**
     * Full constructor for tests and alternative wiring.
     *
     * @param errorLog    the standard application logger used for failures
     * @param dispatcher  executor used to run sink writes off the caller thread
     * @param sinkWriter  writes one serialized event line to a sink
     * @param txRegistry  Jakarta transaction registry; drives commit/rollback deferral
     * @param jsonb       JSON-B instance used to serialize events on the async side
     */
    public Slf4jObservability(
            Logger errorLog,
            ExecutorService dispatcher,
            Consumer<String> sinkWriter,
            TransactionSynchronizationRegistry txRegistry,
            Jsonb jsonb) {
        this.errorLog    = Objects.requireNonNull(errorLog,     "errorLog");
        this.dispatcher  = Objects.requireNonNull(dispatcher,   "dispatcher");
        this.sinkWriter  = Objects.requireNonNull(sinkWriter,   "sinkWriter");
        this.txRegistry  = Objects.requireNonNull(txRegistry,   "txRegistry");
        this.jsonb       = Objects.requireNonNull(jsonb,        "jsonb");
    }

    @PreDestroy
    public void onDestroy() {
        dispatcher.close();
        try { jsonb.close(); } catch (Exception ignored) { }
    }

    @Override
    public void emit(ObservabilityEvent event) {
        Objects.requireNonNull(event, "event");
        switch (currentDispatchMode()) {
            case BUFFER  -> bufferInTransaction(event);
            case DISCARD -> { /* event is associated with business work that has not
                                 successfully committed; do not emit. */
                            }
            case DISPATCH -> dispatchAsync(event);
        }
    }

    /**
     * Decision based on the current Jakarta Transaction status. See the class
     * Javadoc §"Transaction-aware dispatch" for the rationale per status.
     *
     * <p>A registry exception is treated as {@link DispatchMode#DISPATCH}: we
     * do not know whether a transaction is in scope, and dispatching (the
     * existing out-of-transaction path) is the safer default than guessing.
     */
    private DispatchMode currentDispatchMode() {
        final int status;
        try {
            status = txRegistry.getTransactionStatus();
        } catch (Exception e) {
            return DispatchMode.DISPATCH;
        }
        return switch (status) {
            case Status.STATUS_ACTIVE         -> DispatchMode.BUFFER;
            case Status.STATUS_NO_TRANSACTION -> DispatchMode.DISPATCH;
            default                           -> DispatchMode.DISCARD;
        };
    }

    private enum DispatchMode { BUFFER, DISCARD, DISPATCH }

    @SuppressWarnings("unchecked")
    private void bufferInTransaction(ObservabilityEvent event) {
        try {
            var pending = (List<ObservabilityEvent>) txRegistry.getResource(PENDING_KEY);
            if (pending == null) {
                pending = new ArrayList<>();
                txRegistry.putResource(PENDING_KEY, pending);
                final List<ObservabilityEvent> toRelease = pending;
                txRegistry.registerInterposedSynchronization(new Synchronization() {
                    @Override public void beforeCompletion() {}
                    @Override public void afterCompletion(int status) {
                        if (status == Status.STATUS_COMMITTED) {
                            for (ObservabilityEvent e : toRelease) {
                                dispatchAsync(e);
                            }
                        }
                        // rollback: toRelease is simply abandoned
                    }
                });
            }
            pending.add(event);
        } catch (RuntimeException e) {
            errorLog.warn("Observability transaction buffering failed; dispatching immediately", e);
            dispatchAsync(event);
        }
    }

    private void dispatchAsync(ObservabilityEvent event) {
        try {
            dispatcher.execute(() -> serializeAndSink(event));
        } catch (RejectedExecutionException e) {
            errorLog.error("Observability dispatcher rejected event {}", event.eventId(), e);
        } catch (RuntimeException e) {
            errorLog.error("Observability enqueue failed for event {}", event.eventId(), e);
        }
    }

    private void serializeAndSink(ObservabilityEvent event) {
        String line;
        try {
            line = jsonb.toJson(event) + '\n';
        } catch (RuntimeException e) {
            errorLog.error("Observability event serialization failed", e);
            return;
        }
        try {
            sinkWriter.accept(line);
        } catch (RuntimeException e) {
            errorLog.error("Observability sink failed; dropping event", e);
        }
    }

    /** Returns the error logger used to report observability failures. */
    public Logger errorLog() {
        return errorLog;
    }

    /** Returns the executor used for asynchronous dispatch; primarily for tests. */
    public ExecutorService dispatcher() {
        return dispatcher;
    }
}
