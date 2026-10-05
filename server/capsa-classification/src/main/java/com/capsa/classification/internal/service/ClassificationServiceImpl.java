package com.capsa.classification.internal.service;

import com.capsa.classification.api.ClassificationService;
import com.capsa.classification.internal.domain.ClassificationMemoryEntry;
import com.capsa.classification.internal.persistence.entity.ClassificationMemoryEntryEntity;
import com.capsa.classification.internal.persistence.repository.ClassificationMemoryEntryRepository;
import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.Observability;
import com.capsa.observability.api.events.ClassificationRecorded;
import com.capsa.users.api.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
class ClassificationServiceImpl implements ClassificationService {

    private static final Logger LOG = LoggerFactory.getLogger(ClassificationServiceImpl.class);

    private final ClassificationMemoryEntryRepository repository;
    private final Observability observability;

    @Inject
    ClassificationServiceImpl(ClassificationMemoryEntryRepository repository, Observability observability) {
        this.repository = repository;
        this.observability = observability;
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
        observability.emit(new ClassificationRecorded(
            UUID.randomUUID(),
            Instant.now(),
            new EventActor.User(userId.value()),
            selectedListId
        ));
        LOG.debug("Recorded classification memory entry userId={} selectedListId={} entryId={}",
            userId.value(), selectedListId, entry.getId());
    }
}
