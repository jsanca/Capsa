package com.capsa.capture.internal.domain;

import java.util.UUID;

/**
 * Immutable evidence of content received by Capsa.
 *
 * <p>A {@code Capture} preserves both the original content exactly as it was
 * submitted and a normalized representation computed at capture time. The
 * normalized form is stored (not recomputed on demand) so that historical
 * classification behavior remains anchored to the normalization rules in
 * effect at the time — preserving {@code ClassificationMemory} matching
 * across rule changes.
 *
 * <p>The {@code Capture} is the source aggregate for the smart-capture path
 * (UC-03 / UC-04). A successful resolution produces a downstream {@code Item};
 * an unresolved Capture is persisted awaiting the user's choice.
 *
 * @param captureId         system-assigned capture identifier
 * @param originalContent   content as submitted; immutable
 * @param normalizedContent content after {@code CaptureNormalizer}; stable
 *                          representation used by classification
 */
public record Capture(UUID captureId, String originalContent, String normalizedContent) {}
