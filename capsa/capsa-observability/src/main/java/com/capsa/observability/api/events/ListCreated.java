package com.capsa.observability.api.events;

import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.ObservabilityEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a {@code CapsaList} has been created (UC-01).
 *
 * @param eventId    unique event identity
 * @param occurredAt creation moment of the event
 * @param actor      user who created the list
 * @param listId     id of the newly-created list
 * @param name       list name as supplied by the user
 */
public record ListCreated(
        UUID eventId,
        Instant occurredAt,
        EventActor actor,
        UUID listId,
        String name
) implements ObservabilityEvent {}