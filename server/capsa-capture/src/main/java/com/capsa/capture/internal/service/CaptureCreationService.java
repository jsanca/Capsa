package com.capsa.capture.internal.service;

import com.capsa.capture.internal.domain.Capture;
import com.capsa.capture.internal.persistence.entity.ClassificationAttemptEntity;
import com.capsa.capture.internal.persistence.entity.CaptureEntity;
import com.capsa.capture.internal.persistence.repository.CaptureRepository;
import com.capsa.capture.internal.persistence.repository.ClassificationAttemptRepository;
import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.Observability;
import com.capsa.observability.api.events.CaptureSubmitted;
import com.capsa.users.api.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
class CaptureCreationService {

    private static final Logger LOG = LoggerFactory.getLogger(CaptureCreationService.class);

    private final CaptureRepository captureRepository;
    private final ClassificationAttemptRepository attemptRepository;
    private final Observability observability;

    @Inject
    CaptureCreationService(
            CaptureRepository captureRepository,
            ClassificationAttemptRepository attemptRepository,
            Observability observability) {
        this.captureRepository = captureRepository;
        this.attemptRepository = attemptRepository;
        this.observability = observability;
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

        observability.emit(new CaptureSubmitted(
            UUID.randomUUID(),
            Instant.now(),
            new EventActor.User(userId.value()),
            capture.id
        ));

        LOG.debug("Persisted capture captureId={} userId={} contentLength={}",
            capture.id, userId.value(), originalContent.length());
        return new Capture(capture.id, originalContent, normalizedContent);
    }
}
