package com.capsa.classification.internal.service;

import com.capsa.classification.api.ClassificationService;
import com.capsa.classification.internal.domain.ClassificationMemoryEntry;
import com.capsa.classification.internal.persistence.entity.ClassificationMemoryEntryEntity;
import com.capsa.classification.internal.persistence.repository.ClassificationMemoryEntryRepository;
import com.capsa.users.api.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
class ClassificationServiceImpl implements ClassificationService {

    private final ClassificationMemoryEntryRepository repository;

    @Inject
    ClassificationServiceImpl(ClassificationMemoryEntryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void recordResolution(UserId userId, String normalizedContent, UUID selectedListId) {
        var entry = ClassificationMemoryEntry.create(
            userId,
            normalizedContent,
            selectedListId,
            ClassificationMemoryEntry.Source.USER_CONFIRMED
        );
        var entity = new ClassificationMemoryEntryEntity();
        entity.setId(entry.getId());
        entity.setUserId(entry.getUserId().value());
        entity.setNormalizedContent(entry.getNormalizedContent());
        entity.setSelectedListId(entry.getSelectedListId());
        entity.setSource(entry.getSource().name());
        entity.setRecordedAt(entry.getRecordedAt());
        repository.save(entity);
    }
}
