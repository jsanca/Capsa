package com.capsa.observability.api.events;

import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.ObservabilityEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a {@code ClassificationMemoryEntry} has been appended as
 * evidence of a classification decision (UC-04 user-confirmed, and UC-03
 * auto-classified — the source is implied by the originating path).
 *
 * <p>Capture content is deliberately excluded. Per architecture guidance,
 * user-provided content is not copied into observability streams; downstream
 * sinks correlate via the originating {@code captureId} (carried on
 * {@link ItemCreated} for resolved captures) when retrieval is justified.
 *
 * @param eventId    unique event identity
 * @param occurredAt recording moment
 * @param actor      user whose memory was updated
 * @param listId     list selected at classification time
 */
public record ClassificationRecorded(
        UUID eventId,
        Instant occurredAt,
        EventActor actor,
        UUID listId
) implements ObservabilityEvent {}