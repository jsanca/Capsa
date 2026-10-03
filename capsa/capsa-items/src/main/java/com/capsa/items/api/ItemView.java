package com.capsa.items.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Read projection of an {@code Item} intended for transport across the
 * capability boundary.
 *
 * @param itemId      item identifier
 * @param listId      list the item belongs to; never changes after creation
 * @param captureId   originating capture id; {@code null} for direct adds (UC-02)
 * @param name        display name
 * @param notes       optional notes; may be {@code null}
 * @param status      lifecycle state serialized as a string
 *                    ({@code "PENDING"} or {@code "DONE"})
 * @param createdAt   creation timestamp
 * @param completedAt completion timestamp; {@code null} until {@code DONE}
 */
public record ItemView(
    ItemId itemId,
    UUID listId,
    UUID captureId,
    String name,
    String notes,
    String status,
    Instant createdAt,
    Instant completedAt
) {}
