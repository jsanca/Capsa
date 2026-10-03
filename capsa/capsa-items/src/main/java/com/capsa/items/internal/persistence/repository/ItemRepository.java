package com.capsa.items.internal.persistence.repository;

import com.capsa.items.internal.persistence.entity.ItemEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ItemRepository {

    private final EntityManager em;

    @Inject
    public ItemRepository(EntityManager em) {
        this.em = em;
    }

    public Optional<ItemEntity> findById(UUID id) {
        return Optional.ofNullable(em.find(ItemEntity.class, id));
    }

    public List<ItemEntity> findByListId(UUID listId) {
        return em.createQuery(
                "SELECT e FROM ItemEntity e WHERE e.listId = :listId ORDER BY e.createdAt ASC",
                ItemEntity.class)
            .setParameter("listId", listId)
            .getResultList();
    }

    public List<ItemEntity> findByListIdAndStatus(UUID listId, String status) {
        return em.createQuery(
                "SELECT e FROM ItemEntity e WHERE e.listId = :listId AND e.status = :status ORDER BY e.createdAt ASC",
                ItemEntity.class)
            .setParameter("listId", listId)
            .setParameter("status", status)
            .getResultList();
    }

    public void save(ItemEntity entity) {
        em.persist(entity);
    }
}
