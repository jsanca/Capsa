package com.capsa.lists.api;

import com.capsa.users.api.UserId;
import java.util.List;

/**
 * Capability for managing {@code CapsaList} aggregates and for enforcing the
* authorization boundary that {@code Item}
 *       creation and completion depend on.
 *
 * <p>Authorization model:
 * <ul>
 *   <li>A {@code CapsaList} has exactly one {@code ownerId}.</li>
 *   <li>Only the owner may read the list and contribute items to it.</li>
 *   <li>{@link #verifyContributionAccess(UserId, ListId)} is the single
 *       authorization check used by other capabilities (notably
 *       {@code ItemService}) before mutating a list's contents.</li>
 * </ul>
 */
public interface ListService {

    /**
     * Creates a new list owned by {@code ownerId}.
     *
     * @param ownerId owning user; non-null
     * @param command input data (name, purpose)
     * @return the persisted {@link ListView}
     * @throws IllegalArgumentException if the command's name is blank
     */
    ListView create(UserId ownerId, CreateListCommand command);

    /**
     * Loads a list by id, enforcing owner-only access.
     *
     * @param requestingUser authenticated caller; non-null
     * @param listId         target list id; non-null
     * @return the {@link ListView}
     * @throws ListNotFoundException     if no list exists with that id
     * @throws ListAccessDeniedException if {@code requestingUser} is not the owner
     */
    ListView getById(UserId requestingUser, ListId listId);

    /**
     * Lists all lists owned by {@code ownerId}.
     *
     * @param ownerId owning user; non-null
     * @return the owner's lists (empty list if none)
     */
    List<ListView> getByUser(UserId ownerId);

    /**
     * Confirms that {@code userId} may add items to the list. Does not return
     * data; throws on failure.
     *
     * @param userId user attempting to contribute; non-null
     * @param listId target list id; non-null
     * @throws ListNotFoundException     if no list exists with that id
     * @throws ListAccessDeniedException if {@code userId} is not the owner
     */
    void verifyContributionAccess(UserId userId, ListId listId);
}
