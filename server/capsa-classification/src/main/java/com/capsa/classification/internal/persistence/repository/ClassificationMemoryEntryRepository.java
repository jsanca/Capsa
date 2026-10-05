package com.capsa.classification.internal.persistence.repository;

import com.capsa.classification.internal.persistence.entity.ClassificationMemoryEntryEntity;
import com.capsa.users.api.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

@ApplicationScoped
public class ClassificationMemoryEntryRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ClassificationMemoryEntryRepository.class);

    private final EntityManager em;

    @Inject
    public ClassificationMemoryEntryRepository(EntityManager em) {
        this.em = em;
    }

    protected ClassificationMemoryEntryRepository() {
        this.em = null;
    }

    public Optional<ClassificationMemoryEntryEntity> findLatestUserConfirmed(UserId userId, String normalizedContent) {
        LOG.debug("findLatestUserConfirmed userId={} contentLength={}",
            userId.value(), normalizedContent == null ? 0 : normalizedContent.length());
        var results = em.createQuery(
                "SELECT e FROM ClassificationMemoryEntryEntity e " +
                "WHERE e.userId = :userId AND e.normalizedContent = :content AND e.source = 'USER_CONFIRMED' " +
                "ORDER BY e.recordedAt DESC",
                ClassificationMemoryEntryEntity.class)
            .setParameter("userId", userId.value())
            .setParameter("content", normalizedContent)
            .setMaxResults(1)
            .getResultList();
        final boolean hit = !results.isEmpty();
        LOG.debug("findLatestUserConfirmed userId={} hit={}", userId.value(), hit);
        return hit ? Optional.of(results.getFirst()) : Optional.empty();
    }

    public void save(ClassificationMemoryEntryEntity entity) {
        em.persist(entity);
        LOG.debug("persisted classification memory entry entryId={} userId={} selectedListId={}",
            entity.getId(), entity.getUserId(), entity.getSelectedListId());
    }
}
