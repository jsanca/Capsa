package com.capsa.classification.internal.persistence.repository;

import com.capsa.classification.internal.persistence.entity.ClassificationMemoryEntryEntity;
import com.capsa.users.api.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.Optional;

@ApplicationScoped
public class ClassificationMemoryEntryRepository {

    private final EntityManager em;

    @Inject
    public ClassificationMemoryEntryRepository(EntityManager em) {
        this.em = em;
    }

    protected ClassificationMemoryEntryRepository() {
        this.em = null;
    }

    public Optional<ClassificationMemoryEntryEntity> findLatestUserConfirmed(UserId userId, String normalizedContent) {
        var results = em.createQuery(
                "SELECT e FROM ClassificationMemoryEntryEntity e " +
                "WHERE e.userId = :userId AND e.normalizedContent = :content AND e.source = 'USER_CONFIRMED' " +
                "ORDER BY e.recordedAt DESC",
                ClassificationMemoryEntryEntity.class)
            .setParameter("userId", userId.value())
            .setParameter("content", normalizedContent)
            .setMaxResults(1)
            .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public void save(ClassificationMemoryEntryEntity entity) {
        em.persist(entity);
    }
}
