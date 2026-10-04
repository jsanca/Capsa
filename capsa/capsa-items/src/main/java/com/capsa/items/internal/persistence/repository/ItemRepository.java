package com.capsa.items.internal.persistence.repository;

import com.capsa.items.internal.persistence.entity.ItemEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ItemRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ItemRepository.class);

    private final EntityManager em;

    @Inject
    public ItemRepository(EntityManager em) {
        this.em = em;
    }

    public Optional<ItemEntity> findById(UUID id) {
        LOG.debug("findById itemId={}", id);
        return Optional.ofNullable(em.find(ItemEntity.class, id));
    }

    public List<ItemEntity> findByListId(UUID listId) {
        LOG.debug("findByListId listId={}", listId);
        var results = em.createQuery(
                "SELECT e FROM ItemEntity e WHERE e.listId = :listId ORDER BY e.createdAt ASC",
                ItemEntity.class)
            .setParameter("listId", listId)
            .getResultList();
        LOG.debug("findByListId listId={} count={}", listId, results.size());
        return results;
    }

    public List<ItemEntity> findByListIdAndStatus(UUID listId, String status) {
        LOG.debug("findByListIdAndStatus listId={} status={}", listId, status);
        var results = em.createQuery(
                "SELECT e FROM ItemEntity e WHERE e.listId = :listId AND e.status = :status ORDER BY e.createdAt ASC",
                ItemEntity.class)
            .setParameter("listId", listId)
            .setParameter("status", status)
            .getResultList();
        LOG.debug("findByListIdAndStatus listId={} status={} count={}", listId, status, results.size());
        return results;
    }

    public void save(ItemEntity entity) {
        em.persist(entity);
        LOG.debug("persisted item itemId={} listId={}", entity.getId(), entity.getListId());
    }
}