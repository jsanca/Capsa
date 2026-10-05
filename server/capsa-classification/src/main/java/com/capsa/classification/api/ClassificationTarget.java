package com.capsa.classification.api;

import java.util.UUID;

/**
 * Minimal projection of a list supplied to a classification strategy as a
 * candidate destination.
 *
 * @param listId          candidate list id
 * @param name            list name for display / strategy context
 * @param explicitPurpose user-supplied semantic description; may be
 *                        {@code null} if the user did not provide one
 */
public record ClassificationTarget(UUID listId, String name, String explicitPurpose) {}
