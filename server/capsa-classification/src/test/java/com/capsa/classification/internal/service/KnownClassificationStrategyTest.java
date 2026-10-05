package com.capsa.classification.internal.service;

import com.capsa.classification.api.ClassificationRequest;
import com.capsa.classification.api.ClassificationResult;
import com.capsa.classification.internal.persistence.entity.ClassificationMemoryEntryEntity;
import com.capsa.classification.internal.persistence.repository.ClassificationMemoryEntryRepository;
import com.capsa.users.api.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KnownClassificationStrategyTest {

    private static ClassificationMemoryEntryRepository stubRepository(
            Optional<ClassificationMemoryEntryEntity> result) {
        return new ClassificationMemoryEntryRepository() {
            @Override
            public Optional<ClassificationMemoryEntryEntity> findLatestUserConfirmed(
                    UserId userId, String normalizedContent) {
                return result;
            }

            @Override
            public void save(ClassificationMemoryEntryEntity entity) {
                // no-op in test
            }
        };
    }

    @Test
    void matchFound_returnsClassified() {
        UUID listId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        var entity = new ClassificationMemoryEntryEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setNormalizedContent("buy milk");
        entity.setSelectedListId(listId);
        entity.setSource("USER_CONFIRMED");
        entity.setRecordedAt(Instant.now());

        var strategy = new KnownClassificationStrategy(stubRepository(Optional.of(entity)));
        var request = new ClassificationRequest(new UserId(userId), "buy milk", List.of());

        var result = strategy.classify(request);

        assertEquals(ClassificationResult.Outcome.CLASSIFIED, result.outcome());
        assertEquals(listId, result.selectedListId());
        assertFalse(result.candidates().isEmpty());
        assertEquals(1.0, result.candidates().getFirst().confidence());
    }

    @Test
    void noMatch_returnsNeedsResolution() {
        var strategy = new KnownClassificationStrategy(stubRepository(Optional.empty()));
        var request = new ClassificationRequest(new UserId(UUID.randomUUID()), "unknown content", List.of());

        var result = strategy.classify(request);

        assertEquals(ClassificationResult.Outcome.NEEDS_RESOLUTION, result.outcome());
        assertNull(result.selectedListId());
    }
}
