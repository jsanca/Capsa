package com.capsa.users.api;

/**
 * Request-scoped abstraction over the authenticated OIDC principal.
 *
 * <p>{@link #userId()} triggers lazy find-or-provision of the application-level User.
 * {@link #identity()} returns the raw verified claims without provisioning.
 */
public interface CurrentUser {
    /**
     * Returns the application-level {@link UserId} for the authenticated user.
     * Triggers find-or-provision on first call.
     */
    UserId userId();

    /**
     * Returns the verified OIDC identity (issuer + subject) plus profile claims
     * from the JWT, without triggering user provisioning.
     */
    ExternalIdentity identity();
}
