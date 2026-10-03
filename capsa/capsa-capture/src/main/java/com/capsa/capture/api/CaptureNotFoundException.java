package com.capsa.capture.api;

/**
 * Thrown when a {@code Capture} lookup by id yields no result. Mapped to a
 * not-found response by the REST exception mapper.
 */
public class CaptureNotFoundException extends RuntimeException {
    public CaptureNotFoundException(CaptureId captureId) {
        super("Capture not found: " + captureId.value());
    }
}
