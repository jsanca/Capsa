package com.capsa.users.api;

/**
 * Request-scoped binding that exposes the authenticated {@link UserId} for the
 * current execution context. Injected into capability code that needs to act
 * on behalf of the calling user without receiving the id as a parameter on
 * every operation.
 *
 * <p>Implementations are provided by the authentication/integration layer;
 * domain code depends only on this interface.
 */
public interface CurrentUser {

    /** Identifier of the authenticated user for the current request. */
    UserId userId();
}
