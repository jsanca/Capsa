import { useCallback, useEffect, useState } from "react";
import { useActiveAuthProvider } from "../auth/useActiveAuthProvider";
import {
  claimBootstrap,
  getBootstrapStatus,
  type BootstrapStatus,
} from "../api/bootstrap";
import type { CapsaApiError } from "../api/errors";

export type GateState =
  | { kind: "checking" }
  | { kind: "error"; reason: "network" | "server"; message: string }
  | { kind: "required" }
  | { kind: "bootstrap-closed" }
  | { kind: "ready" };

/**
 * Drives the bootstrap gate.
 *
 * Exposed as a React hook so tests can render the gate directly with the
 * active MSW handlers installed. Components should consume the gate via the
 * `AdminEntryGate` component, not this hook.
 */
export function useBootstrapGate(): {
  state: GateState;
  refetch: () => Promise<void>;
  submitToken: (token: string) => Promise<SubmitResult>;
} {
  const [state, setState] = useState<GateState>({ kind: "checking" });
  // Subscribe to provider state so the gate re-runs after sign-in.
  const provider = useActiveAuthProvider();

  const refetch = useCallback(async (): Promise<void> => {
    setState({ kind: "checking" });
    try {
      const status = await getBootstrapStatus();
      setState(statusFromBootstrap(status));
    } catch (cause) {
      setState(stateFromFetchError(cause as CapsaApiError));
    }
  }, []);

  useEffect(() => {
    void refetch();
  }, [refetch, provider]);

  const submitToken = useCallback(
    async (token: string): Promise<SubmitResult> => {
      try {
        const status = await claimBootstrap({ token });
        // Successful 201 claim: the caller is now the confirmed ADMIN.
        // This is the ONLY path that grants ready state.
        setState({ kind: "ready" });
        return { kind: "success", status };
      } catch (cause) {
        const error = cause as CapsaApiError;
        // 409 means bootstrap was already claimed by someone else — the
        // caller is NOT necessarily ADMIN. Show bootstrap-closed, not ready.
        if (error.httpStatus === 409) {
          setState({ kind: "bootstrap-closed" });
          return { kind: "already-claimed" };
        }
        return { kind: "error", error };
      }
    },
    [],
  );

  return { state, refetch, submitToken };
}

export type SubmitResult =
  | { kind: "success"; status: BootstrapStatus }
  | { kind: "already-claimed" }
  | { kind: "error"; error: CapsaApiError };

function statusFromBootstrap(status: BootstrapStatus): GateState {
  return status.required ? { kind: "required" } : { kind: "bootstrap-closed" };
}

function stateFromFetchError(error: CapsaApiError): GateState {
  if (error.code === "NETWORK_ERROR") {
    return { kind: "error", reason: "network", message: error.message };
  }
  return { kind: "error", reason: "server", message: error.message };
}