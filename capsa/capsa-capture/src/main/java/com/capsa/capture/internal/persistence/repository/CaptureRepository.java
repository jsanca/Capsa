package com.capsa.capture.internal.persistence.repository;

import com.capsa.capture.internal.persistence.entity.CaptureEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CaptureRepository {

    private final EntityManager em;

    @Inject
    public CaptureRepository(EntityManager em) {
        this.em = em;
    }

    public void persist(CaptureEntity entity) {
        em.persist(entity);
    }

    public Optional<CaptureEntity> findById(UUID id) {
        return Optional.ofNullable(em.find(CaptureEntity.class, id));
    }
}
