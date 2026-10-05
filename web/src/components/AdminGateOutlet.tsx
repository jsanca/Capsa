import { useActiveAuthProvider } from "../auth/useActiveAuthProvider";
import { AdminEntryGate } from "../components/AdminEntryGate";
import { AdminShell } from "../components/AdminShell";
import { useBootstrapGate } from "../auth/useBootstrapGate";
import type { SubmitResult } from "../auth/useBootstrapGate";

/**
 * Connects the gate to the live bootstrap state.
 *
 * This component is rendered in place of `AdminShell` for the `/admin-ui`
 * subtree. Once bootstrap is no longer required it renders `AdminShell`,
 * which in turn exposes its own `<Outlet />` for the nested routes.
 */
export function AdminGateOutlet(): JSX.Element {
  const { state, refetch, submitToken } = useBootstrapGate();
  // Subscribe to provider changes so a real OIDC sign-in re-checks status.
  useActiveAuthProvider();

  const handleSubmit = async (token: string): Promise<SubmitResult> => {
    return submitToken(token);
  };

  return (
    <AdminEntryGate state={state} onRetry={() => void refetch()} onSubmitToken={handleSubmit}>
      <AdminShell />
    </AdminEntryGate>
  );
}