/**
 * Wire shape types for the invitation resource.
 *
 * The role taxonomy lives in `domain/roles.ts` (server-owned). This module
 * only carries resource-specific shapes (`InvitationId`, request, response).
 */
export interface InvitationId {
  value: string;
}