package com.capsa.classification.internal.service;

import com.capsa.classification.api.ClassificationCandidate;
import com.capsa.classification.api.ClassificationRequest;
import com.capsa.classification.api.ClassificationResult;
import com.capsa.classification.internal.persistence.repository.ClassificationMemoryEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@ApplicationScoped
class KnownClassificationStrategy implements ClassificationStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(KnownClassificationStrategy.class);

    private final ClassificationMemoryEntryRepository repository;

    @Inject
    KnownClassificationStrategy(ClassificationMemoryEntryRepository repository) {
        this.repository = repository;
    }

    @Override
    public ClassificationResult classify(ClassificationRequest request) {
        return repository.findLatestUserConfirmed(request.userId(), request.normalizedContent())
            .map(entry -> {
                LOG.debug("Known strategy hit userId={} selectedListId={}",
                    request.userId().value(), entry.getSelectedListId());
                return new ClassificationResult(
                    ClassificationResult.Outcome.CLASSIFIED,
                    List.of(new ClassificationCandidate(entry.getSelectedListId(), 1.0, "Previously confirmed by user")),
                    entry.getSelectedListId(),
                    null
                );
            })
            .orElseGet(() -> {
                LOG.debug("Known strategy miss userId={}", request.userId().value());
                return new ClassificationResult(
                    ClassificationResult.Outcome.NEEDS_RESOLUTION,
                    List.of(),
                    null,
                    null
                );
            });
    }
}
