import { apiNamespace } from "../config";
import { apiCall } from "./apiCall";
import type {
  CreateInvitationRequest,
  CreateInvitationResponse,
} from "./invitation.types";

const invitationsPath = `${apiNamespace}/invitations`;

/**
 * Posts an invitation request to the Capsa Server.
 *
 * Auth header (when available) is injected by `apiCall`. The server endpoint
 * does not yet exist — the proposed contract targeted here is documented in
 * `docs/engineering/agents/reports/CAPSA-WEB-001-report.md`. Once the server
 * ships the endpoint, no caller-side change is required.
 */
export async function createInvitation(
  request: CreateInvitationRequest,
): Promise<CreateInvitationResponse> {
  return apiCall<CreateInvitationResponse>(invitationsPath, {
    method: "POST",
    body: JSON.stringify(request),
  });
}