package com.capsa.observability.api.events;

import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.ObservabilityEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when the classification pipeline could not produce a confident
 * result and the capture is awaiting user resolution (UC-03 uncertain path).
 *
 * @param eventId    unique event identity
 * @param occurredAt classification moment
 * @param actor      user who submitted the capture
 * @param captureId  id of the capture awaiting resolution
 */
public record CaptureNeedsResolution(
        UUID eventId,
        Instant occurredAt,
        EventActor actor,
        UUID captureId
) implements ObservabilityEvent {}