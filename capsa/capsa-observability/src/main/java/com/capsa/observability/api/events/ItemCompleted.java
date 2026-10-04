package com.capsa.observability.api.events;

import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.ObservabilityEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when an {@code Item} transitions from {@code PENDING} to
 * {@code DONE} (UC-06). Idempotent completion of an already-{@code DONE}
 * item does not re-emit this event.
 *
 * @param eventId    unique event identity
 * @param occurredAt completion moment
 * @param actor      user who completed the item
 * @param itemId     id of the completed item
 */
public record ItemCompleted(
        UUID eventId,
        Instant occurredAt,
        EventActor actor,
        UUID itemId
) implements ObservabilityEvent {}