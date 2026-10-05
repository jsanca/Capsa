import type { AuthProvider } from "./AuthProvider";
import { StubAuthProvider } from "./StubAuthProvider";
import { notifyAuthChange } from "./authEvents";

/**
 * Process-wide holder for the active `AuthProvider`.
 *
 * The provider is a singleton so that `apiCall.ts` — which is called from
 * non-component code paths — can reach the active provider without React
 * context plumbing. Tests replace it via `setActiveAuthProvider` before
 * rendering.
 *
 * The default provider is the development stub. Application code must not
 * depend on which provider is active; it must only depend on the interface.
 */
let activeProvider: AuthProvider = new StubAuthProvider();

export function getActiveAuthProvider(): AuthProvider {
  return activeProvider;
}

export function setActiveAuthProvider(provider: AuthProvider): void {
  activeProvider = provider;
  notifyAuthChange();
}

export function resetActiveAuthProvider(): void {
  activeProvider = new StubAuthProvider();
  notifyAuthChange();
}