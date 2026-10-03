package com.capsa.lists.api;

/**
 * Thrown when a user attempts to read or contribute to a list they do not own.
 * Mapped to an authorization-failure response by the REST exception mapper.
 */
public class ListAccessDeniedException extends RuntimeException {
    public ListAccessDeniedException(String message) {
        super(message);
    }
}
