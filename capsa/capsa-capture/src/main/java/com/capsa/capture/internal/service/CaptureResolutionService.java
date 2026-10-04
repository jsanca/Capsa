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
import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.Observability;
import com.capsa.observability.api.events.CaptureClassified;
import com.capsa.observability.api.events.CaptureNeedsResolution;
import com.capsa.observability.api.events.CaptureResolved;
import com.capsa.users.api.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
class CaptureResolutionService {

    private static final Logger LOG = LoggerFactory.getLogger(CaptureResolutionService.class);

    private final CaptureRepository captureRepository;
    private final ClassificationAttemptRepository attemptRepository;
    private final ClassificationResolutionRepository resolutionRepository;
    private final ItemService itemService;
    private final ClassificationService classificationService;
    private final Observability observability;

    @Inject
    CaptureResolutionService(
            CaptureRepository captureRepository,
            ClassificationAttemptRepository attemptRepository,
            ClassificationResolutionRepository resolutionRepository,
            ItemService itemService,
            ClassificationService classificationService,
            Observability observability) {
        this.captureRepository = captureRepository;
        this.attemptRepository = attemptRepository;
        this.resolutionRepository = resolutionRepository;
        this.itemService = itemService;
        this.classificationService = classificationService;
        this.observability = observability;
    }

    @Transactional
    ItemView resolveCapture(UserId userId, Capture capture, ClassificationResult result) {
        final CaptureEntity entity = captureRepository.findById(capture.captureId())
            .orElseThrow(() -> {
                LOG.warn("Capture disappeared between submit and resolve captureId={}", capture.captureId());
                return new CaptureNotFoundException(new CaptureId(capture.captureId()));
            });

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

        observability.emit(new CaptureClassified(
            UUID.randomUUID(),
            Instant.now(),
            new EventActor.User(userId.value()),
            capture.captureId(),
            result.selectedListId()
        ));

        LOG.info("Capture auto-classified captureId={} selectedListId={} itemId={} userId={}",
            capture.captureId(), result.selectedListId(), item.itemId().value(), userId.value());
        return item;
    }

    @Transactional
    void storeNeedsResolution(Capture capture, ClassificationResult result) {
        final CaptureEntity entity = captureRepository.findById(capture.captureId())
            .orElseThrow(() -> {
                LOG.warn("Capture disappeared between submit and needs-resolution captureId={}", capture.captureId());
                return new CaptureNotFoundException(new CaptureId(capture.captureId()));
            });

        updateAttempt(capture.captureId(), "NEEDS_RESOLUTION", result.candidates());

        entity.processingStatus = "NEEDS_RESOLUTION";

        observability.emit(new CaptureNeedsResolution(
            UUID.randomUUID(),
            Instant.now(),
            new EventActor.User(userIdOf(capture)),
            capture.captureId()
        ));

        LOG.info("Capture awaiting user resolution captureId={} userId={} candidateCount={}",
            capture.captureId(), userIdOf(capture), result.candidates().size());
    }

    @Transactional
    void markFailed(UUID captureId) {
        captureRepository.findById(captureId).ifPresent(e -> {
            e.processingStatus = "FAILED";
            LOG.warn("Capture marked FAILED captureId={}", captureId);
        });
    }

    @Transactional
    ItemView resolveFromUser(UserId userId, CaptureId captureId, ListId listId) {
        final CaptureEntity entity = captureRepository.findById(captureId.value())
            .orElseThrow(() -> new CaptureNotFoundException(captureId));

        if (!userId.value().equals(entity.userId)) {
            LOG.warn("Capture resolve denied ownership captureId={} requestingUserId={} ownerId={}",
                captureId.value(), userId.value(), entity.userId);
            throw new CaptureNotFoundException(captureId);
        }

        if (!"NEEDS_RESOLUTION".equals(entity.processingStatus)) {
            LOG.warn("Capture resolve rejected state captureId={} state={}",
                captureId.value(), entity.processingStatus);
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

        observability.emit(new CaptureResolved(
            UUID.randomUUID(),
            Instant.now(),
            new EventActor.User(userId.value()),
            captureId.value(),
            listId.value()
        ));

        LOG.info("Capture resolved by user captureId={} selectedListId={} itemId={} userId={}",
            captureId.value(), listId.value(), item.itemId().value(), userId.value());
        return item;
    }

    private UUID userIdOf(Capture capture) {
        return captureRepository.findById(capture.captureId())
            .map(e -> e.userId)
            .orElse(null);
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
