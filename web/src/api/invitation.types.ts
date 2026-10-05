import type { CapsaRole } from "../domain/roles";
import type { InvitationId } from "./invitations.types";

export interface CreateInvitationRequest {
  role: CapsaRole;
  expiresAt?: string;
}

export interface CreateInvitationResponse {
  invitationId: InvitationId;
  role: CapsaRole;
  expiresAt: string | null;
  token: string;
  acceptUrl: string;
}