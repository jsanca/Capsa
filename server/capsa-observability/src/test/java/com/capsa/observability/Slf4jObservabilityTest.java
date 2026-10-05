package com.capsa.observability;

import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.ObservabilityEvent;
import com.capsa.observability.api.events.CaptureClassified;
import com.capsa.observability.api.events.CaptureNeedsResolution;
import com.capsa.observability.api.events.CaptureResolved;
import com.capsa.observability.api.events.CaptureSubmitted;
import com.capsa.observability.api.events.ClassificationRecorded;
import com.capsa.observability.api.events.ItemCompleted;
import com.capsa.observability.api.events.ItemCreated;
import com.capsa.observability.api.events.ListCreated;
import com.capsa.observability.internal.Slf4jObservability;
import com.capsa.observability.internal.SynchronousSinkExecutor;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Slf4jObservability}'s transaction-aware dispatch and
 * best-effort delivery contracts.
 */
class Slf4jObservabilityTest {

    private static final Logger ERROR_LOG = LoggerFactory.getLogger(Slf4jObservabilityTest.class);

    private static final Jsonb WORKING_JSONB = JsonbBuilder.create(
        new JsonbConfig().withFormatting(false).withNullValues(true));

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static ListCreated sampleEvent() {
        return new ListCreated(UUID.randomUUID(), Instant.now(),
            new EventActor.User(UUID.randomUUID()), UUID.randomUUID(), "Test");
    }

    private static Slf4jObservability obs(List<String> captured, TestTransactionRegistry txReg) {
        return new Slf4jObservability(ERROR_LOG, new SynchronousSinkExecutor(),
            captured::add, txReg, WORKING_JSONB);
    }

    // -----------------------------------------------------------------------
    // Outside-transaction dispatch
    // -----------------------------------------------------------------------

    @Test
    void emitOutsideTransaction_dispatchedImmediately() {
        List<String> captured = new ArrayList<>();
        var txReg = new TestTransactionRegistry(); // STATUS_NO_TRANSACTION
        var obs = obs(captured, txReg);

        obs.emit(sampleEvent());

        assertEquals(1, captured.size());
    }

    // -----------------------------------------------------------------------
    // Inside-transaction dispatch
    // -----------------------------------------------------------------------

    @Test
    void emitInsideTransaction_notDispatchedBeforeCommit() {
        List<String> captured = new ArrayList<>();
        var txReg = new TestTransactionRegistry();
        var obs = obs(captured, txReg);

        txReg.begin();
        obs.emit(sampleEvent());

        assertEquals(0, captured.size(), "event must not reach sink before commit");
    }

    @Test
    void commitReleasesBufferedEvent() {
        List<String> captured = new ArrayList<>();
        var txReg = new TestTransactionRegistry();
        var obs = obs(captured, txReg);

        txReg.begin();
        obs.emit(sampleEvent());
        txReg.commit();

        assertEquals(1, captured.size());
    }

    @Test
    void multipleEventsInOneTransaction_dispatchedInEmissionOrder() {
        List<String> captured = new ArrayList<>();
        var txReg = new TestTransactionRegistry();
        var obs = obs(captured, txReg);

        UUID listA = UUID.randomUUID();
        UUID listB = UUID.randomUUID();
        UUID listC = UUID.randomUUID();

        txReg.begin();
        obs.emit(new ListCreated(UUID.randomUUID(), Instant.now(),
            new EventActor.User(UUID.randomUUID()), listA, "A"));
        obs.emit(new ListCreated(UUID.randomUUID(), Instant.now(),
            new EventActor.User(UUID.randomUUID()), listB, "B"));
        obs.emit(new ListCreated(UUID.randomUUID(), Instant.now(),
            new EventActor.User(UUID.randomUUID()), listC, "C"));
        txReg.commit();

        assertEquals(3, captured.size());
        assertTrue(captured.get(0).contains("\"name\":\"A\""), captured.get(0));
        assertTrue(captured.get(1).contains("\"name\":\"B\""), captured.get(1));
        assertTrue(captured.get(2).contains("\"name\":\"C\""), captured.get(2));
    }

    @Test
    void rollbackDiscardsBufferedEvents() {
        List<String> captured = new ArrayList<>();
        var txReg = new TestTransactionRegistry();
        var obs = obs(captured, txReg);

        txReg.begin();
        obs.emit(sampleEvent());
        obs.emit(sampleEvent());
        txReg.rollback();

        assertEquals(0, captured.size(), "rolled-back events must not reach the sink");
    }

    // -----------------------------------------------------------------------
    // Doomed transaction guard (CAPSA-ARCH-FIX-002A)
    // -----------------------------------------------------------------------

