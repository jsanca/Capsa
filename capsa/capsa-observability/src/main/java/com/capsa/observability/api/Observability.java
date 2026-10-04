package com.capsa.observability.api;

/**
 * Capability-facing port for emitting structured observability events.
 *
 * <p>Capability services depend only on this interface. They do not know
 * whether events are written to a file, a log, a database, a Kafka topic, or
 * any future sink; that decision is made by the implementation bound at
 * composition time.
 *
 * <h2>Delivery semantics — best effort</h2>
 *
 * <p>Implementation contract:
 * <ul>
 *   <li>Return promptly; do not block the calling business thread.</li>
 *   <li>Never throw to the caller. A sink failure must not cause an
 *       otherwise successful business operation to fail.</li>
 *   <li>Log sink failures via the project's standard application logger
 *       (not via this port — observability failures must not recurse
 *       through the observability path).</li>
 *   <li>Do not require transactional coupling between the business write
 *       and the observability delivery. v0.1 is best-effort; a durable
 *       outbox may be introduced later if evidence requires it.</li>
 * </ul>
 *
 * <h2>Sensitive data</h2>
 *
 * <p>Capability services must not place OIDC tokens, credentials, raw
 * authorization headers, or user-provided capture content in events. The
 * canonical event contract ({@link ObservabilityEvent} and its variants)
 * already excludes these fields.
 */
public interface Observability {

    /**
     * Submits an event for asynchronous delivery. Returns immediately.
     *
     * <p>Never throws. Failures are absorbed by the implementation and
     * reported via the standard application logger.
     *
     * @param event the canonical event to emit; must not be {@code null}
     */
    void emit(ObservabilityEvent event);
}