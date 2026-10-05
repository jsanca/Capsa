package com.capsa.observability.api.events;

import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.ObservabilityEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a user resolved an ambiguous capture by selecting a list
 * (UC-04).
 *
 * @param eventId        unique event identity
 * @param occurredAt     resolution moment
 * @param actor          user who resolved the capture
 * @param captureId      id of the resolved capture
 * @param selectedListId list selected by the user
 */
public record CaptureResolved(
        UUID eventId,
        Instant occurredAt,
        EventActor actor,
        UUID captureId,
        UUID selectedListId
) implements ObservabilityEvent {}