    @Test
    void markedRollbackStatusDiscardsEvent() {
        List<String> captured = new ArrayList<>();
        var txReg = new TestTransactionRegistry();
        var obs = obs(captured, txReg);

        txReg.markRollbackOnly();
        obs.emit(sampleEvent());

        assertEquals(0, captured.size(),
            "STATUS_MARKED_ROLLBACK must not dispatch the event");
    }

    @Test
    void markedRollbackStatusDiscardsEventEvenAfterRollback() {
        List<String> captured = new ArrayList<>();
        var txReg = new TestTransactionRegistry();
        var obs = obs(captured, txReg);

        txReg.markRollbackOnly();
        obs.emit(sampleEvent());
        txReg.rollback();

        assertEquals(0, captured.size(),
            "event emitted under STATUS_MARKED_ROLLBACK must not reach the sink, "
                + "even after the doomed transaction completes");
    }

    @Test
    void emitAfterTransactionMarkedRollbackIsDiscarded() {
        List<String> captured = new ArrayList<>();
        var txReg = new TestTransactionRegistry();
        var obs = obs(captured, txReg);

        txReg.begin();
        obs.emit(sampleEvent());
        // transaction is now doomed before commit
        txReg.markRollbackOnly();
        obs.emit(sampleEvent());
        // finishing the transaction must release nothing for the second event,
        // and the doomed transaction itself must release nothing for the first
        txReg.rollback();

        assertEquals(0, captured.size(),
            "events emitted before and after STATUS_MARKED_ROLLBACK must both be discarded");
    }

    @Test
    void emitInSeparateTransactions_eachDispatchedOnOwnCommit() {
        List<String> captured = new ArrayList<>();
        var txReg = new TestTransactionRegistry();
        var obs = obs(captured, txReg);

        txReg.begin();
        obs.emit(sampleEvent());
        txReg.commit();

        txReg.begin();
        obs.emit(sampleEvent());
        txReg.commit();

        assertEquals(2, captured.size());
    }

    // -----------------------------------------------------------------------
    // Best-effort failure semantics
    // -----------------------------------------------------------------------

    @Test
    void serializationFailureIsAbsorbed() {
        List<String> captured = new ArrayList<>();
        var txReg = new TestTransactionRegistry();
        var obs = new Slf4jObservability(ERROR_LOG, new SynchronousSinkExecutor(),
            captured::add, txReg, failingJsonb());

        assertDoesNotThrow(() -> obs.emit(sampleEvent()));
        assertEquals(0, captured.size(), "failed serialization must produce no output");
    }

    @Test
    void serializationFailureAfterCommitIsAbsorbed() {
        List<String> captured = new ArrayList<>();
        var txReg = new TestTransactionRegistry();
        var obs = new Slf4jObservability(ERROR_LOG, new SynchronousSinkExecutor(),
            captured::add, txReg, failingJsonb());

        txReg.begin();
        assertDoesNotThrow(() -> obs.emit(sampleEvent()));
        assertDoesNotThrow(() -> txReg.commit());
        assertEquals(0, captured.size());
    }

    @Test
    void sinkFailureIsAbsorbed() {
        var txReg = new TestTransactionRegistry();
        var obs = new Slf4jObservability(ERROR_LOG, new SynchronousSinkExecutor(),
            line -> { throw new RuntimeException("sink down"); }, txReg, WORKING_JSONB);

        assertDoesNotThrow(() -> obs.emit(sampleEvent()));
    }

    @Test
    void dispatcherRejectionIsAbsorbed() {
        var txReg = new TestTransactionRegistry();
        var rejectingExecutor = new SynchronousSinkExecutor() {
            @Override public void execute(Runnable command) {
                throw new RejectedExecutionException("shutting down");
            }
        };
        var obs = new Slf4jObservability(ERROR_LOG, rejectingExecutor,
            line -> {}, txReg, WORKING_JSONB);

        assertDoesNotThrow(() -> obs.emit(sampleEvent()));
    }

    // -----------------------------------------------------------------------
    // Output contract
    // -----------------------------------------------------------------------

    @Test
    void emittedLineContainsExpectedEventFields() {
        AtomicReference<String> captured = new AtomicReference<>();
        var txReg = new TestTransactionRegistry();
        var obs = new Slf4jObservability(ERROR_LOG, new SynchronousSinkExecutor(),
            captured::set, txReg, WORKING_JSONB);

        UUID listId = UUID.randomUUID();
        obs.emit(new ListCreated(UUID.randomUUID(), Instant.now(),
            new EventActor.User(UUID.randomUUID()), listId, "My List"));

        String line = captured.get();
        assertNotNull(line);
        assertTrue(line.contains("\"type\":\"ListCreated\"") || line.contains("\"ListCreated\""),
            "must include type discriminator: " + line);
        assertTrue(line.contains(listId.toString()), "must include listId: " + line);
        assertTrue(line.contains("\"name\":\"My List\""), line);
        assertTrue(line.endsWith("\n"), "must end with newline");
    }

