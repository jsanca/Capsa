import { useId, useState } from "react";
import { useActiveAuthProvider } from "../auth/useActiveAuthProvider";
import type { CapsaApiError } from "../api/errors";
import type { GateState, SubmitResult } from "../auth/useBootstrapGate";

interface AdminEntryGateProps {
  state: GateState;
  onRetry: () => void;
  onSubmitToken: (token: string) => Promise<SubmitResult>;
  children: React.ReactNode;
}

/**
 * Gate component placed before `AdminShell`.
 *
 * The component intentionally knows nothing about routing — it renders one
 * of three states (`checking`, `error`, `required`) and yields to its
 * children once the server confirms bootstrap is no longer required.
 *
 * The actual status polling lives in `useBootstrapGate`; this file is purely
 * presentation so it can be exercised in tests with a hand-rolled state.
 */
export function AdminEntryGate({
  state,
  onRetry,
  onSubmitToken,
  children,
}: AdminEntryGateProps): JSX.Element {
  if (state.kind === "checking") {
    return (
      <div className="gate gate--checking" data-testid="gate-checking" role="status">
        <p>Checking Capsa initialization state…</p>
      </div>
    );
  }

  if (state.kind === "error") {
    const heading =
      state.reason === "network"
        ? "Could not reach the Capsa Server"
        : "Capsa Server error";
    return (
      <div className="gate gate--error" data-testid="gate-error" role="alert">
        <h1 className="gate__title">{heading}</h1>
        <p className="gate__message">{state.message}</p>
        <button
          type="button"
          className="gate__retry"
          onClick={onRetry}
          data-testid="gate-retry"
        >
          Retry
        </button>
      </div>
    );
  }

  if (state.kind === "required") {
    return <BootstrapPanel onSubmitToken={onSubmitToken} onRetry={onRetry} />;
  }

  if (state.kind === "bootstrap-closed") {
    return <BootstrapClosedPanel />;
  }

  return <>{children}</>;
}

interface BootstrapPanelProps {
  onSubmitToken: (token: string) => Promise<SubmitResult>;
  onRetry: () => void;
}

type Phase =
  | "awaiting-auth"
  | "submitting"
  | "invalid-token"
  | "auth-required"
  | "server-error"
  | "network-error";

