package com.capsa.classification.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Output of a single classification pipeline execution.
 *
 * <p>Represents semantic outcomes. Execution failures do <em>not</em> appear
 * here; they propagate as exceptions out of the strategy and are handled by
 * the application layer.
 *
 * @param outcome        {@link Outcome#CLASSIFIED} when a list was selected
 *                       confidently, or {@link Outcome#NEEDS_RESOLUTION}
 *                       when the user must choose
 * @param candidates     ranked candidate lists; non-null (may be empty for
 *                       {@code CLASSIFIED} when only the selected list matters)
 * @param selectedListId the destination list when {@code outcome == CLASSIFIED};
 *                       {@code null} when {@code outcome == NEEDS_RESOLUTION}
 * @param metadata       strategy-specific metadata (provider, model, timing,
 *                       similarity metric, etc.); may be empty
 */
public record ClassificationResult(
    Outcome outcome,
    List<ClassificationCandidate> candidates,
    UUID selectedListId,
    Map<String, String> metadata
) {

    /** Semantic classification outcome. */
    public enum Outcome {
        /** A list was selected confidently; {@code selectedListId} is set. */
        CLASSIFIED,
        /** The pipeline could not classify confidently; the user must choose. */
        NEEDS_RESOLUTION
    }
}
