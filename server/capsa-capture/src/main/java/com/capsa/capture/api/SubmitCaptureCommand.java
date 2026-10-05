package com.capsa.capture.api;

/**
 * Input DTO for {@link CaptureService#submit}.
 *
 * @param content raw submitted content; non-blank (the service validates)
 */
public record SubmitCaptureCommand(String content) {}