    @Test
    void emitWritesOneLinePerEvent() {
        List<String> captured = new ArrayList<>();
        var txReg = new TestTransactionRegistry();
        var obs = obs(captured, txReg);

        obs.emit(new ListCreated(UUID.randomUUID(), Instant.now(),
            new EventActor.User(UUID.randomUUID()), UUID.randomUUID(), "A"));
        obs.emit(new ListCreated(UUID.randomUUID(), Instant.now(),
            new EventActor.User(UUID.randomUUID()), UUID.randomUUID(), "B"));

        assertEquals(2, captured.size());
        assertTrue(captured.get(0).endsWith("\n"));
        assertTrue(captured.get(1).endsWith("\n"));
    }

    @Test
    void emitHandlesNullActor() {
        AtomicReference<String> captured = new AtomicReference<>();
        var txReg = new TestTransactionRegistry();
        var obs = new Slf4jObservability(ERROR_LOG, new SynchronousSinkExecutor(),
            captured::set, txReg, WORKING_JSONB);

        obs.emit(new ListCreated(UUID.randomUUID(), Instant.now(),
            null, UUID.randomUUID(), "X"));

        assertTrue(captured.get().contains("\"actor\":null"), captured.get());
    }

    @Test
    void allEightVariantsReachTheSink() {
        List<String> captured = new ArrayList<>();
        var txReg = new TestTransactionRegistry();
        var obs = obs(captured, txReg);

        Instant t = Instant.now();
        EventActor actor = new EventActor.User(UUID.randomUUID());
        UUID id = UUID.randomUUID();
        UUID captureId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        List<ObservabilityEvent> events = List.of(
            new ListCreated(id, t, actor, listId, "L"),
            new ItemCreated(id, t, actor, itemId, listId, null),
            new ItemCompleted(id, t, actor, itemId),
            new CaptureSubmitted(id, t, actor, captureId),
            new CaptureClassified(id, t, actor, captureId, listId),
            new CaptureNeedsResolution(id, t, actor, captureId),
            new CaptureResolved(id, t, actor, captureId, listId),
            new ClassificationRecorded(id, t, actor, listId)
        );

        for (ObservabilityEvent e : events) {
            obs.emit(e);
        }

        assertEquals(8, captured.size(), "all 8 variants must be dispatched");
        for (String line : captured) {
            assertTrue(line.endsWith("\n"), "each line must end with newline: " + line);
            assertFalse(line.substring(0, line.length() - 1).contains("\n"),
                "body must be single line: " + line);
        }
    }

    // -----------------------------------------------------------------------
    // Executor lifecycle
    // -----------------------------------------------------------------------

    @Test
    void executorIsShutdownOnDestroy() {
        var realExecutor = Executors.newVirtualThreadPerTaskExecutor();
        var txReg = new TestTransactionRegistry();
        var obs = new Slf4jObservability(ERROR_LOG, realExecutor,
            line -> {}, txReg, WORKING_JSONB);

        obs.onDestroy();

        assertTrue(realExecutor.isShutdown(), "executor must be shut down after @PreDestroy");
    }

    // -----------------------------------------------------------------------
    // Failing Jsonb stub
    // -----------------------------------------------------------------------

    private static Jsonb failingJsonb() {
        return new Jsonb() {
            @Override public String toJson(Object o) {
                throw new RuntimeException("deliberate serialization failure");
            }
            @Override public String toJson(Object o, Type t) { return toJson(o); }
            @Override public void toJson(Object o, Writer w) { toJson(o); }
            @Override public void toJson(Object o, Type t, Writer w) { toJson(o); }
            @Override public void toJson(Object o, OutputStream s) { toJson(o); }
            @Override public void toJson(Object o, Type t, OutputStream s) { toJson(o); }
            @Override public <T> T fromJson(String s, Class<T> c) { throw new UnsupportedOperationException(); }
            @Override public <T> T fromJson(String s, Type t) { throw new UnsupportedOperationException(); }
            @Override public <T> T fromJson(Reader r, Class<T> c) { throw new UnsupportedOperationException(); }
            @Override public <T> T fromJson(Reader r, Type t) { throw new UnsupportedOperationException(); }
            @Override public <T> T fromJson(InputStream s, Class<T> c) { throw new UnsupportedOperationException(); }
            @Override public <T> T fromJson(InputStream s, Type t) { throw new UnsupportedOperationException(); }
            @Override public void close() {}
        };
    }
}
