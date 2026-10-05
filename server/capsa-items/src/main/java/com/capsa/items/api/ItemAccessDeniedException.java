package com.capsa.items.api;

/**
 * Thrown when a user attempts to operate on an Item whose owning list they do
 * not own. Mapped to an authorization-failure response by the REST exception
 * mapper.
 */
public class ItemAccessDeniedException extends RuntimeException {
    public ItemAccessDeniedException(String message) {
        super(message);
    }
}
