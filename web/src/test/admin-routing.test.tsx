import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router-dom";
import { AppRoutes } from "../AppRoutes";
import { bootstrapCalls, failingHandlers, invitationsCalls, server } from "./handlers";

// Drive through the full bootstrap claim flow so tests reach the admin shell.
// Status returns required=true; a simulated token submission returns 201 → ready state.
async function renderAt(path: string): Promise<void> {
  bootstrapCalls.setStatusResponse({ status: 200, body: { required: true, claimedAt: null } });
  bootstrapCalls.setClaimResponse({ status: 201, body: { required: false, claimedAt: "2026-10-05T00:00:00.000Z" } });

  render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  );

  // Wait for the bootstrap form, submit the token, then wait for admin shell.
  await waitFor(() => {
    expect(screen.getByTestId("gate-token-input")).toBeInTheDocument();
  });
  const user = userEvent.setup();
  await user.type(screen.getByTestId("gate-token-input"), "test-token");
  await user.click(screen.getByTestId("gate-submit"));

  await waitFor(() => {
    expect(screen.getByTestId("admin-shell")).toBeInTheDocument();
  });
}

beforeEach(() => {
  bootstrapCalls.reset();
  invitationsCalls.reset();
});

describe("admin application routing", () => {
  it("renders the admin shell under /admin-ui", async () => {
    await renderAt("/admin-ui");
    expect(screen.getByTestId("admin-shell")).toBeInTheDocument();
    expect(screen.getByTestId("admin-home")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Administration" })).toBeInTheDocument();
  });

  it("redirects the application root to /admin-ui", async () => {
    await renderAt("/");
    expect(screen.getByTestId("admin-shell")).toBeInTheDocument();
  });

  it("falls back to /admin-ui for unknown paths", async () => {
    await renderAt("/does-not-exist");
    expect(screen.getByTestId("admin-shell")).toBeInTheDocument();
  });

  it("exposes an Invitations navigation entry", async () => {
    await renderAt("/admin-ui");
    const nav = screen.getByRole("navigation", { name: "Admin navigation" });
    expect(nav.querySelector('a[href="/admin-ui/invitations/new"]')).not.toBeNull();
  });

  it("renders the invitation screen at /admin-ui/invitations/new", async () => {
    await renderAt("/admin-ui/invitations/new");
    expect(screen.getByTestId("invitation-new-page")).toBeInTheDocument();
    expect(screen.getByTestId("invitation-form")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Create invitation" })).toBeInTheDocument();
  });
});

describe("InvitationNewPage", () => {
  it("renders the form with role and expires-at fields", async () => {
    await renderAt("/admin-ui/invitations/new");
    expect(screen.getByTestId("invitation-form")).toBeInTheDocument();
    expect(screen.getByTestId("invitation-role")).toBeInTheDocument();
    expect(screen.getByTestId("invitation-expires")).toBeInTheDocument();
    expect(screen.getByTestId("invitation-submit")).toBeEnabled();
  });

  it("requires a role to be selected before submission", async () => {
    const user = userEvent.setup();
    await renderAt("/admin-ui/invitations/new");

    await user.click(screen.getByTestId("invitation-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("invitation-role-error")).toHaveTextContent(
        "Choose a role for the invitation.",
      );
    });
  });

  it("submits a valid form, sends the expected request body, and shows confirmation", async () => {
    const user = userEvent.setup();
    invitationsCalls.setNextResponse({
      invitationId: { value: "inv-abc-123" },
      role: "ADMIN",
      expiresAt: "2027-01-01T12:00:00.000Z",
      token: "tok-abc-123",
      acceptUrl: "https://capsa.example.com/invitations/accept?token=tok-abc-123",
    });
    await renderAt("/admin-ui/invitations/new");

    await user.selectOptions(screen.getByTestId("invitation-role"), "ADMIN");
    fireEvent.change(screen.getByTestId("invitation-expires"), {
      target: { value: "2027-01-01T12:00" },
    });
    await user.click(screen.getByTestId("invitation-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("invitation-result")).toBeInTheDocument();
    });

    const body = invitationsCalls.getLastRequestBody() as {
      role?: string;
      expiresAt?: string;
    };
    expect(body.role).toBe("ADMIN");
    // The form converts the datetime-local input to ISO using local time,
    // so compute the expected ISO from the same local interpretation.
    expect(body.expiresAt).toBe(new Date("2027-01-01T12:00").toISOString());

    expect(screen.getByTestId("invitation-result-id")).toHaveTextContent("inv-abc-123");
    expect(screen.getByTestId("invitation-result-role")).toHaveTextContent("ADMIN");
    expect(screen.getByTestId("invitation-result-token")).toHaveTextContent("tok-abc-123");
    expect(screen.getByTestId("invitation-result-url")).toHaveTextContent(
      "https://capsa.example.com/invitations/accept?token=tok-abc-123",
    );
  });

  it("disables the submit button while the request is in flight", async () => {
    const user = userEvent.setup();
    const gate: { release: (() => void) | null } = { release: null };
    const stalled = new Promise<void>((resolve) => {
      gate.release = resolve;
    });
    server.use(
      http.post("http://localhost:8080/capsa/api/invitations", async () => {
        await stalled;
        return HttpResponse.json(
          {
            invitationId: { value: "x" },
            role: "USER",
            expiresAt: null,
            token: "t",
            acceptUrl: "http://localhost:8080/accept",
          },
          { status: 201 },
        );
      }),
    );

    await renderAt("/admin-ui/invitations/new");
    await user.selectOptions(screen.getByTestId("invitation-role"), "USER");
    await user.click(screen.getByTestId("invitation-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("invitation-submit")).toBeDisabled();
    });
    expect(screen.getByTestId("invitation-submit")).toHaveTextContent(/Creating/i);

    gate.release?.();
    await waitFor(() => {
      expect(screen.getByTestId("invitation-submit")).toBeEnabled();
    });
  });

  it("displays backend 422 validation errors and preserves the form state", async () => {
    const user = userEvent.setup();
    failingHandlers.validation();

    await renderAt("/admin-ui/invitations/new");
    await user.selectOptions(screen.getByTestId("invitation-role"), "ADMIN");
    fireEvent.change(screen.getByTestId("invitation-expires"), {
      target: { value: "2027-02-01T09:00" },
    });
    await user.click(screen.getByTestId("invitation-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("invitation-error")).toBeInTheDocument();
    });
    expect(screen.getByTestId("invitation-error")).toHaveTextContent(
      "role must be USER or ADMIN",
    );

    expect(screen.getByTestId("invitation-role")).toHaveValue("ADMIN");
    expect(screen.getByTestId("invitation-expires")).toHaveValue("2027-02-01T09:00");
    expect(screen.queryByTestId("invitation-result")).not.toBeInTheDocument();
  });

  it("displays a network error without destroying entered form data", async () => {
    const user = userEvent.setup();
    failingHandlers.networkError();

    await renderAt("/admin-ui/invitations/new");
    await user.selectOptions(screen.getByTestId("invitation-role"), "USER");
    fireEvent.change(screen.getByTestId("invitation-expires"), {
      target: { value: "2027-03-01T09:00" },
    });
    await user.click(screen.getByTestId("invitation-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("invitation-error")).toBeInTheDocument();
    });
    expect(screen.getByTestId("invitation-error")).toHaveTextContent(/Network error/i);

    expect(screen.getByTestId("invitation-role")).toHaveValue("USER");
    expect(screen.getByTestId("invitation-expires")).toHaveValue("2027-03-01T09:00");
    expect(screen.queryByTestId("invitation-result")).not.toBeInTheDocument();
  });

  it("displays an unexpected server failure and recovers", async () => {
    const user = userEvent.setup();
    failingHandlers.unexpectedStatus();

    await renderAt("/admin-ui/invitations/new");
    await user.selectOptions(screen.getByTestId("invitation-role"), "USER");
    await user.click(screen.getByTestId("invitation-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("invitation-error")).toBeInTheDocument();
    });
    expect(screen.getByTestId("invitation-error")).toHaveTextContent(/500/);

    expect(screen.getByTestId("invitation-role")).toHaveValue("USER");
    expect(screen.queryByTestId("invitation-result")).not.toBeInTheDocument();
  });
});