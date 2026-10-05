package com.capsa.users.api;

/**
 * Thrown when a {@code User} lookup by id yields no result. Mapped to a
 * not-found response by the REST exception mapper.
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
