import { useId, useState } from "react";
import { createInvitation } from "../api/invitations";
import type { CapsaApiError, CapsaErrorCode } from "../api/errors";
import type { CreateInvitationResponse } from "../api/invitation.types";
import { CAPSA_ROLES, type CapsaRole } from "../domain/roles";

type FormState = {
  role: CapsaRole | "";
  expiresAt: string;
};

type FieldErrors = {
  role?: string;
  expiresAt?: string;
};

type SubmitStatus =
  | { kind: "idle" }
  | { kind: "submitting" }
  | { kind: "error"; error: CapsaApiError };

function describeError(code: CapsaErrorCode, message: string): string {
  switch (code) {
    case "CAPSA_VALIDATION_ERROR":
      return message;
    case "CAPSA_FORBIDDEN":
      return "You do not have permission to create invitations.";
    case "CAPSA_UNAUTHORIZED":
      return "Your session is no longer valid. Please sign in again.";
    case "CAPSA_INVITATION_CONFLICT":
      return message;
    case "NETWORK_ERROR":
      return message;
    default:
      return message;
  }
}

function validate(form: FormState): FieldErrors {
  const errors: FieldErrors = {};
  if (form.role === "") {
    errors.role = "Choose a role for the invitation.";
  }
  if (form.expiresAt !== "" && Number.isNaN(Date.parse(form.expiresAt))) {
    errors.expiresAt = "Expiration must be a valid date and time.";
  }
  return errors;
}

function toIsoLocal(value: string): string | undefined {
  if (value === "") return undefined;
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return undefined;
  return parsed.toISOString();
}

export function InvitationNewPage(): JSX.Element {
  const formId = useId();
  const [form, setForm] = useState<FormState>({ role: "", expiresAt: "" });
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [status, setStatus] = useState<SubmitStatus>({ kind: "idle" });
  const [created, setCreated] = useState<CreateInvitationResponse | null>(null);

  const submitting = status.kind === "submitting";
  const formError = status.kind === "error" ? status.error : null;

  function update<K extends keyof FormState>(key: K, value: FormState[K]): void {
    setForm((previous) => ({ ...previous, [key]: value }));
    setFieldErrors((previous) => ({ ...previous, [key]: undefined }));
    if (created !== null) setCreated(null);
    if (formError !== null) setStatus({ kind: "idle" });
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const errors = validate(form);
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setStatus({ kind: "submitting" });
    setFieldErrors({});
    setCreated(null);

    try {
      const response = await createInvitation({
        role: form.role as CapsaRole,
        expiresAt: toIsoLocal(form.expiresAt),
      });
      setCreated(response);
      setStatus({ kind: "idle" });
    } catch (cause) {
      const error = cause as CapsaApiError;
      setStatus({ kind: "error", error });
    }
  }

  return (
    <section className="admin-page" data-testid="invitation-new-page">
      <header className="admin-page__header">
        <h1 className="admin-page__title">Create invitation</h1>
        <p className="admin-page__lede">
          Issue a new invitation through the public Capsa Server API.
        </p>
      </header>

      <form
        noValidate
        className="invitation-form"
        aria-labelledby={`${formId}-title`}
        onSubmit={handleSubmit}
        data-testid="invitation-form"
      >
        <h2 id={`${formId}-title`} className="visually-hidden">
          Invitation form
        </h2>

        <div className="invitation-form__field">
          <label htmlFor={`${formId}-role`} className="invitation-form__label">
            Role <span aria-hidden="true">*</span>
            <span className="visually-hidden">(required)</span>
          </label>
          <select
            id={`${formId}-role`}
            className="invitation-form__control"
            name="role"
            value={form.role}
            onChange={(event) => update("role", event.target.value as FormState["role"])}
            required
            aria-invalid={fieldErrors.role !== undefined}
            aria-describedby={fieldErrors.role !== undefined ? `${formId}-role-error` : undefined}
            disabled={submitting}
            data-testid="invitation-role"
          >
            <option value="" disabled>
              Select a role…
            </option>
            {CAPSA_ROLES.map((role) => (
              <option key={role} value={role}>
                {role}
              </option>
            ))}
          </select>
          {fieldErrors.role !== undefined ? (
            <p
              id={`${formId}-role-error`}
              className="invitation-form__field-error"
              data-testid="invitation-role-error"
            >
              {fieldErrors.role}
            </p>
          ) : null}
        </div>

        <div className="invitation-form__field">
          <label htmlFor={`${formId}-expires`} className="invitation-form__label">
            Expires at
          </label>
          <input
            id={`${formId}-expires`}
            className="invitation-form__control"
            name="expiresAt"
            type="datetime-local"
            value={form.expiresAt}
            onChange={(event) => update("expiresAt", event.target.value)}
            aria-invalid={fieldErrors.expiresAt !== undefined}
            aria-describedby={
              fieldErrors.expiresAt !== undefined ? `${formId}-expires-error` : undefined
            }
            disabled={submitting}
            data-testid="invitation-expires"
          />
          {fieldErrors.expiresAt !== undefined ? (
            <p
              id={`${formId}-expires-error`}
              className="invitation-form__field-error"
              data-testid="invitation-expires-error"
            >
              {fieldErrors.expiresAt}
            </p>
          ) : null}
        </div>

        <div className="invitation-form__actions">
          <button
            type="submit"
            className="invitation-form__submit"
            disabled={submitting}
            data-testid="invitation-submit"
          >
            {submitting ? "Creating…" : "Create invitation"}
          </button>
        </div>

        {formError !== null ? (
          <div
            role="alert"
            className="invitation-form__alert invitation-form__alert--error"
            data-testid="invitation-error"
          >
            <strong>
              {formError.httpStatus === 0
                ? "Network error"
                : `Error ${formError.httpStatus}`}
              :
            </strong>{" "}
            {describeError(formError.code, formError.message)}
          </div>
        ) : null}
      </form>

      {created !== null ? (
        <section
          className="invitation-result"
          aria-live="polite"
          data-testid="invitation-result"
        >
          <h2 className="invitation-result__title">Invitation created</h2>
          <dl className="invitation-result__list">
            <div className="invitation-result__row">
              <dt>Invitation id</dt>
              <dd>
                <code data-testid="invitation-result-id">{created.invitationId.value}</code>
              </dd>
            </div>
            <div className="invitation-result__row">
              <dt>Role</dt>
              <dd data-testid="invitation-result-role">{created.role}</dd>
            </div>
            {created.expiresAt !== null ? (
              <div className="invitation-result__row">
                <dt>Expires at</dt>
                <dd data-testid="invitation-result-expires">{created.expiresAt}</dd>
              </div>
            ) : null}
            <div className="invitation-result__row">
              <dt>Accept URL</dt>
              <dd>
                <a
                  href={created.acceptUrl}
                  className="invitation-result__url"
                  data-testid="invitation-result-url"
                >
                  {created.acceptUrl}
                </a>
              </dd>
            </div>
            <div className="invitation-result__row">
              <dt>Token</dt>
              <dd>
                <code data-testid="invitation-result-token">{created.token}</code>
              </dd>
            </div>
          </dl>
          <p className="invitation-result__hint">
            Share the accept URL with the invitee. The token is also returned here for
            reference.
          </p>
        </section>
      ) : null}
    </section>
  );
}