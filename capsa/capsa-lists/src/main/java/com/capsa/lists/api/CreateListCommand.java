package com.capsa.lists.api;

/**
 * Input DTO for creating a {@code CapsaList}.
 *
 * @param name    list name; non-blank (the service validates and strips it)
 * @param purpose optional semantic description; may be {@code null}
 */
public record CreateListCommand(String name, String purpose) {}
