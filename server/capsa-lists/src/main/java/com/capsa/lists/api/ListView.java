package com.capsa.lists.api;

import com.capsa.users.api.UserId;

/**
 * Read projection of a {@code CapsaList} intended for transport across the
 * capability boundary.
 *
 * @param listId   list identifier
 * @param name     display name
 * @param purpose  optional semantic description; may be {@code null}
 * @param ownerId  owning user
 */
public record ListView(ListId listId, String name, String purpose, UserId ownerId) {}
