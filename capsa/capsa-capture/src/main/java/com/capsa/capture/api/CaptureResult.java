package com.capsa.capture.api;

import com.capsa.items.api.ItemView;
import java.util.List;
import com.capsa.classification.api.ClassificationCandidate;

/**
 * Outcome of {@link CaptureService#submit}. Sealed to the two classification
 * outcomes the smart-capture path can return — confident classification or
 * the user being asked to choose. Execution failures are not represented
 * here; they propagate as exceptions.
 */
public sealed interface CaptureResult permits CaptureResult.Classified, CaptureResult.NeedsResolution {

    /**
     * Classification succeeded with sufficient confidence; the produced
     * {@link ItemView} is already persisted in its destination list.
     */
    record Classified(ItemView item) implements CaptureResult {}

    /**
     * Classification was not confident enough; the user must select a
     * destination list from (or beyond) {@code candidates}.
     */
    record NeedsResolution(CaptureId captureId, List<ClassificationCandidate> candidates) implements CaptureResult {}
}
