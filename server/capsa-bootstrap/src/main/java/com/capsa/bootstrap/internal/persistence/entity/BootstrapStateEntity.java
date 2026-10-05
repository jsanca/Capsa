package com.capsa.bootstrap.internal.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bootstrap_state")
public class BootstrapStateEntity {

    @Id
    @Column(name = "id", updatable = false)
    private Integer id;

    @Column(name = "claimed_at", nullable = false, updatable = false)
    private Instant claimedAt;

    @Column(name = "claimed_by_user_id", nullable = false, updatable = false)
    private UUID claimedByUserId;

    public BootstrapStateEntity() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }

    public UUID getClaimedByUserId() { return claimedByUserId; }
    public void setClaimedByUserId(UUID claimedByUserId) { this.claimedByUserId = claimedByUserId; }
}
