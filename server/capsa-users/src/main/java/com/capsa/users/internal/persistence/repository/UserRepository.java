package com.capsa.users.internal.persistence.repository;

import com.capsa.users.internal.persistence.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
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

    public Optional<UserEntity> findByIssuerAndSubject(String issuer, String subject) {
        LOG.debug("findByIssuerAndSubject");
        var results = em.createQuery(
                "SELECT u FROM UserEntity u WHERE u.oidcIssuer = :issuer AND u.oidcSubject = :subject",
                UserEntity.class)
            .setParameter("issuer", issuer)
            .setParameter("subject", subject)
            .getResultList();
        boolean hit = !results.isEmpty();
        LOG.debug("findByIssuerAndSubject hit={}", hit);
        return hit ? Optional.of(results.getFirst()) : Optional.empty();
    }

    /**
     * Atomically inserts a new user row. Returns {@code true} if the row was
     * inserted, {@code false} if a row with the same (oidc_issuer, oidc_subject)
     * already exists (ON CONFLICT DO NOTHING).
     */
    public boolean insertIfAbsent(UUID id, String issuer, String subject,
                                   String email, String name, String role, Instant createdAt) {
        int rows = em.createNativeQuery(
                "INSERT INTO users(id, oidc_issuer, oidc_subject, email, name, role, created_at) " +
                "VALUES (:id, :issuer, :subject, :email, :name, :role, :createdAt) " +
                "ON CONFLICT (oidc_issuer, oidc_subject) DO NOTHING")
            .setParameter("id", id)
            .setParameter("issuer", issuer)
            .setParameter("subject", subject)
            .setParameter("email", email)
            .setParameter("name", name)
            .setParameter("role", role)
            .setParameter("createdAt", createdAt)
            .executeUpdate();
        LOG.debug("insertIfAbsent inserted={}", rows == 1);
        return rows == 1;
    }

    public Optional<UserEntity> findById(UUID id) {
        LOG.debug("findById userId={}", id);
        return Optional.ofNullable(em.find(UserEntity.class, id));
    }
}
