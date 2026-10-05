import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import type { CreateInvitationResponse } from "../api/invitation.types";
import type { CapsaErrorBody } from "../api/errors";

const invitationsUrl = "http://localhost:8080/capsa/api/invitations";
const bootstrapStatusUrl = "http://localhost:8080/capsa/api/bootstrap/status";
const bootstrapClaimUrl = "http://localhost:8080/capsa/api/bootstrap";

interface BootstrapStatusOverride {
  status: number;
  body: { required: boolean; claimedAt: string | null } | CapsaErrorBody;
}

let bootstrapOverride: BootstrapStatusOverride = {
  status: 200,
  body: { required: false, claimedAt: null },
};

let bootstrapClaimOverride: BootstrapStatusOverride = {
  status: 201,
  body: { required: false, claimedAt: "2026-10-04T12:00:00.000Z" },
};

export const bootstrapCalls = {
  reset(): void {
    bootstrapOverride = {
      status: 200,
      body: { required: false, claimedAt: null },
    };
    bootstrapClaimOverride = {
      status: 201,
      body: { required: false, claimedAt: "2026-10-04T12:00:00.000Z" },
    };
  },
  setStatusResponse(override: BootstrapStatusOverride): void {
    bootstrapOverride = override;
  },
  setClaimResponse(override: BootstrapStatusOverride): void {
    bootstrapClaimOverride = override;
  },
  getStatusCalls(): { count: number; lastAuthHeader: string | null } {
    return { count: statusCalls.count, lastAuthHeader: statusCalls.lastAuthHeader };
  },
  getClaimCalls(): {
    count: number;
    lastAuthHeader: string | null;
    lastBody: unknown;
  } {
    return {
      count: claimCalls.count,
      lastAuthHeader: claimCalls.lastAuthHeader,
      lastBody: claimCalls.lastBody,
    };
  },
};

const statusCalls = { count: 0, lastAuthHeader: null as string | null };
const claimCalls = { count: 0, lastAuthHeader: null as string | null, lastBody: null as unknown };

let createdResponse: CreateInvitationResponse = {
  invitationId: { value: "11111111-1111-1111-1111-111111111111" },
  role: "USER",
  expiresAt: "2026-12-31T23:59:59Z",
  token: "test-token-value",
  acceptUrl: "http://localhost:8080/invitations/accept?token=test-token-value",
};

let lastRequestBody: unknown = null;

export const invitationsCalls = {
  reset(): void {
    createdResponse = {
      invitationId: { value: "11111111-1111-1111-1111-111111111111" },
      role: "USER",
      expiresAt: "2026-12-31T23:59:59Z",
      token: "test-token-value",
      acceptUrl: "http://localhost:8080/invitations/accept?token=test-token-value",
    };
    lastRequestBody = null;
  },
  setNextResponse(response: CreateInvitationResponse): void {
    createdResponse = response;
  },
  getLastRequestBody(): unknown {
    return lastRequestBody;
  },
};

export const handlers = [
  http.get(bootstrapStatusUrl, ({ request }) => {
    statusCalls.count += 1;
    statusCalls.lastAuthHeader = request.headers.get("authorization");
    return HttpResponse.json(bootstrapOverride.body, { status: bootstrapOverride.status });
  }),
  http.post(bootstrapClaimUrl, async ({ request }) => {
    claimCalls.count += 1;
    claimCalls.lastAuthHeader = request.headers.get("authorization");
    claimCalls.lastBody = await request.json();
    return HttpResponse.json(bootstrapClaimOverride.body, { status: bootstrapClaimOverride.status });
  }),
  http.post(invitationsUrl, async ({ request }) => {
    lastRequestBody = await request.json();
    return HttpResponse.json(createdResponse, { status: 201 });
  }),
];

export const failingHandlers = {
  validation(): void {
    server.use(
      http.post(invitationsUrl, () => {
        const body: CapsaErrorBody = {
          code: "CAPSA_VALIDATION_ERROR",
          message: "role must be USER or ADMIN",
        };
        return HttpResponse.json(body, { status: 422 });
      }),
    );
  },
  forbidden(): void {
    server.use(
      http.post(invitationsUrl, () => {
        const body: CapsaErrorBody = { code: "CAPSA_FORBIDDEN", message: "Not allowed" };
        return HttpResponse.json(body, { status: 403 });
      }),
    );
  },
  networkError(): void {
    server.use(
      http.post(invitationsUrl, () => {
        return HttpResponse.error();
      }),
    );
  },
  unexpectedStatus(): void {
    server.use(
      http.post(invitationsUrl, () => {
        return HttpResponse.text("boom", { status: 500 });
      }),
    );
  },
};

export const server = setupServer(...handlers);

export function setLastRequestBody(body: unknown): void {
  lastRequestBody = body;
}