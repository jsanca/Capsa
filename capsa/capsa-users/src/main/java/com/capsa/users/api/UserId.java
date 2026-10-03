package com.capsa.users.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed identifier for a {@code User}. Wraps a {@link UUID} so that
 * ids cannot be silently confused across aggregates at compile time.
 *
 * @param value the underlying UUID; non-null
 */
public record UserId(UUID value) {
    public UserId {
        Objects.requireNonNull(value, "value");
    }
}
