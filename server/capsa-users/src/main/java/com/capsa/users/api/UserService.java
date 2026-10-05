package com.capsa.users.api;

/**
 * Capability for provisioning and looking up User records.
 * Provisioning is atomic and concurrency-safe via database-level conflict resolution.
 */
public interface UserService {

    /**
     * Returns the UserView for the given (issuer, subject) pair, creating one if
     * none exists. Concurrent calls for the same pair resolve to the same User.
     * New users are provisioned with role {@link Role#USER}.
     *
     * @param issuer  OIDC issuer claim; non-blank
     * @param subject OIDC subject claim; non-blank
     * @param email   email from identity provider; non-blank
     * @param name    display name; may be {@code null}
     * @return the existing or newly-provisioned UserView
     */
    UserView findOrProvision(String issuer, String subject, String email, String name);

    /**
     * Assigns the given role to an existing User. Must be called within an active
     * transaction (caller owns transactional scope).
     *
     * @param userId target user identifier
     * @param role   role to assign
     * @throws UserNotFoundException if no user exists with that id
     */
    void assignRole(UserId userId, Role role);

    /**
     * Looks up a UserView by internal id.
     *
     * @param userId the internal user identifier
     * @return the matching UserView
     * @throws UserNotFoundException if no user exists with that id
     */
    UserView findById(UserId userId);
}
