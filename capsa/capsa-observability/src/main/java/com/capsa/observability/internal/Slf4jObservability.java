package com.capsa.observability.internal;

import com.capsa.observability.api.Observability;
import com.capsa.observability.api.ObservabilityEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Dispatch is asynchronous via a virtual-thread-per-task executor so the
 * caller never blocks on logging I/O.
 *
 * <p>Serialization uses Jakarta JSON-B with compact (non-pretty) output.
 * A single {@link Jsonb} instance is reused — {@code Jsonb} is
 * thread-safe after configuration. The instance is built lazily on first
 * use so that bean construction does not require a JSON-B provider to be
 * on the classpath at deployment time.
 *
 * <h2>Best-effort delivery</h2>
 *
 * <p>Two failure modes are absorbed:
 * <ul>
 *   <li><b>Enqueue failure</b> — if the executor rejects a task (shutdown
 *       or saturated), the failure is logged at {@code ERROR} via the
 *       application logger. {@link #emit(ObservabilityEvent)} still
 *       returns to the caller without throwing.</li>
 *   <li><b>Sink failure</b> — if the sink raises while the virtual thread
 *       is writing the event, the failure is logged at {@code ERROR} via
 *       the application logger. The exception is not propagated.</li>
 * </ul>
 *
 * <p>Both failure modes are reported through the ordinary application
 * logger, not through this observability port. Observability failures must
 * not recurse back into the observability path.
 *
 * <h2>Test seam</h2>
 *
 * <p>Tests instantiate this class via
 * {@link #Slf4jObservability(Logger, ExecutorService, Consumer)}
 * with a synchronous executor (see {@link SynchronousSinkExecutor}) to
 * observe a completed sink write before {@link #emit} returns.
 */
@ApplicationScoped
public class Slf4jObservability implements Observability {

    /**
     * Dedicated logger category for observability events. Operators route
     * this category separately from ordinary application logs.
     */
    public static final String LOGGER_NAME = "capsa.observability";

    private final Logger errorLog;
    private final ExecutorService dispatcher;
    private final Consumer<String> sinkWriter;
    private final Jsonb jsonb;

    /**
     * Production constructor — asynchronous dispatch via virtual threads.
     */
    public Slf4jObservability() {
        this(
            LoggerFactory.getLogger(Slf4jObservability.class),
            Executors.newVirtualThreadPerTaskExecutor(),
            line -> LoggerFactory.getLogger(LOGGER_NAME).info(line)
        );
    }

    /**
     * Constructor for tests and for swapping the sink writer.
     *
     * @param errorLog    the standard application logger used for failures
     * @param dispatcher  executor used to run sink writes off the caller thread
     * @param sinkWriter  writes one serialized event line to a sink (typically the logger)
     */
    public Slf4jObservability(
            Logger errorLog,
            ExecutorService dispatcher,
            Consumer<String> sinkWriter) {
        this.errorLog = Objects.requireNonNull(errorLog, "errorLog");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.sinkWriter = Objects.requireNonNull(sinkWriter, "sinkWriter");
        this.jsonb = JsonbBuilder.create(new JsonbConfig()
            .withFormatting(false)
            .withNullValues(true));
    }

    @Override
    public void emit(ObservabilityEvent event) {
        Objects.requireNonNull(event, "event");
        String line;
        try {
            line = jsonb.toJson(event) + '\n';
        } catch (RuntimeException serializationFailure) {
            errorLog.error("Observability event serialization failed", serializationFailure);
            return;
        }
        try {
            dispatcher.execute(() -> dispatch(line));
        } catch (RejectedExecutionException enqueueFailure) {
            errorLog.error("Observability dispatcher rejected event {}", event.eventId(), enqueueFailure);
        } catch (RuntimeException enqueueFailure) {
            errorLog.error("Observability enqueue failed for event {}", event.eventId(), enqueueFailure);
        }
    }

    private void dispatch(String line) {
        try {
            sinkWriter.accept(line);
        } catch (RuntimeException sinkFailure) {
            errorLog.error("Observability sink failed; dropping event", sinkFailure);
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