package com.capsa.classification.api;

import java.util.UUID;

/**
 * A ranked candidate list produced by a classification strategy.
 *
 * @param listId      candidate list id
 * @param confidence  confidence in the range {@code [0.0, 1.0]}; higher means
 *                    the strategy considers this list a better fit
 * @param explanation optional human-readable rationale for the candidate;
 *                    may be {@code null}
 */
public record ClassificationCandidate(UUID listId, double confidence, String explanation) {}
