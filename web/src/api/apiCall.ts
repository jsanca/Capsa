import { getActiveAuthProvider } from "../auth/providerHolder";
import { capsaApiUrl } from "../config";
import {
  KNOWN_ERROR_CODES,
  isCapsaErrorBody,
  type CapsaApiError,
} from "./errors";

/**
 * Single outbound HTTP boundary for the Capsa Server.
 *
 * All API clients (`bootstrap.ts`, `invitations.ts`, and any future client)
 * must go through this function. It:
 *
 *   - reads the active `AuthProvider` and attaches `Authorization: Bearer …`
 *     when a token is available;
 *   - parses the standard `{ code, message }` error envelope into a typed
 *     `CapsaApiError`;
 *   - distinguishes transport failure (`httpStatus: 0`, `code: NETWORK_ERROR`)
 *     from server error responses.
 *
 * Presentation components never build endpoint paths or headers directly.
 */
export interface ApiCallInit extends Omit<RequestInit, "headers"> {
  headers?: HeadersInit;
  /** When true, omit `Authorization` even if a token is available. */
  anonymous?: boolean;
}

export async function apiCall<TResponse>(
  path: string,
  init: ApiCallInit = {},
): Promise<TResponse> {
  const headers = new Headers(init.headers);
  if (!headers.has("Content-Type") && init.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  if (init.anonymous !== true) {
    const token = await getActiveAuthProvider().getAccessToken();
    if (token !== null) {
      headers.set("Authorization", `Bearer ${token}`);
    }
  }

  const requestInit: RequestInit = {
    ...init,
    headers,
  };

  let response: Response;
  try {
    response = await fetch(`${capsaApiUrl}${path}`, requestInit);
  } catch (cause) {
    throw makeNetworkError(cause);
  }

  if (response.ok) {
    return (await response.json()) as TResponse;
  }

  throw await parseErrorResponse(response);
}

export async function parseErrorResponse(response: Response): Promise<CapsaApiError> {
  let body: unknown = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }

  if (isCapsaErrorBody(body)) {
    const code: CapsaApiError["code"] = KNOWN_ERROR_CODES.has(body.code)
      ? (body.code as CapsaApiError["code"])
      : mapStatusToFallbackCode(response.status);
    return { code, message: body.message, httpStatus: response.status };
  }

  return {
    code: mapStatusToFallbackCode(response.status),
    message: `Unexpected server response (HTTP ${response.status}).`,
    httpStatus: response.status,
  };
}

function mapStatusToFallbackCode(status: number): CapsaApiError["code"] {
  if (status === 401) return "CAPSA_UNAUTHORIZED";
  if (status === 403) return "CAPSA_FORBIDDEN";
  if (status === 404) return "CAPSA_NOT_FOUND";
  if (status === 422) return "CAPSA_VALIDATION_ERROR";
  return "CAPSA_VALIDATION_ERROR";
}

function makeNetworkError(cause: unknown): CapsaApiError {
  const reason = cause instanceof Error ? cause.message : String(cause);
  return {
    code: "NETWORK_ERROR",
    message: `Could not reach the Capsa Server: ${reason}`,
    httpStatus: 0,
  };
}