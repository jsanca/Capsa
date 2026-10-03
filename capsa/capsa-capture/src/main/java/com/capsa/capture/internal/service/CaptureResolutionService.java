package com.capsa.capture.internal.service;

import com.capsa.capture.api.CaptureId;
import com.capsa.capture.api.CaptureNotFoundException;
import com.capsa.capture.api.CaptureNotAwaitingResolutionException;
import com.capsa.capture.internal.domain.Capture;
import com.capsa.capture.internal.persistence.entity.ClassificationAttemptEntity;
import com.capsa.capture.internal.persistence.entity.CaptureEntity;
import com.capsa.capture.internal.persistence.entity.ClassificationResolutionEntity;
import com.capsa.capture.internal.persistence.repository.CaptureRepository;
import com.capsa.capture.internal.persistence.repository.ClassificationAttemptRepository;
import com.capsa.capture.internal.persistence.repository.ClassificationResolutionRepository;
import com.capsa.classification.api.ClassificationCandidate;
import com.capsa.classification.api.ClassificationResult;
import com.capsa.classification.api.ClassificationService;
import com.capsa.items.api.ItemService;
import com.capsa.items.api.ItemView;
import com.capsa.lists.api.ListId;
import com.capsa.users.api.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
class CaptureResolutionService {

    private final CaptureRepository captureRepository;
    private final ClassificationAttemptRepository attemptRepository;
    private final ClassificationResolutionRepository resolutionRepository;
    private final ItemService itemService;
    private final ClassificationService classificationService;

    @Inject
    CaptureResolutionService(
            CaptureRepository captureRepository,
            ClassificationAttemptRepository attemptRepository,
            ClassificationResolutionRepository resolutionRepository,
            ItemService itemService,
            ClassificationService classificationService) {
        this.captureRepository = captureRepository;
        this.attemptRepository = attemptRepository;
        this.resolutionRepository = resolutionRepository;
        this.itemService = itemService;
        this.classificationService = classificationService;
    }

    @Transactional
    ItemView resolveCapture(UserId userId, Capture capture, ClassificationResult result) {
        final CaptureEntity entity = captureRepository.findById(capture.captureId())
            .orElseThrow(() -> new CaptureNotFoundException(new CaptureId(capture.captureId())));

        final ItemView item = itemService.createFromCapture(
            userId,
            new ListId(result.selectedListId()),
            capture.captureId(),
            capture.normalizedContent(),
            null
        );

        updateAttempt(capture.captureId(), "CLASSIFIED", result.candidates());

        entity.processingStatus = "CLASSIFIED";

        persistResolution(capture.captureId(), result.selectedListId(), "AUTO");

        return item;
    }

    @Transactional
    void storeNeedsResolution(Capture capture, ClassificationResult result) {
        final CaptureEntity entity = captureRepository.findById(capture.captureId())
            .orElseThrow(() -> new CaptureNotFoundException(new CaptureId(capture.captureId())));

        updateAttempt(capture.captureId(), "NEEDS_RESOLUTION", result.candidates());

        entity.processingStatus = "NEEDS_RESOLUTION";
    }

    @Transactional
    void markFailed(UUID captureId) {
        captureRepository.findById(captureId).ifPresent(e -> e.processingStatus = "FAILED");
    }

    @Transactional
    ItemView resolveFromUser(UserId userId, CaptureId captureId, ListId listId) {
        final CaptureEntity entity = captureRepository.findById(captureId.value())
            .orElseThrow(() -> new CaptureNotFoundException(captureId));

        if (!userId.value().equals(entity.userId)) {
            throw new CaptureNotFoundException(captureId);
        }

        if (!"NEEDS_RESOLUTION".equals(entity.processingStatus)) {
            throw new CaptureNotAwaitingResolutionException(captureId);
        }

        final ItemView item = itemService.createFromCapture(
            userId,
            listId,
            captureId.value(),
            entity.normalizedContent,
            null
        );

        persistResolution(captureId.value(), listId.value(), "USER");

        classificationService.recordResolution(userId, entity.normalizedContent, listId.value());

        entity.processingStatus = "RESOLVED";

        return item;
    }

    private void updateAttempt(UUID captureId, String outcome, List<ClassificationCandidate> candidates) {
        attemptRepository.findByCaptureId(captureId).ifPresent(attempt -> {
            attempt.outcome = outcome;
            attempt.candidates = serializeCandidates(candidates);
        });
    }

    private void persistResolution(UUID captureId, UUID selectedListId, String resolvedBy) {
        final ClassificationResolutionEntity resolution = new ClassificationResolutionEntity();
        resolution.captureId = captureId;
        resolution.selectedListId = selectedListId;
        resolution.resolvedBy = resolvedBy;
        resolution.resolvedAt = Instant.now();
        resolutionRepository.persist(resolution);
    }

    private String serializeCandidates(List<ClassificationCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return "[]";
        return "[" + candidates.stream()
            .map(c -> "{\"listId\":\"" + c.listId() + "\",\"confidence\":" + c.confidence() + "}")
            .collect(Collectors.joining(",")) + "]";
    }
}
