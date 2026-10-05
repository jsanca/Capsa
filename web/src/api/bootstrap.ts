import { apiNamespace } from "../config";
import { apiCall } from "./apiCall";

export interface BootstrapStatus {
  required: boolean;
  claimedAt: string | null;
}

export interface ClaimBootstrapRequest {
  token: string;
}

const bootstrapPath = `${apiNamespace}/bootstrap`;
const statusPath = `${bootstrapPath}/status`;

/**
 * Reads the authoritative server-side bootstrap state.
 *
 * The frontend never infers bootstrap state locally: it always asks the
 * server. Per `CAPSA-ARCH-REVIEW-003` §5, `required = true` means no ADMIN
 * exists yet and the first claim is required.
 *
 * This endpoint is publicly permitted on the server; the request is sent
 * without an `Authorization` header to keep wire shape unambiguous for a
 * unauthenticated probe.
 */
export async function getBootstrapStatus(): Promise<BootstrapStatus> {
  return apiCall<BootstrapStatus>(statusPath, { anonymous: true });
}

/**
 * Claims the bootstrap singleton on behalf of the authenticated caller.
 *
 * Identity, role, and email are **not** included in the request body; the
 * server derives them from the verified OIDC token attached by the API
 * seam (`Authorization: Bearer …`). The token value itself is the only
 * payload and is never persisted, logged, or reflected outside the request
 * lifecycle.
 */
export async function claimBootstrap(
  request: ClaimBootstrapRequest,
): Promise<BootstrapStatus> {
  return apiCall<BootstrapStatus>(bootstrapPath, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

// Re-exported for tests that want to assert against the wire path.
export const bootstrapStatusUrl = statusPath;