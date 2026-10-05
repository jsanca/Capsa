/**
 * Provider-agnostic interface for acquiring an access token to attach to
 * outbound Capsa Server calls as `Authorization: Bearer <token>`.
 *
 * The frontend never constructs this header directly in presentation code; the
 * centralized API seam in `api/apiCall.ts` always routes through the active
 * provider. This makes a future swap from the development stub to a real OIDC
 * SDK a single-file change.
 */
export interface AuthProvider {
  /**
   * Returns the current access token, or `null` if no user is authenticated.
   * May return a previously cached value without performing I/O.
   */
  getAccessToken(): Promise<string | null>;

  /**
   * Begins an authentication flow. Resolves once the provider considers the
   * user authenticated (or rejects on failure). The development stub resolves
   * immediately so the UI can drive the bootstrap flow without a real OIDC
   * client.
   */
  signIn(): Promise<void>;

  /**
   * Clears any locally cached authentication state. The development stub does
   * not persist state; a real provider must clear its token store here.
   */
  signOut(): Promise<void>;
}