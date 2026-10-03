package com.capsa.classification.internal.service;

import com.capsa.classification.api.Classifier;
import com.capsa.classification.api.ClassificationRequest;
import com.capsa.classification.api.ClassificationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ClassificationPipeline implements Classifier {

    private final List<ClassificationStrategy> strategies;

    @Inject
    ClassificationPipeline(KnownClassificationStrategy strategy) {
        this.strategies = List.of(strategy);
    }

    ClassificationPipeline(List<ClassificationStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    @Override
    public ClassificationResult classify(ClassificationRequest request) {
        ClassificationResult last = null;
        for (ClassificationStrategy strategy : strategies) {
            last = strategy.classify(request);
            if (last.outcome() == ClassificationResult.Outcome.CLASSIFIED) {
                return last;
            }
        }
        return last != null ? last : new ClassificationResult(
            ClassificationResult.Outcome.NEEDS_RESOLUTION,
            List.of(),
            null,
            null
        );
    }
}
