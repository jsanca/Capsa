package com.capsa.bootstrap.internal.persistence.repository;

import com.capsa.bootstrap.internal.persistence.entity.BootstrapStateEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class BootstrapRepository {

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapRepository.class);

    private final EntityManager em;

    @Inject
    public BootstrapRepository(EntityManager em) {
        this.em = em;
    }

    public Optional<BootstrapStateEntity> findBootstrapState() {
        return Optional.ofNullable(em.find(BootstrapStateEntity.class, 1));
    }

    /**
     * Atomically claims the bootstrap singleton. Returns {@code true} if the row
     * was inserted (this caller won the race), {@code false} if already claimed.
     * Uses ON CONFLICT(id) DO NOTHING on the fixed singleton key (id=1).
     */
    public boolean claim(UUID claimedByUserId) {
        int rows = em.createNativeQuery(
                "INSERT INTO bootstrap_state(id, claimed_by_user_id) " +
                "VALUES (1, :userId) ON CONFLICT(id) DO NOTHING")
            .setParameter("userId", claimedByUserId)
            .executeUpdate();
        LOG.debug("bootstrap claim rows={}", rows);
        return rows == 1;
    }
}
