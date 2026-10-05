package com.capsa.users.internal.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representing an authenticated Capsa account holder.
 *
 * <p>A {@code User} owns {@code Lists}, submits
 * {@code Captures}, and resolves ambiguous
 * classifications. Authentication is anchored to the OIDC subject claim; the
 * User is provisioned lazily on first authenticated access.
 *
 * <p>Instances are immutable. New Users are produced via
 * {@link #create(String, String, String)}; previously-persisted Users are
 * rehydrated via {@link #reconstitute(UUID, String, String, String, Instant)}.
 *
 * <p>Invariants:
 * <ul>
 *   <li>{@code id} is non-null after creation.</li>
 *   <li>{@code oidcSubject} is non-blank and unique.</li>
 *   <li>{@code email} is non-blank.</li>
 * </ul>
 */
public final class User {
    private final UUID id;
    private final String oidcSubject;
    private final String email;
    private final String name;
    private final Instant createdAt;

    private User(UUID id, String oidcSubject, String email, String name, Instant createdAt) {
        this.id = id;
        this.oidcSubject = oidcSubject;
        this.email = email;
        this.name = name;
        this.createdAt = createdAt;
    }

    /**
     * Provisions a new {@code User} for a previously-unseen OIDC subject.
     *
     * @param oidcSubject the stable, non-blank OIDC subject identifier
     * @param email       the user's email; non-blank
     * @param name        display name; may be {@code null}
     * @return a new {@code User} with a freshly-generated id and {@code createdAt}
     * @throws NullPointerException     if {@code oidcSubject} or {@code email} is null
     * @throws IllegalArgumentException if {@code oidcSubject} or {@code email} is blank
     */
    public static User create(String oidcSubject, String email, String name) {
        Objects.requireNonNull(oidcSubject, "oidcSubject");
        if (oidcSubject.isBlank()) throw new IllegalArgumentException("oidcSubject must not be blank");
        Objects.requireNonNull(email, "email");
        if (email.isBlank()) throw new IllegalArgumentException("email must not be blank");
        return new User(UUID.randomUUID(), oidcSubject, email, name, Instant.now());
    }

    /**
     * Rehydrates a {@code User} from persisted state. Skips validation; intended
     * for repository rehydration only.
     */
    public static User reconstitute(UUID id, String oidcSubject, String email, String name, Instant createdAt) {
        return new User(id, oidcSubject, email, name, createdAt);
    }

    /** Stable, system-assigned identifier. */
    public UUID id() { return id; }

    /** OIDC subject claim; the authentication anchor. */
    public String oidcSubject() { return oidcSubject; }

    /** User's email address. */
    public String email() { return email; }

    /** Display name; may be {@code null} if not provided by the identity provider. */
    public String name() { return name; }

    /** Provisioning timestamp. */
    public Instant createdAt() { return createdAt; }
}
