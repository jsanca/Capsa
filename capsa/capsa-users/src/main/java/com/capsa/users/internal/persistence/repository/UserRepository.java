package com.capsa.users.internal.persistence.repository;

import com.capsa.users.internal.persistence.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository {

    private static final Logger LOG = LoggerFactory.getLogger(UserRepository.class);

    private final EntityManager em;

    @Inject
    public UserRepository(EntityManager em) {
        this.em = em;
    }

    public Optional<UserEntity> findByOidcSubject(final String oidcSubject) {
        LOG.debug("findByOidcSubject oidcSubjectLength={}", oidcSubject == null ? 0 : oidcSubject.length());
        var results = em.createQuery(
                "SELECT u FROM UserEntity u WHERE u.oidcSubject = :sub", UserEntity.class)
            .setParameter("sub", oidcSubject)
            .getResultList();
        final boolean hit = !results.isEmpty();
        LOG.debug("findByOidcSubject hit={}", hit);
        return hit ? Optional.of(results.getFirst()) : Optional.empty();
    }

    public Optional<UserEntity> findById(final UUID id) {
        LOG.debug("findById userId={}", id);
        return Optional.ofNullable(em.find(UserEntity.class, id));
    }

    public UserEntity save(final UserEntity entity) {
        em.persist(entity);
        LOG.debug("persisted user userId={}", entity.getId());
        return entity;
    }
}