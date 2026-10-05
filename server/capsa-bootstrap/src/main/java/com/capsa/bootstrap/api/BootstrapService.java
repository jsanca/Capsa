package com.capsa.bootstrap.api;

import com.capsa.users.api.ExternalIdentity;

/**
 * Capability for the one-time first-admin bootstrap.
 *
 * <p>Bootstrap is the atomic transition that assigns the first {@code ADMIN} role
 * to an authenticated identity. It is protected by an externally configured secret
 * and a database singleton row that prevents more than one successful claim.
 *
 * <p>After a successful claim, {@link #getStatus()} returns
 * {@code required = false} and further claim attempts return 409.
 */
public interface BootstrapService {

    /**
     * Returns the current bootstrap state.
     * Safe to call without authentication; never discloses token state.
     */
    BootstrapStatus getStatus();

    /**
     * Claims the bootstrap singleton, provisioning the caller as the first ADMIN.
     *
     * <p>Transaction contract: user provisioning, bootstrap claim insert, and
     * ADMIN role assignment all occur in a single transaction. Failure at any
     * step rolls back the entire operation.
     *
     * @param identity the verified OIDC identity of the authenticated caller
     * @param command  contains the bootstrap token to validate
     * @return the resulting {@link BootstrapStatus}
     * @throws BootstrapAlreadyClaimedException if bootstrap has already been claimed
     * @throws BootstrapTokenInvalidException   if the token is invalid or missing
     */
    BootstrapStatus claim(ExternalIdentity identity, ClaimBootstrapCommand command);
}
