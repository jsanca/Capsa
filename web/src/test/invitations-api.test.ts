import { beforeEach, describe, expect, it } from "vitest";
import { createInvitation } from "../api/invitations";
import { failingHandlers, invitationsCalls } from "./handlers";

const sampleResponse = {
  invitationId: { value: "inv-1" },
  role: "USER" as const,
  expiresAt: null,
  token: "tok-1",
  acceptUrl: "http://localhost:8080/invitations/accept?token=tok-1",
};

describe("createInvitation API client", () => {
  beforeEach(() => {
    invitationsCalls.setNextResponse(sampleResponse);
  });

  it("returns a parsed response on 201", async () => {
    const response = await createInvitation({ role: "USER" });
    expect(response).toEqual(sampleResponse);
  });

  it("throws a typed CapsaApiError on 422", async () => {
    failingHandlers.validation();
    await expect(createInvitation({ role: "USER" })).rejects.toMatchObject({
      code: "CAPSA_VALIDATION_ERROR",
      message: "role must be USER or ADMIN",
      httpStatus: 422,
    });
  });

  it("throws a typed CapsaApiError on 403", async () => {
    failingHandlers.forbidden();
    await expect(createInvitation({ role: "USER" })).rejects.toMatchObject({
      code: "CAPSA_FORBIDDEN",
      httpStatus: 403,
    });
  });

  it("throws NETWORK_ERROR when the request cannot reach the server", async () => {
    failingHandlers.networkError();
    await expect(createInvitation({ role: "USER" })).rejects.toMatchObject({
      code: "NETWORK_ERROR",
      httpStatus: 0,
    });
  });
});