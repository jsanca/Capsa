package com.capsa.items.api;

import java.util.UUID;

/**
 * Input DTO for creating an {@code Item} via direct user add (UC-02).
 *
 * @param listId destination list id; non-null
 * @param name   item name; non-blank (the service validates)
 * @param notes  optional notes; may be {@code null}
 */
public record CreateItemCommand(UUID listId, String name, String notes) {}
