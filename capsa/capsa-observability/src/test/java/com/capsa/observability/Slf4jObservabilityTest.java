package com.capsa.observability;

import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.events.ListCreated;
import com.capsa.observability.internal.Slf4jObservability;
import com.capsa.observability.internal.SynchronousSinkExecutor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Slf4jObservability}'s best-effort delivery contract.
 */
class Slf4jObservabilityTest {

    private static final Logger ERROR_LOG = LoggerFactory.getLogger(Slf4jObservabilityTest.class);

    @Test
    void emitDoesNotThrowWhenSinkFails() {
        var sink = new java.util.function.Consumer<String>() {
            @Override public void accept(String s) { throw new RuntimeException("sink down"); }
        };
        var obs = new Slf4jObservability(ERROR_LOG, new SynchronousSinkExecutor(), sink);

        assertDoesNotThrow(() -> obs.emit(new ListCreated(
            UUID.randomUUID(), Instant.now(), null, UUID.randomUUID(), "X"
        )));
    }

    @Test
    void emitDoesNotThrowWhenDispatcherRejects() {
        var rejectedExecutor = new SynchronousSinkExecutor() {
            @Override
            public void execute(Runnable command) {
                throw new RejectedExecutionException("shutting down");
            }
        };
        var obs = new Slf4jObservability(ERROR_LOG, rejectedExecutor, line -> { /* never reached */ });

        assertDoesNotThrow(() -> obs.emit(new ListCreated(
            UUID.randomUUID(), Instant.now(), null, UUID.randomUUID(), "X"
        )));
    }

    @Test
    void emitWritesOneLinePerEventToSink() {
        List<String> captured = new ArrayList<>();
        var obs = new Slf4jObservability(ERROR_LOG, new SynchronousSinkExecutor(), captured::add);

        obs.emit(new ListCreated(UUID.randomUUID(), Instant.now(),
            new EventActor.User(UUID.randomUUID()), UUID.randomUUID(), "A"));
        obs.emit(new ListCreated(UUID.randomUUID(), Instant.now(),
            new EventActor.User(UUID.randomUUID()), UUID.randomUUID(), "B"));

        assertEquals(2, captured.size());
        assertTrue(captured.get(0).endsWith("\n"));
        assertTrue(captured.get(1).endsWith("\n"));
        assertTrue(captured.get(0).contains("\"name\":\"A\""));
        assertTrue(captured.get(1).contains("\"name\":\"B\""));
    }

    @Test
    void emitHandlesNullActor() {
        AtomicReference<String> captured = new AtomicReference<>();
        var obs = new Slf4jObservability(ERROR_LOG, new SynchronousSinkExecutor(), captured::set);

        obs.emit(new ListCreated(UUID.randomUUID(), Instant.now(), null, UUID.randomUUID(), "X"));

        String json = captured.get();
        assertTrue(json.contains("\"actor\":null"), json);
    }
}