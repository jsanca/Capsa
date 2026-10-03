package com.capsa.users.api;

/**
 * Capability for provisioning and looking up {@link UserView} records.
 *
 * <p>Implementations bridge the OIDC authentication context and the
 * {@code User} aggregate. The provisioning step is idempotent on
 * {@code oidcSubject}: a second call for the same subject returns the
 * existing {@code UserView}.
 */
public interface UserService {

    /**
     * Returns the {@code UserView} for {@code oidcSubject}, creating one if
     * none exists. Used at the boundary of an authenticated request to bind
     * the external identity to the internal {@code User} aggregate.
     *
     * @param oidcSubject the OIDC subject claim; non-blank
     * @param email       email from the identity provider; non-blank
     * @param name        display name from the identity provider; may be {@code null}
     * @return the existing or newly-provisioned {@code UserView}
     */
    UserView findOrProvision(String oidcSubject, String email, String name);

    /**
     * Looks up a {@code UserView} by its internal id.
     *
     * @param userId the internal user identifier
     * @return the matching {@code UserView}
     * @throws UserNotFoundException if no user exists with that id
     */
    UserView findById(UserId userId);
}