function BootstrapPanel({ onSubmitToken, onRetry }: BootstrapPanelProps): JSX.Element {
  const formId = useId();
  const auth = useActiveAuthProvider();
  const [phase, setPhase] = useState<Phase>("awaiting-auth");
  const [token, setToken] = useState<string>("");

  async function handleSignIn(): Promise<void> {
    try {
      await auth.signIn();
      // Move past awaiting-auth on success. If we previously surfaced an
      // error, clear it so the user can re-enter the token.
      setPhase((previous) =>
        previous === "awaiting-auth" || previous === "auth-required"
          ? "awaiting-auth"
          : previous,
      );
    } catch {
      setPhase("auth-required");
    }
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const trimmed = token.trim();
    if (trimmed.length === 0) {
      return;
    }
    setPhase("submitting");
    const result = await onSubmitToken(trimmed);
    // Always clear the token from local state after a request resolves; the
    // gateway hook will transition us into `ready` on success.
    setToken("");
    if (result.kind === "success" || result.kind === "already-claimed") {
      setPhase("awaiting-auth");
      return;
    }
    setPhase(phaseForError(result.error));
  }

  function handleTokenChange(value: string): void {
    setToken(value);
    // Editing the token after a server-side error clears that error so the
    // user can re-submit without seeing a stale message.
    if (
      phase === "invalid-token" ||
      phase === "auth-required" ||
      phase === "server-error" ||
      phase === "network-error"
    ) {
      setPhase("awaiting-auth");
    }
  }

  const submitting = phase === "submitting";

  return (
    <div className="gate gate--bootstrap" data-testid="gate-bootstrap">
      <header className="gate__header">
        <h1 className="gate__title">Capsa Initial Setup</h1>
        <p className="gate__lede">
          Capsa is initialized only by the first authenticated administrator who
          presents the configured setup secret. The Capsa Server is the authority
          for whether this step is required.
        </p>
      </header>

      <div className="gate__signin">
        <button
          type="button"
          className="gate__signin-button"
          onClick={() => void handleSignIn()}
          disabled={submitting}
          data-testid="gate-signin"
        >
          Sign in with Google
        </button>
        <p className="gate__signin-hint" data-testid="gate-signin-hint">
          {phase === "awaiting-auth"
            ? "Sign in to receive an access token, then enter the bootstrap token."
            : phase === "auth-required"
              ? "Authentication was not completed. Try signing in again."
              : "Signed in (development stub). Enter the bootstrap token."}
        </p>
      </div>

      <form
        className="gate__form"
        onSubmit={(event) => void handleSubmit(event)}
        aria-labelledby={`${formId}-title`}
        noValidate
        data-testid="gate-form"
      >
        <h2 id={`${formId}-title`} className="visually-hidden">
          Bootstrap token
        </h2>

        <div className="gate__field">
          <label htmlFor={`${formId}-token`} className="gate__label">
            Bootstrap token <span aria-hidden="true">*</span>
            <span className="visually-hidden">(required)</span>
          </label>
          <input
            id={`${formId}-token`}
            className="gate__control"
            name="bootstrapToken"
            type="password"
            autoComplete="off"
            spellCheck={false}
            value={token}
            onChange={(event) => handleTokenChange(event.target.value)}
            disabled={submitting}
            required
            aria-invalid={phase === "invalid-token"}
            aria-describedby={`${formId}-token-hint`}
            data-testid="gate-token-input"
          />
          <p id={`${formId}-token-hint`} className="gate__hint">
            The token is configured server-side. It is sent over the wire once
            and is not stored in this browser.
          </p>
        </div>

        <div className="gate__actions">
          <button
            type="submit"
            className="gate__submit"
            disabled={submitting || token.trim().length === 0}
            data-testid="gate-submit"
          >
            {submitting ? "Initializing…" : "Initialize Capsa"}
          </button>
        </div>

        <ErrorBanner phase={phase} />
      </form>

      <button
        type="button"
        className="gate__retry-link"
        onClick={onRetry}
        data-testid="gate-recheck"
      >
        Re-check bootstrap status
      </button>
    </div>
  );
}

function ErrorBanner({ phase }: { phase: Phase }): JSX.Element | null {
  if (phase === "invalid-token") {
    return (
      <div role="alert" className="gate__alert gate__alert--error" data-testid="gate-error-invalid-token">
        <strong>Invalid token.</strong> The Capsa Server rejected the bootstrap token.
      </div>
    );
  }
  if (phase === "auth-required") {
    return (
      <div role="alert" className="gate__alert gate__alert--error" data-testid="gate-error-auth-required">
        <strong>Authentication required.</strong> Sign in again before submitting the bootstrap token.
      </div>
    );
  }
  if (phase === "server-error") {
    return (
      <div role="alert" className="gate__alert gate__alert--error" data-testid="gate-error-server">
        <strong>Server error.</strong> The Capsa Server could not process the bootstrap claim. Try again.
      </div>
    );
  }
  if (phase === "network-error") {
    return (
      <div role="alert" className="gate__alert gate__alert--error" data-testid="gate-error-network">
        <strong>Network error.</strong> The Capsa Server could not be reached.
      </div>
    );
  }
  return null;
}

function phaseForError(error: CapsaApiError): Phase {
  if (error.code === "NETWORK_ERROR") return "network-error";
  if (error.code === "CAPSA_UNAUTHORIZED") return "auth-required";
  if (error.code === "CAPSA_FORBIDDEN" || error.code === "CAPSA_BOOTSTRAP_INVALID_TOKEN") return "invalid-token";
  return "server-error";
}

function BootstrapClosedPanel(): JSX.Element {
  return (
    <div className="gate gate--closed" data-testid="gate-bootstrap-closed" role="status">
      <h1 className="gate__title">Capsa is already initialized.</h1>
      <p className="gate__message">
        Your account is authenticated but does not currently have confirmed administrative access.
      </p>
    </div>
  );
}