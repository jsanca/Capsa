package com.capsa.lists.api;

/**
 * Thrown when a {@code CapsaList} lookup yields no result. Mapped to a
 * not-found response by the REST exception mapper.
 */
public class ListNotFoundException extends RuntimeException {
    public ListNotFoundException(String message) {
        super(message);
    }
}
