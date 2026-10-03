package com.capsa.users.internal.persistence.repository;

import com.capsa.users.internal.persistence.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository {

    private final EntityManager em;

    @Inject
    public UserRepository(EntityManager em) {
        this.em = em;
    }

    public Optional<UserEntity> findByOidcSubject(final String oidcSubject) {

        var results = em.createQuery(
                "SELECT u FROM UserEntity u WHERE u.oidcSubject = :sub", UserEntity.class)
            .setParameter("sub", oidcSubject)
            .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<UserEntity> findById(final UUID id) {

        return Optional.ofNullable(em.find(UserEntity.class, id));
    }

    public UserEntity save(final UserEntity entity) {

        em.persist(entity);
        return entity;
    }
}
