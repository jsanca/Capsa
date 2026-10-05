/**
 * Capsa role taxonomy — server-owned.
 *
 * The authoritative definition lives server-side (`com.capsa.users.api.Role`).
 * The frontend imports these constants only to label the user-facing dropdowns;
 * no authorization decision is ever made locally from these strings.
 *
 * See `docs/engineering/agents/reports/CAPSA-ARCH-REVIEW-003.md` §6 (Auth /
 * Invitation Interaction) and H-2 (No role / authorization model exists).
 */
export const CAPSA_ROLES = ["USER", "ADMIN"] as const;
export type CapsaRole = (typeof CAPSA_ROLES)[number];

export function isCapsaRole(value: string): value is CapsaRole {
  return (CAPSA_ROLES as ReadonlyArray<string>).includes(value);
}