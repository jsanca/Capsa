package com.capsa.observability.api.events;

import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.ObservabilityEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a new {@code Item} has been created.
 *
 * <p>Covers both creation paths:
 * <ul>
 *   <li>UC-02 — direct user add. {@code captureId} is {@code null}.</li>
 *   <li>UC-04 — capture resolved by the user. {@code captureId} references
 *       the originating capture for traceability.</li>
 * </ul>
 *
 * @param eventId    unique event identity
 * @param occurredAt creation moment of the event
 * @param actor      user who caused the item to be created
 * @param itemId     id of the newly-created item
 * @param listId     destination list
 * @param captureId  originating capture id, or {@code null} for direct adds
 */
public record ItemCreated(
        UUID eventId,
        Instant occurredAt,
        EventActor actor,
        UUID itemId,
        UUID listId,
        UUID captureId
) implements ObservabilityEvent {}