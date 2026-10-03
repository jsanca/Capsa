package com.capsa.classification.internal.service;

import com.capsa.classification.api.ClassificationCandidate;
import com.capsa.classification.api.ClassificationRequest;
import com.capsa.classification.api.ClassificationResult;
import com.capsa.classification.internal.persistence.repository.ClassificationMemoryEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
class KnownClassificationStrategy implements ClassificationStrategy {

    private final ClassificationMemoryEntryRepository repository;

    @Inject
    KnownClassificationStrategy(ClassificationMemoryEntryRepository repository) {
        this.repository = repository;
    }

    @Override
    public ClassificationResult classify(ClassificationRequest request) {
        return repository.findLatestUserConfirmed(request.userId(), request.normalizedContent())
            .map(entry -> new ClassificationResult(
                ClassificationResult.Outcome.CLASSIFIED,
                List.of(new ClassificationCandidate(entry.getSelectedListId(), 1.0, "Previously confirmed by user")),
                entry.getSelectedListId(),
                null
            ))
            .orElseGet(() -> new ClassificationResult(
                ClassificationResult.Outcome.NEEDS_RESOLUTION,
                List.of(),
                null,
                null
            ));
    }
}
