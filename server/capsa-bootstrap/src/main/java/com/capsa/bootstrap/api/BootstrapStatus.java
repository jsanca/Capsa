package com.capsa.bootstrap.api;

import java.time.Instant;

/**
 * Bootstrap completion state.
 * {@code required} is {@code true} when no first-admin bootstrap has been claimed.
 * {@code claimedAt} is non-null only when bootstrap has been claimed.
 */
public record BootstrapStatus(boolean required, Instant claimedAt) {

    public static BootstrapStatus notYetClaimed() {
        return new BootstrapStatus(true, null);
    }

    public static BootstrapStatus claimed(Instant at) {
        return new BootstrapStatus(false, at);
    }
}
