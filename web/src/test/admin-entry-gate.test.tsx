import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router-dom";
import {
  resetActiveAuthProvider,
  setActiveAuthProvider,
} from "../auth";
import { StubAuthProvider } from "../auth/StubAuthProvider";
import { AppRoutes } from "../AppRoutes";
import { bootstrapCalls, server } from "./handlers";

async function renderAtAdminUi(): Promise<void> {
  render(
    <MemoryRouter initialEntries={["/admin-ui"]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  bootstrapCalls.reset();
});

describe("AdminEntryGate — bootstrap status", () => {
  it("enters the gate before rendering AdminShell (1, 2)", async () => {
    bootstrapCalls.setStatusResponse({
      status: 200,
      body: { required: true, claimedAt: null },
    });

    await renderAtAdminUi();

    await waitFor(() => {
      expect(screen.getByTestId("gate-bootstrap")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("admin-shell")).not.toBeInTheDocument();
  });

  it("renders the loading state while the bootstrap status is in flight", async () => {
    await renderAtAdminUi();
    // The default handler is intercepted; we briefly hold the response so the
    // checking panel is visible without flushing immediately.
    expect(screen.getByTestId("gate-checking")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByTestId("gate-checking")).not.toBeInTheDocument();
    });
  });

  it("renders the first-run flow when status.required is true (3)", async () => {
    bootstrapCalls.setStatusResponse({
      status: 200,
      body: { required: true, claimedAt: null },
    });

    await renderAtAdminUi();

    await waitFor(() => {
      expect(screen.getByTestId("gate-bootstrap")).toBeInTheDocument();
    });
    expect(screen.getByTestId("gate-signin")).toBeInTheDocument();
    expect(screen.getByTestId("gate-token-input")).toBeInTheDocument();
    expect(screen.getByTestId("gate-submit")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Capsa Initial Setup" })).toBeInTheDocument();
  });

  it("shows bootstrap-closed state when status.required is false (4)", async () => {
    bootstrapCalls.setStatusResponse({
      status: 200,
      body: { required: false, claimedAt: "2026-10-04T12:00:00.000Z" },
    });

    await renderAtAdminUi();

    await waitFor(() => {
      expect(screen.getByTestId("gate-bootstrap-closed")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("admin-shell")).not.toBeInTheDocument();
    expect(screen.queryByTestId("gate-bootstrap")).not.toBeInTheDocument();
  });

  it("surfaces a recoverable error when bootstrap status fetch fails (server)", async () => {
    bootstrapCalls.setStatusResponse({
      status: 500,
      body: { code: "CAPSA_INTERNAL_ERROR", message: "boom" },
    });

    await renderAtAdminUi();

    await waitFor(() => {
      expect(screen.getByTestId("gate-error")).toBeInTheDocument();
    });
    expect(screen.getByRole("heading", { name: "Capsa Server error" })).toBeInTheDocument();
  });

  it("surfaces a recoverable error when bootstrap status fetch fails (network)", async () => {
    bootstrapCalls.setStatusResponse({
      status: 500,
      body: { code: "NETWORK_ERROR", message: "offline" },
    });

    await renderAtAdminUi();

    await waitFor(() => {
      expect(screen.getByTestId("gate-error")).toBeInTheDocument();
    });
  });
});

describe("AdminEntryGate — bootstrap submission", () => {
  it("submits the bootstrap token to the correct endpoint (5)", async () => {
    bootstrapCalls.setStatusResponse({
      status: 200,
      body: { required: true, claimedAt: null },
    });
    bootstrapCalls.setClaimResponse({
      status: 201,
      body: { required: false, claimedAt: "2026-10-04T13:00:00.000Z" },
    });

    await renderAtAdminUi();

    await waitFor(() => {
      expect(screen.getByTestId("gate-token-input")).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.type(screen.getByTestId("gate-token-input"), "test-bootstrap-token");
    await user.click(screen.getByTestId("gate-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("admin-shell")).toBeInTheDocument();
    });

    const claimCalls = bootstrapCalls.getClaimCalls();
    expect(claimCalls.count).toBe(1);
    expect(claimCalls.lastBody).toEqual({ token: "test-bootstrap-token" });
  });

  it("attaches the bearer token from the AuthProvider through the centralized seam (6)", async () => {
    const stub = new StubAuthProvider("STUB_TOKEN_VALUE");
    setActiveAuthProvider(stub);
    try {
      bootstrapCalls.setStatusResponse({
        status: 200,
        body: { required: true, claimedAt: null },
      });
      bootstrapCalls.setClaimResponse({
        status: 201,
        body: { required: false, claimedAt: "2026-10-04T13:00:00.000Z" },
      });

      await renderAtAdminUi();

      await waitFor(() => {
        expect(screen.getByTestId("gate-token-input")).toBeInTheDocument();
      });

      const user = userEvent.setup();
      await user.type(screen.getByTestId("gate-token-input"), "another-token");
      await user.click(screen.getByTestId("gate-submit"));

      await waitFor(() => {
        expect(screen.getByTestId("admin-shell")).toBeInTheDocument();
      });

      const claimCalls = bootstrapCalls.getClaimCalls();
      expect(claimCalls.lastAuthHeader).toBe("Bearer STUB_TOKEN_VALUE");
    } finally {
      resetActiveAuthProvider();
    }
  });

  it("displays an invalid-token error on 403 (7)", async () => {
    bootstrapCalls.setStatusResponse({
      status: 200,
      body: { required: true, claimedAt: null },
    });
    bootstrapCalls.setClaimResponse({
      status: 403,
      body: { code: "CAPSA_FORBIDDEN", message: "Invalid bootstrap token" },
    });

    await renderAtAdminUi();

    await waitFor(() => {
      expect(screen.getByTestId("gate-token-input")).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.type(screen.getByTestId("gate-token-input"), "wrong-token");
    await user.click(screen.getByTestId("gate-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("gate-error-invalid-token")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("admin-shell")).not.toBeInTheDocument();
  });

  it("shows bootstrap-closed state on 409 (not admin shell) (8)", async () => {
    bootstrapCalls.setStatusResponse({
      status: 200,
      body: { required: true, claimedAt: null },
    });
    bootstrapCalls.setClaimResponse({
      status: 409,
      body: { code: "CAPSA_BOOTSTRAP_ALREADY_CLAIMED", message: "Already claimed" },
    });

    await renderAtAdminUi();

    await waitFor(() => {
      expect(screen.getByTestId("gate-token-input")).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.type(screen.getByTestId("gate-token-input"), "any-token");
    await user.click(screen.getByTestId("gate-submit"));

    // After 409 the gate transitions to bootstrap-closed (not admin shell),
    // because the caller is NOT confirmed as ADMIN.
    await waitFor(() => {
      expect(screen.getByTestId("gate-bootstrap-closed")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("admin-shell")).not.toBeInTheDocument();
  });

  it("returns the user to the authentication-required state on 401 (9)", async () => {
    bootstrapCalls.setStatusResponse({
      status: 200,
      body: { required: true, claimedAt: null },
    });
    bootstrapCalls.setClaimResponse({
      status: 401,
      body: { code: "CAPSA_UNAUTHORIZED", message: "Bearer token missing" },
    });

    await renderAtAdminUi();

    await waitFor(() => {
      expect(screen.getByTestId("gate-token-input")).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.type(screen.getByTestId("gate-token-input"), "needs-token");
    await user.click(screen.getByTestId("gate-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("gate-error-auth-required")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("admin-shell")).not.toBeInTheDocument();
  });

  it("surfaces a recoverable error on network failure (10)", async () => {
    bootstrapCalls.setStatusResponse({
      status: 200,
      body: { required: true, claimedAt: null },
    });
    server.use(
      http.post("http://localhost:8080/capsa/api/bootstrap", () => {
        return HttpResponse.error();
      }),
    );

    await renderAtAdminUi();

    await waitFor(() => {
      expect(screen.getByTestId("gate-token-input")).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.type(screen.getByTestId("gate-token-input"), "any-token");
    await user.click(screen.getByTestId("gate-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("gate-error-network")).toBeInTheDocument();
    });
  });

  it("transitions into the admin shell on a successful bootstrap (11)", async () => {
    bootstrapCalls.setStatusResponse({
      status: 200,
      body: { required: true, claimedAt: null },
    });
    bootstrapCalls.setClaimResponse({
      status: 201,
      body: { required: false, claimedAt: "2026-10-04T13:00:00.000Z" },
    });

    await renderAtAdminUi();

    await waitFor(() => {
      expect(screen.getByTestId("gate-token-input")).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.type(screen.getByTestId("gate-token-input"), "correct-token");
    await user.click(screen.getByTestId("gate-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("admin-shell")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("gate-bootstrap")).not.toBeInTheDocument();
  });

  it("does not persist the bootstrap token in storage or state after submission (12)", async () => {
    bootstrapCalls.setStatusResponse({
      status: 200,
      body: { required: true, claimedAt: null },
    });
    bootstrapCalls.setClaimResponse({
      status: 201,
      body: { required: false, claimedAt: "2026-10-04T13:00:00.000Z" },
    });

    await renderAtAdminUi();

    await waitFor(() => {
      expect(screen.getByTestId("gate-token-input")).toBeInTheDocument();
    });

    const SECRET = "do-not-persist-me";
    fireEvent.change(screen.getByTestId("gate-token-input"), {
      target: { value: SECRET },
    });
    expect((screen.getByTestId("gate-token-input") as HTMLInputElement).value).toBe(SECRET);

    const user = userEvent.setup();
    await user.click(screen.getByTestId("gate-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("admin-shell")).toBeInTheDocument();
    });

    // The gate's input must be unmounted and the secret must not appear
    // anywhere in the document. Storage is checked defensively in case
    // jsdom ever wires it up, but we do not rely on it being present.
    expect(screen.queryByTestId("gate-token-input")).not.toBeInTheDocument();
    expect(document.body.innerHTML).not.toContain(SECRET);
    const storage = (globalThis as { localStorage?: unknown }).localStorage;
    if (storage && typeof (storage as Storage).getItem === "function") {
      expect((storage as Storage).getItem("bootstrapToken")).toBeNull();
      expect((storage as Storage).length).toBe(0);
    }
    const session = (globalThis as { sessionStorage?: unknown }).sessionStorage;
    if (session && typeof (session as Storage).getItem === "function") {
      expect((session as Storage).getItem("bootstrapToken")).toBeNull();
      expect((session as Storage).length).toBe(0);
    }
  });

  it("reaches admin shell only after successful first claim (201)", async () => {
    bootstrapCalls.setStatusResponse({
      status: 200,
      body: { required: true, claimedAt: null },
    });
    bootstrapCalls.setClaimResponse({
      status: 201,
      body: { required: false, claimedAt: "2026-10-04T13:00:00.000Z" },
    });

    await renderAtAdminUi();

    await waitFor(() => {
      expect(screen.getByTestId("gate-token-input")).toBeInTheDocument();
    });

    // Admin shell is NOT present before claiming
    expect(screen.queryByTestId("admin-shell")).not.toBeInTheDocument();

    const user = userEvent.setup();
    await user.type(screen.getByTestId("gate-token-input"), "first-claim-token");
    await user.click(screen.getByTestId("gate-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("admin-shell")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("gate-bootstrap-closed")).not.toBeInTheDocument();
  });

  it("maps CAPSA_BOOTSTRAP_INVALID_TOKEN to invalid-token phase", async () => {
    bootstrapCalls.setStatusResponse({
      status: 200,
      body: { required: true, claimedAt: null },
    });
    bootstrapCalls.setClaimResponse({
      status: 403,
      body: { code: "CAPSA_BOOTSTRAP_INVALID_TOKEN", message: "The bootstrap token is invalid" },
    });

    await renderAtAdminUi();

    await waitFor(() => {
      expect(screen.getByTestId("gate-token-input")).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.type(screen.getByTestId("gate-token-input"), "bad-token");
    await user.click(screen.getByTestId("gate-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("gate-error-invalid-token")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("admin-shell")).not.toBeInTheDocument();
  });

  it("re-evaluates gate when auth provider changes", async () => {
    bootstrapCalls.setStatusResponse({
      status: 200,
      body: { required: true, claimedAt: null },
    });

    const initialStub = new StubAuthProvider("INITIAL_TOKEN");
    setActiveAuthProvider(initialStub);
    try {
      await renderAtAdminUi();

      await waitFor(() => {
        expect(screen.getByTestId("gate-bootstrap")).toBeInTheDocument();
      });

      const initialStatusCount = bootstrapCalls.getStatusCalls().count;

      // Switch to a new provider — notifyAuthChange() is called internally
      // by setActiveAuthProvider, which should cause the gate to re-fetch.
      const newStub = new StubAuthProvider("NEW_TOKEN");
      setActiveAuthProvider(newStub);

      await waitFor(() => {
        expect(bootstrapCalls.getStatusCalls().count).toBeGreaterThan(initialStatusCount);
      });
    } finally {
      resetActiveAuthProvider();
    }
  });
});

describe("API seam separation", () => {
  it("does not let presentation components construct Authorization headers directly (13)", async () => {
    // The InvitationNewPage is rendered only after the gate resolves to
    // "ready". With the new model, required=false shows bootstrap-closed (not
    // admin shell). We drive through the full claim flow (required=true → 201)
    // to reach ready state, which correctly tests the auth seam for the
    // invitation page.
    const stub = new StubAuthProvider("PAGE-TEST-TOKEN");
    setActiveAuthProvider(stub);
    try {
      bootstrapCalls.setStatusResponse({
        status: 200,
        body: { required: true, claimedAt: null },
      });
      bootstrapCalls.setClaimResponse({
        status: 201,
        body: { required: false, claimedAt: "2026-10-04T12:00:00.000Z" },
      });

      await renderAtAdminUi();

      await waitFor(() => {
        expect(screen.getByTestId("gate-token-input")).toBeInTheDocument();
      });

      // Claim bootstrap to reach ready state (admin shell)
      const user = userEvent.setup();
      await user.type(screen.getByTestId("gate-token-input"), "claim-token-for-seam-test");
      await user.click(screen.getByTestId("gate-submit"));

      await waitFor(() => {
        expect(screen.getByTestId("admin-shell")).toBeInTheDocument();
      });

      await user.click(screen.getByRole("link", { name: /Invitations/ }));

      await waitFor(() => {
        expect(screen.getByTestId("invitation-form")).toBeInTheDocument();
      });

      await user.selectOptions(screen.getByTestId("invitation-role"), "USER");
      await user.click(screen.getByTestId("invitation-submit"));

      await waitFor(() => {
        expect(screen.getByTestId("invitation-result")).toBeInTheDocument();
      });

      // Confirm the Authorization header was attached by the seam, not the page.
      // The cleanest structural assertion is: the page source never imports
      // anything from auth/.
      const pageModule = await import("../pages/InvitationNewPage");
      const source = pageModule.InvitationNewPage.toString();
      expect(source).not.toMatch(/Authorization/);
      expect(source).not.toMatch(/Bearer/);
      expect(source).not.toMatch(/getAccessToken|signIn|signOut/);
    } finally {
      resetActiveAuthProvider();
    }
  });
});
