package com.capsa.items.internal.domain;

import com.capsa.lists.api.ListId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representing a single actionable or retainable occurrence
 * within a {@code CapsaList}. An {@code Item} represents a specific
 * occurrence, not a recurring real-world concept — a repeated need creates
 * a new {@code Item} rather than reopening a completed one.
 *
 * <p>Lifecycle: {@link ItemStatus#PENDING PENDING} on creation; transitions
 * to {@link ItemStatus#DONE DONE} via {@link #complete()} (idempotent).
 *
 * <p>Why {@code Item} is its own aggregate root (not nested in List):
 * Items have an independent lifecycle, are queried by
 * {@code listId + status} without loading the List, and may accumulate large
 * histories per list.
 *
 * <p>Invariants:
 * <ul>
 *   <li>{@code id} is non-null after creation.</li>
 *   <li>{@code listId} is non-null and never changes.</li>
 *   <li>{@code name} is non-blank (stripped of surrounding whitespace).</li>
 *   <li>{@code status == PENDING} on creation.</li>
 *   <li>{@code completedAt != null} iff {@code status == DONE}.</li>
 *   <li>Completion never changes {@code listId} and never deletes the Item.</li>
 * </ul>
 */
public class Item {

    /**
     * Lifecycle status. {@link #PENDING} on creation; {@link #DONE} after
     * completion. New lifecycle states (e.g. {@code ON_HOLD}, {@code ARCHIVED})
     * may be added when requirements justify them.
     */
    public enum ItemStatus { PENDING, DONE }

    private final UUID id;
    private final UUID listId;
    private final UUID captureId;
    private final String name;
    private final String notes;
    private ItemStatus status;
    private final Instant createdAt;
    private Instant completedAt;

    private Item(UUID id, UUID listId, UUID captureId, String name, String notes,
                 ItemStatus status, Instant createdAt, Instant completedAt) {
        this.id = id;
        this.listId = listId;
        this.captureId = captureId;
        this.name = name;
        this.notes = notes;
        this.status = status;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    /**
     * Creates a new {@code Item} in {@code listId} via direct user input
     * (UC-02). The resulting Item has no originating {@code Capture}.
     *
     * @param listId destination list; non-null
     * @param name   item name; non-blank (stripped of surrounding whitespace)
     * @param notes  optional free-text notes; may be {@code null}
     * @return a new {@code Item} in {@link ItemStatus#PENDING PENDING} state
     * @throws NullPointerException     if {@code listId} or {@code name} is null
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static Item create(ListId listId, String name, String notes) {
        Objects.requireNonNull(listId, "listId");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        return new Item(UUID.randomUUID(), listId.value(), null, name.strip(), notes,
                ItemStatus.PENDING, Instant.now(), null);
    }

    /**
     * Creates a new {@code Item} in {@code listId} from a resolved
     * {@code Capture} (UC-03/UC-04). The {@code captureId} is retained for
     * traceability from Item back to its originating Capture.
     *
     * @param listId    destination list; non-null
     * @param captureId originating capture id; non-null
     * @param name      item name; non-blank (stripped of surrounding whitespace)
     * @param notes     optional free-text notes; may be {@code null}
     * @return a new {@code Item} in {@link ItemStatus#PENDING PENDING} state
     */
    public static Item createFromCapture(ListId listId, UUID captureId, String name, String notes) {
        Objects.requireNonNull(listId, "listId");
        Objects.requireNonNull(captureId, "captureId");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        return new Item(UUID.randomUUID(), listId.value(), captureId, name.strip(), notes,
                ItemStatus.PENDING, Instant.now(), null);
    }

    /**
     * Transitions the Item from {@link ItemStatus#PENDING PENDING} to
     * {@link ItemStatus#DONE DONE} and records {@code completedAt}.
     *
     * <p>Idempotent: a second call on an already-{@code DONE} Item is a no-op.
     * A completed Item is never reopened; a new need for the same real-world
     * concept creates a new Item occurrence.
     */
    public void complete() {
        if (status == ItemStatus.DONE) return;
        this.status = ItemStatus.DONE;
        this.completedAt = Instant.now();
    }

    /** Stable, system-assigned identifier. */
    public UUID getId()            { return id; }

    /** Owning list; never changes after creation. */
    public UUID getListId()        { return listId; }

    /** Originating capture id; {@code null} for direct adds (UC-02). */
    public UUID getCaptureId()     { return captureId; }

    /** Display name (stripped of surrounding whitespace). */
    public String getName()        { return name; }

    /** Optional free-text notes; may be {@code null}. */
    public String getNotes()       { return notes; }

    /** Current lifecycle status. */
    public ItemStatus getStatus()  { return status; }

    /** Creation timestamp; domain-significant history. */
    public Instant getCreatedAt()  { return createdAt; }

    /** Completion timestamp; {@code null} until {@code status == DONE}. */
    public Instant getCompletedAt(){ return completedAt; }
}
