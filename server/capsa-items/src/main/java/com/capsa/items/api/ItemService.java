package com.capsa.items.api;

import com.capsa.lists.api.ListId;
import com.capsa.users.api.UserId;
import java.util.List;
import java.util.UUID;

/**
 * Capability for creating, completing, and querying {@code Item} aggregates.
 *
 * <p>All operations are authorized against the owning list via
 * {@link com.capsa.lists.api.ListService#verifyContributionAccess(com.capsa.users.api.UserId, com.capsa.lists.api.ListId)};
 * non-owners receive {@link ItemAccessDeniedException}. Existence checks
 * produce {@link ItemNotFoundException}.
 */
public interface ItemService {

    /**
     * Creates a new {@code Item} directly in {@code command.listId()}
     * (UC-02). The caller must own the destination list.
     *
     * @param userId  authenticated user; non-null
     * @param command input data (listId, name, notes)
     * @return the persisted {@link ItemView} in {@code PENDING} state
     * @throws com.capsa.lists.api.ListAccessDeniedException if the user does not own the list
     * @throws IllegalArgumentException  if the command's name is blank
     */
    ItemView create(UserId userId, CreateItemCommand command);

    /**
     * Creates a new {@code Item} from a resolved {@code Capture} (UC-04).
     * The {@code captureId} is recorded for traceability. The caller must own
     * the destination list.
     *
     * @param userId    authenticated user; non-null
     * @param listId    destination list; non-null
     * @param captureId originating capture id; non-null
     * @param name      item name; non-blank
     * @param notes     optional notes; may be {@code null}
     * @return the persisted {@link ItemView} in {@code PENDING} state
     */
    ItemView createFromCapture(UserId userId, ListId listId, UUID captureId, String name, String notes);

    /**
     * Transitions the Item from {@code PENDING} to {@code DONE} (UC-06).
     * Idempotent: completing an already-completed Item returns its current
     * state without throwing.
     *
     * @param userId authenticated user; non-null
     * @param itemId target item id; non-null
     * @return the {@link ItemView} reflecting the (idempotent) result
     * @throws ItemNotFoundException     if no item exists with that id
     * @throws ItemAccessDeniedException if the user does not own the list
     */
    ItemView complete(UserId userId, ItemId itemId);

    /**
     * Returns Items belonging to {@code listId}, filtered by lifecycle view.
     *
     * @param userId authenticated user; non-null
     * @param listId target list id; non-null
     * @param filter {@link ItemStatusFilter#ACTIVE ACTIVE} (default),
     *               {@link ItemStatusFilter#HISTORY HISTORY}, or
     *               {@link ItemStatusFilter#ALL ALL}
     * @return matching {@link ItemView}s (empty list if none)
     * @throws com.capsa.lists.api.ListAccessDeniedException if the user does not own the list
     */
    List<ItemView> getByList(UserId userId, ListId listId, ItemStatusFilter filter);
}
