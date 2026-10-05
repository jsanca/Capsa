package com.capsa.users.api;

/**
 * Read projection of a {@code User}.
 *
 * @param userId internal user identifier
 * @param email  user's email address
 * @param name   display name; may be {@code null}
 * @param role   assigned role
 */
public record UserView(UserId userId, String email, String name, Role role) {}
