/**
 * Wire-level error types for the Capsa Server public REST API.
 *
 * Every API client (`apiCall.ts`, `bootstrap.ts`, `invitations.ts`) throws a
 * `CapsaApiError` on non-2xx responses and on transport failure. Presentation
 * code switches on `error.code` and never inspects `error.httpStatus` to make
 * authorization choices.
 */
export interface CapsaErrorBody {
  code: string;
  message: string;
}

export type CapsaErrorCode =
  | "CAPSA_VALIDATION_ERROR"
  | "CAPSA_FORBIDDEN"
  | "CAPSA_UNAUTHORIZED"
  | "CAPSA_NOT_FOUND"
  | "CAPSA_BOOTSTRAP_ALREADY_CLAIMED"
  | "CAPSA_BOOTSTRAP_INVALID_TOKEN"
  | "CAPSA_INVITATION_CONFLICT"
  | "NETWORK_ERROR";

export interface CapsaApiError {
  code: CapsaErrorCode;
  message: string;
  httpStatus: number;
}

export const KNOWN_ERROR_CODES: ReadonlySet<string> = new Set<CapsaErrorCode>([
  "CAPSA_VALIDATION_ERROR",
  "CAPSA_FORBIDDEN",
  "CAPSA_UNAUTHORIZED",
  "CAPSA_NOT_FOUND",
  "CAPSA_BOOTSTRAP_ALREADY_CLAIMED",
  "CAPSA_BOOTSTRAP_INVALID_TOKEN",
  "CAPSA_INVITATION_CONFLICT",
]);

export function isCapsaErrorBody(value: unknown): value is CapsaErrorBody {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return typeof candidate.code === "string" && typeof candidate.message === "string";
}