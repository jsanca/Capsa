package com.capsa.capture.internal.persistence.repository;

import com.capsa.capture.internal.persistence.entity.ClassificationAttemptEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ClassificationAttemptRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ClassificationAttemptRepository.class);

    private final EntityManager em;

    @Inject
    public ClassificationAttemptRepository(EntityManager em) {
        this.em = em;
    }

    public void persist(ClassificationAttemptEntity entity) {
        em.persist(entity);
        LOG.debug("persisted classification attempt captureId={}", entity.captureId);
    }

    public Optional<ClassificationAttemptEntity> findByCaptureId(UUID captureId) {
        LOG.debug("findByCaptureId captureId={}", captureId);
        var result = em.createQuery(
                "SELECT a FROM ClassificationAttemptEntity a WHERE a.captureId = :captureId",
                ClassificationAttemptEntity.class)
            .setParameter("captureId", captureId)
            .getResultStream()
            .findFirst();
        LOG.debug("findByCaptureId captureId={} hit={}", captureId, result.isPresent());
        return result;
    }
}