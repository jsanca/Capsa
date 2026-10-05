package com.capsa.bootstrap.api;

/**
 * Request body for the bootstrap claim endpoint.
 * The token is validated against the externally configured {@code CAPSA_BOOTSTRAP_TOKEN}.
 *
 * @param token bootstrap secret; never persisted or logged
 */
public record ClaimBootstrapCommand(String token) {}
