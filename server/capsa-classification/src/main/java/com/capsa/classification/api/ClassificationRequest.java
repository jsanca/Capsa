package com.capsa.classification.api;

import com.capsa.users.api.UserId;
import java.util.List;

/**
 * Input to a single {@link Classifier#classify} call.
 *
 * @param userId            owning user; evidence is scoped per user
 * @param normalizedContent content to classify, already normalized by
 *                          {@code CaptureNormalizer}; non-blank
 * @param candidates        candidate destination lists with semantic context;
 *                          non-null (may be empty when no lists exist)
 */
public record ClassificationRequest(
    UserId userId,
    String normalizedContent,
    List<ClassificationTarget> candidates
) {}
