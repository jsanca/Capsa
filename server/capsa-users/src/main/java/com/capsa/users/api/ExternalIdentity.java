package com.capsa.users.api;

/**
 * Verified OIDC identity from a JWT bearer token.
 * {@code issuer} and {@code subject} are the persistent identity anchor.
 * {@code email} and {@code displayName} are profile data.
 */
public record ExternalIdentity(String issuer, String subject, String email, String displayName) {}
