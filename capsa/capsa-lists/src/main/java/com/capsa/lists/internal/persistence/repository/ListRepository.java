package com.capsa.lists.internal.persistence.repository;

import com.capsa.lists.internal.persistence.entity.ListEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ListRepository {

    private final EntityManager em;

    @Inject
    public ListRepository(EntityManager em) {
        this.em = em;
    }

    public Optional<ListEntity> findById(final UUID id) {
        return Optional.ofNullable(em.find(ListEntity.class, id));
    }

    public List<ListEntity> findByOwnerId(final UUID ownerId) {
        return em.createQuery(
                "SELECT l FROM ListEntity l WHERE l.ownerId = :ownerId ORDER BY l.createdAt", ListEntity.class)
            .setParameter("ownerId", ownerId)
            .getResultList();
    }

    public ListEntity save(final ListEntity entity) {
        em.persist(entity);
        return entity;
    }
}
