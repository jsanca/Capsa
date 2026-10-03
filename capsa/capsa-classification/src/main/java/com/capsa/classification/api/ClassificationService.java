package com.capsa.classification.api;

import com.capsa.users.api.UserId;
import java.util.UUID;

/**
 * Capability for appending classification evidence to the
 * {@code ClassificationMemory}.
 *
 * <p>The {@code capture} module invokes this both after automatic
 * classification (UC-03) and after the user resolves an ambiguous capture
 * (UC-04). The {@code Source} recorded is determined by the caller.
 */
public interface ClassificationService {

    /**
     * Appends a {@code ClassificationMemoryEntry} for the supplied decision.
     * Does not overwrite prior evidence for the same
     * {@code (userId, normalizedContent)} — entries are append-only.
     *
     * @param userId            owning user; non-null
     * @param normalizedContent normalized capture content; the lookup key
     * @param selectedListId    list that was selected as destination
     */
    void recordResolution(UserId userId, String normalizedContent, UUID selectedListId);
}
