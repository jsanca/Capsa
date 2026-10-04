package com.capsa.classification.internal.service;

import com.capsa.classification.api.Classifier;
import com.capsa.classification.api.ClassificationRequest;
import com.capsa.classification.api.ClassificationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@ApplicationScoped
public class ClassificationPipeline implements Classifier {

    private static final Logger LOG = LoggerFactory.getLogger(ClassificationPipeline.class);

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
            final String strategyName = strategy.getClass().getSimpleName();
            LOG.debug("Running classification strategy={} userId={}", strategyName, request.userId().value());
            last = strategy.classify(request);
            LOG.debug("Strategy result strategy={} outcome={} userId={}",
                strategyName, last.outcome(), request.userId().value());
            if (last.outcome() == ClassificationResult.Outcome.CLASSIFIED) {
                LOG.debug("Pipeline classified by strategy={} userId={}", strategyName, request.userId().value());
                return last;
            }
        }
        final ClassificationResult result = last != null ? last : new ClassificationResult(
            ClassificationResult.Outcome.NEEDS_RESOLUTION,
            List.of(),
            null,
            null
        );
        LOG.debug("Pipeline exhausted; final outcome={} userId={}", result.outcome(), request.userId().value());
        return result;
    }
}
