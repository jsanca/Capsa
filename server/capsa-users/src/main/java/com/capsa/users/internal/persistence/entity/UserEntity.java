package com.capsa.users.internal.persistence.entity;

import com.capsa.users.api.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "oidc_issuer", nullable = false, updatable = false, length = 255)
    private String oidcIssuer;

    @Column(name = "oidc_subject", nullable = false, updatable = false, length = 255)
    private String oidcSubject;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "name", length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getOidcIssuer() { return oidcIssuer; }
    public void setOidcIssuer(String oidcIssuer) { this.oidcIssuer = oidcIssuer; }

    public String getOidcSubject() { return oidcSubject; }
    public void setOidcSubject(String oidcSubject) { this.oidcSubject = oidcSubject; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
