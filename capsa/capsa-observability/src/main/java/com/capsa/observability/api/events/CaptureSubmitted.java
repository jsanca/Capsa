package com.capsa.observability.api.events;

import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.ObservabilityEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a raw {@code Capture} has been persisted for classification
 * (UC-03). The raw content is intentionally not included — the sink can
 * correlate against the capture id if needed.
 *
 * @param eventId    unique event identity
 * @param occurredAt submission moment
 * @param actor      user who submitted the capture
 * @param captureId  id of the newly-persisted capture
 */
public record CaptureSubmitted(
        UUID eventId,
        Instant occurredAt,
        EventActor actor,
        UUID captureId
) implements ObservabilityEvent {}