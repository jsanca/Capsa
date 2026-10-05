package com.capsa.capture.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed identifier for a {@code Capture}. Wraps a {@link UUID} so
 * that ids cannot be silently confused across aggregates at compile time.
 *
 * @param value the underlying UUID; non-null
 */
public record CaptureId(UUID value) {
    public CaptureId {
        Objects.requireNonNull(value, "value");
    }
}
