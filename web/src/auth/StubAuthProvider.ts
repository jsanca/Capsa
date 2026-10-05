import type { AuthProvider } from "./AuthProvider";

/**
 * Development/stub auth provider.
 *
 * `signIn` and `signOut` resolve immediately and do not persist anything.
 * `getAccessToken` resolves with `VITE_CAPSA_STUB_ACCESS_TOKEN` when the
 * environment variable is set, otherwise `null`.
 *
 * This is intentionally a no-op: the slice does not include a real Google
 * OIDC SDK. The UI and API architecture are designed so that a real provider
 * (`GoogleOidcAuthProvider` or equivalent) can replace this one without
 * changes to `apiCall.ts`, the bootstrap flow, or the invitation page.
 *
 * **Security note:** the stub token must never be used to authorize production
 * behavior. The backend treats this header as unverified metadata in `%dev`
 * and `%test` profiles (OIDC disabled) and as a bearer token in `%prod`.
 */
export class StubAuthProvider implements AuthProvider {
  private cachedToken: string | null;

  constructor(initialToken: string | null = readStubTokenFromEnv()) {
    this.cachedToken = initialToken;
  }

  getAccessToken(): Promise<string | null> {
    return Promise.resolve(this.cachedToken);
  }

  signIn(): Promise<void> {
    // No real authentication happens in the stub. The UI may still gate the
    // first-run flow on a successful `signIn()` so swapping in a real provider
    // does not require restructuring the gate.
    return Promise.resolve();
  }

  signOut(): Promise<void> {
    this.cachedToken = null;
    return Promise.resolve();
  }

  /** Test-only: simulate acquiring a token without changing environment. */
  setStubToken(token: string | null): void {
    this.cachedToken = token;
  }
}

function readStubTokenFromEnv(): string | null {
  const raw = import.meta.env.VITE_CAPSA_STUB_ACCESS_TOKEN;
  if (typeof raw === "string" && raw.length > 0) {
    return raw;
  }
  return null;
}