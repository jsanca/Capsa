package com.capsa.observability.api.events;

import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.ObservabilityEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when the classification pipeline produced a confident result for
 * a {@code Capture} (UC-03 happy path).
 *
 * @param eventId        unique event identity
 * @param occurredAt     classification moment
 * @param actor          user who submitted the capture
 * @param captureId      id of the capture that was classified
 * @param selectedListId list selected by the pipeline
 */
public record CaptureClassified(
        UUID eventId,
        Instant occurredAt,
        EventActor actor,
        UUID captureId,
        UUID selectedListId
) implements ObservabilityEvent {}