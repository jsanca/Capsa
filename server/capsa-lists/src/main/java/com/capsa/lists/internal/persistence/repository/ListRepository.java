package com.capsa.lists.internal.persistence.repository;

import com.capsa.lists.internal.persistence.entity.ListEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ListRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ListRepository.class);

    private final EntityManager em;

    @Inject
    public ListRepository(EntityManager em) {
        this.em = em;
    }

    public Optional<ListEntity> findById(final UUID id) {
        LOG.debug("findById listId={}", id);
        return Optional.ofNullable(em.find(ListEntity.class, id));
    }

    public List<ListEntity> findByOwnerId(final UUID ownerId) {
        LOG.debug("findByOwnerId ownerId={}", ownerId);
        var results = em.createQuery(
                "SELECT l FROM ListEntity l WHERE l.ownerId = :ownerId ORDER BY l.createdAt", ListEntity.class)
            .setParameter("ownerId", ownerId)
            .getResultList();
        LOG.debug("findByOwnerId count={}", results.size());
        return results;
    }

    public ListEntity save(final ListEntity entity) {
        em.persist(entity);
        LOG.debug("persisted list listId={} ownerId={}", entity.getId(), entity.getOwnerId());
        return entity;
    }
}