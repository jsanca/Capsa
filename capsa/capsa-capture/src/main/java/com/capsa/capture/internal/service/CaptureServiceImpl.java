package com.capsa.capture.internal.service;

import com.capsa.capture.api.CaptureId;
import com.capsa.capture.api.CaptureResult;
import com.capsa.capture.api.CaptureService;
import com.capsa.capture.internal.domain.Capture;
import com.capsa.capture.internal.domain.CaptureNormalizer;
import com.capsa.classification.api.ClassificationRequest;
import com.capsa.classification.api.ClassificationResult;
import com.capsa.classification.api.Classifier;
import com.capsa.items.api.ItemView;
import com.capsa.lists.api.ListId;
import com.capsa.users.api.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class CaptureServiceImpl implements CaptureService {

    private final CaptureCreationService captureCreationService;
    private final CaptureResolutionService captureResolutionService;
    private final Classifier classifier;

    @Inject
    public CaptureServiceImpl(
            final CaptureCreationService captureCreationService,
            final CaptureResolutionService captureResolutionService,
            final Classifier classifier) {
        this.captureCreationService = captureCreationService;
        this.captureResolutionService = captureResolutionService;
        this.classifier = classifier;
    }

    @Override
    public CaptureResult submit(UserId userId, String content) {
        final String normalized = CaptureNormalizer.normalize(content);

        final Capture capture = captureCreationService.createCapture(userId, content, normalized);

        final ClassificationResult result;
        try {
            result = classifier.classify(new ClassificationRequest(userId, normalized, List.of()));
        } catch (Exception e) {
            captureResolutionService.markFailed(capture.captureId());
            throw e;
        }

        return switch (result.outcome()) {
            case CLASSIFIED -> {
                final ItemView item = captureResolutionService.resolveCapture(userId, capture, result);
                yield new CaptureResult.Classified(item);
            }
            case NEEDS_RESOLUTION -> {
                captureResolutionService.storeNeedsResolution(capture, result);
                yield new CaptureResult.NeedsResolution(
                    new CaptureId(capture.captureId()),
                    result.candidates()
                );
            }
        };
    }

    @Override
    public ItemView resolve(UserId userId, CaptureId captureId, ListId listId) {
        return captureResolutionService.resolveFromUser(userId, captureId, listId);
    }
}
