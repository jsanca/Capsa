import { useSyncExternalStore } from "react";
import { getActiveAuthProvider } from "./providerHolder";
import { subscribeAuthChange } from "./authEvents";
import type { AuthProvider } from "./AuthProvider";

/**
 * React accessor for the active `AuthProvider`.
 *
 * The provider is a process-wide singleton. Tests swap providers via
 * `setActiveAuthProvider` followed by `notifyAuthChange()`; production code
 * typically never changes the active provider after startup.
 */
export function useActiveAuthProvider(): AuthProvider {
  return useSyncExternalStore(
    subscribeAuthChange,
    () => getActiveAuthProvider(),
    () => getActiveAuthProvider(),
  );
}