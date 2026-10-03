package com.capsa.classification.api;

/**
 * The classification capability exposed to the {@code capture} module.
 *
 * <p>This is the only classification abstraction the rest of the system
 * depends on. Concrete strategies (Known, Embedding, SystemOne, etc.) and
 * the pipeline that orders them are internal to {@code capsa-classification}
 * and are not referenced from outside.
 *
 * <p>Error contract: classification outcomes are represented in
 * {@link ClassificationResult} (semantic success or
 * {@code NEEDS_RESOLUTION}). Execution failures — timeouts, provider
 * unavailability, infrastructure errors — propagate as exceptions and are
 * not encoded in {@link ClassificationResult}.
 */
public interface Classifier {

    /**
     * Classifies the request's content against the supplied candidate lists.
     *
     * @param request the user, normalized content, and candidate lists; non-null
     * @return a {@link ClassificationResult} with either a chosen list and
     *         {@link ClassificationResult.Outcome#CLASSIFIED CLASSIFIED}, or
     *         {@link ClassificationResult.Outcome#NEEDS_RESOLUTION NEEDS_RESOLUTION}
     *         with candidate rankings for the user to choose from
     */
    ClassificationResult classify(ClassificationRequest request);
}
