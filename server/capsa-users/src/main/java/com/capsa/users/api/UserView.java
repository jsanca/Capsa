package com.capsa.users.api;

/**
 * Read projection of a {@code User} intended for transport across the
 * capability boundary (REST responses, inter-module calls).
 *
 * @param userId internal user identifier
 * @param email  user's email address
 * @param name   display name; may be {@code null}
 */
public record UserView(UserId userId, String email, String name) {}
