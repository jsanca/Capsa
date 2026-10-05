package com.capsa.lists.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed identifier for a {@code CapsaList}. Wraps a {@link UUID} so
 * that ids cannot be silently confused across aggregates at compile time.
 *
 * @param value the underlying UUID; non-null
 */
public record ListId(UUID value) {
    public ListId {
        Objects.requireNonNull(value, "value");
    }
}
