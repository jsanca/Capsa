package com.capsa.capture.internal.persistence.repository;

import com.capsa.capture.internal.persistence.entity.ClassificationResolutionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ClassificationResolutionRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ClassificationResolutionRepository.class);

    private final EntityManager em;

    @Inject
    public ClassificationResolutionRepository(EntityManager em) {
        this.em = em;
    }

    public void persist(ClassificationResolutionEntity entity) {
        em.persist(entity);
        LOG.debug("persisted classification resolution captureId={} selectedListId={} resolvedBy={}",
            entity.captureId, entity.selectedListId, entity.resolvedBy);
    }
}