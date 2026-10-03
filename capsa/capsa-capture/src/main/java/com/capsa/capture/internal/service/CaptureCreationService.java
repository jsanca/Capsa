package com.capsa.capture.internal.service;

import com.capsa.capture.internal.domain.Capture;
import com.capsa.capture.internal.persistence.entity.ClassificationAttemptEntity;
import com.capsa.capture.internal.persistence.entity.CaptureEntity;
import com.capsa.capture.internal.persistence.repository.CaptureRepository;
import com.capsa.capture.internal.persistence.repository.ClassificationAttemptRepository;
import com.capsa.users.api.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;

@ApplicationScoped
class CaptureCreationService {

    private final CaptureRepository captureRepository;
    private final ClassificationAttemptRepository attemptRepository;

    @Inject
    CaptureCreationService(CaptureRepository captureRepository, ClassificationAttemptRepository attemptRepository) {
        this.captureRepository = captureRepository;
        this.attemptRepository = attemptRepository;
    }

    @Transactional
    Capture createCapture(UserId userId, String originalContent, String normalizedContent) {
        final CaptureEntity capture = new CaptureEntity();
        capture.userId = userId.value();
        capture.originalContent = originalContent;
        capture.normalizedContent = normalizedContent;
        capture.processingStatus = "PROCESSING";
        capture.capturedAt = Instant.now();
        captureRepository.persist(capture);

        final ClassificationAttemptEntity attempt = new ClassificationAttemptEntity();
        attempt.captureId = capture.id;
        attempt.attemptedAt = Instant.now();
        attemptRepository.persist(attempt);

        return new Capture(capture.id, originalContent, normalizedContent);
    }
}
