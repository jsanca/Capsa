package com.capsa.capture.api;

/**
 * Thrown when {@link CaptureService#resolve} is invoked for a capture that is
 * not in the awaiting-resolution state — for example, already resolved, still
 * processing, or failed.
 */
public class CaptureNotAwaitingResolutionException extends RuntimeException {
    public CaptureNotAwaitingResolutionException(CaptureId captureId) {
        super("Capture is not awaiting resolution: " + captureId.value());
    }
}
