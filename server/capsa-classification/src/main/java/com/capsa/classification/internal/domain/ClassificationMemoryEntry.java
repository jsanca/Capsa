package com.capsa.classification.internal.domain;

import com.capsa.users.api.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * A single piece of classification evidence: the normalized content, the
 * destination list it was classified to, the user it belongs to, and how the
 * classification was determined.
 *
 * <p>{@code ClassificationMemoryEntry} is the independently-stored unit of
 * classification history. Entries are append-only; conflicting entries for
 * the same {@code normalizedContent} (same user) are valid historical
 * evidence — newer evidence does not overwrite older. The Known
 * Classification strategy queries by
 * {@code userId + normalizedContent} and treats {@code USER_CONFIRMED} as
 * stronger evidence than {@code AUTO}.
 *
 * <p>Why {@code userId} is required: lists belong to users. Two users may
 * legitimately classify identical normalized text into different lists, so
 * evidence must be scoped to the user's semantic list space.
 *
 * <p>Instances are immutable. New entries are produced via
 * {@link #create(UserId, String, UUID, Source)}; previously-persisted entries
 * are rehydrated via {@link #reconstitute(UUID, UserId, String, UUID, Source, Instant)}.
 */
public final class ClassificationMemoryEntry {

    /**
     * Origin of the classification decision.
     * <ul>
     *   <li>{@link #AUTO} — the classification pipeline selected the
     *       destination without user intervention (UC-03 CLASSIFIED path).</li>
     *   <li>{@link #USER_CONFIRMED} — the user explicitly selected the
     *       destination after the pipeline could not (UC-04).</li>
     * </ul>
     */
    public enum Source { AUTO, USER_CONFIRMED }

    private final UUID id;
    private final UserId userId;
    private final String normalizedContent;
    private final UUID selectedListId;
    private final Source source;
    private final Instant recordedAt;

    private ClassificationMemoryEntry(
            UUID id,
            UserId userId,
            String normalizedContent,
            UUID selectedListId,
            Source source,
            Instant recordedAt) {
        this.id = id;
        this.userId = userId;
        this.normalizedContent = normalizedContent;
        this.selectedListId = selectedListId;
        this.source = source;
        this.recordedAt = recordedAt;
    }

    /**
     * Records a new classification decision as append-only evidence.
     *
     * @param userId            owning user; scopes evidence to the user's list space
     * @param normalizedContent normalized capture content; the lookup key
     * @param selectedListId    list that was selected as destination
     * @param source            {@link Source#AUTO} or {@link Source#USER_CONFIRMED}
     * @return a new entry with a freshly-generated id and {@code recordedAt}
     */
    public static ClassificationMemoryEntry create(
            UserId userId,
            String normalizedContent,
            UUID selectedListId,
            Source source) {
        return new ClassificationMemoryEntry(
            UUID.randomUUID(),
            userId,
            normalizedContent,
            selectedListId,
            source,
            Instant.now()
        );
    }

    /**
     * Rehydrates a {@code ClassificationMemoryEntry} from persisted state.
     * Skips validation; intended for repository rehydration only.
     */
    public static ClassificationMemoryEntry reconstitute(
            UUID id,
            UserId userId,
            String normalizedContent,
            UUID selectedListId,
            Source source,
            Instant recordedAt) {
        return new ClassificationMemoryEntry(id, userId, normalizedContent, selectedListId, source, recordedAt);
    }

    /** Stable, system-assigned identifier. */
    public UUID getId() { return id; }

    /** Owning user; scopes evidence to this user's list space. */
    public UserId getUserId() { return userId; }

    /** Normalized capture content; the lookup key used by the Known strategy. */
    public String getNormalizedContent() { return normalizedContent; }

    /** List selected as destination at classification time. */
    public UUID getSelectedListId() { return selectedListId; }

    /** Origin of the classification decision. */
    public Source getSource() { return source; }

    /** Recording timestamp; never modified after creation. */
    public Instant getRecordedAt() { return recordedAt; }
}
