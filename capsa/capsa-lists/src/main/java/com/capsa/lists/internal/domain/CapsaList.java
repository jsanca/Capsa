package com.capsa.lists.internal.domain;

import com.capsa.lists.api.ListId;
import com.capsa.users.api.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representing a named, semantic collection owned by a
 * {@code User}. A {@code CapsaList} provides the destination for
 * {@code Items} and the semantic criteria
 * Capsa uses to determine whether a {@code Capture}
 * belongs to it.
 *
 * <p>Key design decision: a {@code CapsaList} does not hold its Items in
 * memory. Items are queried via repository by {@code listId + status}. This
 * avoids loading the full Item history when a List is accessed and supports
 * unbounded List growth without aggregate redesign.
 *
 * <p>Instances are immutable. New Lists are produced via
 * {@link #create(UserId, String, String)}; previously-persisted Lists are
 * rehydrated via {@link #reconstitute(ListId, UserId, String, String, Instant)}.
 *
 * <p>Invariants:
 * <ul>
 *   <li>{@code id} is non-null after creation.</li>
 *   <li>{@code ownerId} is non-null.</li>
 *   <li>{@code name} is non-blank (leading/trailing whitespace stripped).</li>
 * </ul>
 */
public final class CapsaList {
    private final ListId id;
    private final UserId ownerId;
    private final String name;
    private final String purpose;
    private final Instant createdAt;

    private CapsaList(ListId id, UserId ownerId, String name, String purpose, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.purpose = purpose;
        this.createdAt = createdAt;
    }

    /**
     * Creates a new {@code CapsaList} for {@code ownerId}.
     *
     * @param ownerId owning user; non-null
     * @param name    list name; non-blank (stripped of surrounding whitespace)
     * @param purpose optional semantic description of what belongs in the list;
     *                may be {@code null}
     * @return a new {@code CapsaList} with a freshly-generated id
     * @throws NullPointerException     if {@code ownerId} or {@code name} is null
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static CapsaList create(UserId ownerId, String name, String purpose) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        return new CapsaList(new ListId(UUID.randomUUID()), ownerId, name.strip(), purpose, Instant.now());
    }

    /**
     * Rehydrates a {@code CapsaList} from persisted state. Skips validation;
     * intended for repository rehydration only.
     */
    public static CapsaList reconstitute(ListId id, UserId ownerId, String name, String purpose, Instant createdAt) {
        return new CapsaList(id, ownerId, name, purpose, createdAt);
    }

    /** Stable, system-assigned identifier. */
    public ListId id() { return id; }

    /** Owning user; never changes after creation. */
    public UserId ownerId() { return ownerId; }

    /** Display name (stripped of surrounding whitespace). */
    public String name() { return name; }

    /** Optional semantic description used to guide classification; may be {@code null}. */
    public String purpose() { return purpose; }

    /** Creation timestamp. */
    public Instant createdAt() { return createdAt; }
}
