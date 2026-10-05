package com.capsa.classification.internal.service;

import com.capsa.classification.api.ClassificationRequest;
import com.capsa.classification.api.ClassificationResult;
import com.capsa.users.api.UserId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClassificationPipelineTest {

    private static ClassificationRequest anyRequest() {
        return new ClassificationRequest(new UserId(UUID.randomUUID()), "content", List.of());
    }

    @Test
    void classifiedResultTerminatesPipeline() {
        UUID listId = UUID.randomUUID();
        var classified = new ClassificationResult(
            ClassificationResult.Outcome.CLASSIFIED,
            List.of(),
            listId,
            null
        );
        var shouldNotRun = new ClassificationResult(
            ClassificationResult.Outcome.NEEDS_RESOLUTION,
            List.of(),
            null,
            null
        );

        // First strategy classifies; second should never be consulted
        var callCount = new int[]{0};
        ClassificationStrategy first = request -> {
            callCount[0]++;
            return classified;
        };
        ClassificationStrategy second = request -> {
            callCount[0] += 100; // will make the test fail if called
            return shouldNotRun;
        };

        var pipeline = new ClassificationPipeline(List.of(first, second));
        var result = pipeline.classify(anyRequest());

        assertEquals(ClassificationResult.Outcome.CLASSIFIED, result.outcome());
        assertEquals(listId, result.selectedListId());
        assertEquals(1, callCount[0], "Second strategy must not be called after CLASSIFIED");
    }

    @Test
    void executionExceptionPropagates() {
        ClassificationStrategy failing = request -> {
            throw new RuntimeException("Strategy failure");
        };

        var pipeline = new ClassificationPipeline(List.of(failing));

        var ex = assertThrows(RuntimeException.class, () -> pipeline.classify(anyRequest()));
        assertEquals("Strategy failure", ex.getMessage());
    }

    @Test
    void emptyPipeline_returnsNeedsResolution() {
        var pipeline = new ClassificationPipeline(List.of());
        var result = pipeline.classify(anyRequest());
        assertEquals(ClassificationResult.Outcome.NEEDS_RESOLUTION, result.outcome());
    }
}
