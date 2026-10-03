package com.capsa.capture.internal.persistence.repository;

import com.capsa.capture.internal.persistence.entity.ClassificationAttemptEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ClassificationAttemptRepository {

    private final EntityManager em;

    @Inject
    public ClassificationAttemptRepository(EntityManager em) {
        this.em = em;
    }

    public void persist(ClassificationAttemptEntity entity) {
        em.persist(entity);
    }

    public Optional<ClassificationAttemptEntity> findByCaptureId(UUID captureId) {
        return em.createQuery(
                "SELECT a FROM ClassificationAttemptEntity a WHERE a.captureId = :captureId",
                ClassificationAttemptEntity.class)
            .setParameter("captureId", captureId)
            .getResultStream()
            .findFirst();
    }
}
