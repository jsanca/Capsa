package com.capsa.capture.internal.persistence.repository;

import com.capsa.capture.internal.persistence.entity.CaptureEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CaptureRepository {

    private static final Logger LOG = LoggerFactory.getLogger(CaptureRepository.class);

    private final EntityManager em;

    @Inject
    public CaptureRepository(EntityManager em) {
        this.em = em;
    }

    public void persist(CaptureEntity entity) {
        em.persist(entity);
        LOG.debug("persisted capture captureId={} userId={}", entity.id, entity.userId);
    }

    public Optional<CaptureEntity> findById(UUID id) {
        LOG.debug("findById captureId={}", id);
        return Optional.ofNullable(em.find(CaptureEntity.class, id));
    }
}