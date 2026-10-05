package com.capsa.items.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed identifier for an {@code Item}. Wraps a {@link UUID} so that
 * ids cannot be silently confused across aggregates at compile time.
 *
 * @param value the underlying UUID; non-null
 */
public record ItemId(UUID value) {
    public ItemId {
        Objects.requireNonNull(value, "value");
    }
}
