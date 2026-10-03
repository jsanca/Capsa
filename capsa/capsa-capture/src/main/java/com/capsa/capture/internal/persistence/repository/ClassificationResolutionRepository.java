package com.capsa.capture.internal.persistence.repository;

import com.capsa.capture.internal.persistence.entity.ClassificationResolutionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class ClassificationResolutionRepository {

    private final EntityManager em;

    @Inject
    public ClassificationResolutionRepository(EntityManager em) {
        this.em = em;
    }

    public void persist(ClassificationResolutionEntity entity) {
        em.persist(entity);
    }
}
