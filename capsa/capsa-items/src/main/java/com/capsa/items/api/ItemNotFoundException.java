package com.capsa.items.api;

/**
 * Thrown when an {@code Item} lookup by id yields no result. Mapped to a
 * not-found response by the REST exception mapper.
 */
public class ItemNotFoundException extends RuntimeException {
    public ItemNotFoundException(String message) {
        super(message);
    }
}
