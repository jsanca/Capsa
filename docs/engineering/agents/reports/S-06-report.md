# S-06 Report — Error Model + OIDC Hardening

## Summary

Cross-cutting hardening pass. Introduced a standard error response body, per-capability exception mappers, a fallback mapper for unhandled exceptions, and production OIDC configuration via environment variables. Fixed inline validation responses in all REST resources to emit the canonical format.

## Error Model

`ErrorResponse` record added to `capsa-runtime`:
```json
{ "code": "CAPSA_CAPABILITY_ERROR_KEY", "message": "Human-readable message." }
```

## Exception Mappers (capsa-runtime)

| Mapper | Exception | HTTP Status | Code |
|---|---|---|---|
| `ListNotFoundExceptionMapper` | `ListNotFoundException` | 404 | `CAPSA_LIST_NOT_FOUND` |
| `ListAccessDeniedExceptionMapper` | `ListAccessDeniedException` | 403 | `CAPSA_ACCESS_DENIED` |
| `ItemNotFoundExceptionMapper` | `ItemNotFoundException` | 404 | `CAPSA_ITEM_NOT_FOUND` |
| `ItemAccessDeniedExceptionMapper` | `ItemAccessDeniedException` | 403 | `CAPSA_ACCESS_DENIED` |
| `CaptureNotFoundExceptionMapper` | `CaptureNotFoundException` | 404 | `CAPSA_CAPTURE_NOT_FOUND` |
| `CaptureNotAwaitingResolutionExceptionMapper` | `CaptureNotAwaitingResolutionException` | 409 | `CAPSA_CAPTURE_NOT_AWAITING_RESOLUTION` |
| `UserNotFoundExceptionMapper` | `UserNotFoundException` | 404 | `CAPSA_USER_NOT_FOUND` |
| `FallbackExceptionMapper` | `Exception` (catch-all) | 500 | `CAPSA_INTERNAL_ERROR` |

`FallbackExceptionMapper` delegates `WebApplicationException` back to its embedded response and logs unexpected exceptions at ERROR level via `System.Logger`.

## Validation Errors

All three REST resources (`ListResource`, `ItemResource`, `CaptureResource`) updated:
- Status changed from `400` to `422`
- Response body changed from `{"error":"..."}` to `{"code":"CAPSA_VALIDATION_ERROR","message":"..."}`

`CaptureResource` also had its manual try/catch for `CaptureNotFoundException` and `CaptureNotAwaitingResolutionException` removed — these are now handled by their dedicated mappers.

## OidcCurrentUser — JWT Claim Extraction

`OidcCurrentUser` now attempts to cast the injected `Principal` to `JsonWebToken` (MicroProfile JWT). In production with Quarkus OIDC, the principal IS a `JsonWebToken`; claim extraction proceeds for `email` and `name`, with fallback to the OIDC subject if a claim is absent. In test mode (`@TestSecurity`), the principal is a simple mock — the cast fails gracefully and the subject is used for both identity and email, preserving all existing test behavior without change.

## OIDC Production Configuration

`application.properties` now references env vars for the production OIDC issuer:
```properties
quarkus.oidc.auth-server-url=${CAPSA_OIDC_AUTH_SERVER_URL:}
quarkus.oidc.client-id=${CAPSA_OIDC_CLIENT_ID:capsa}
```
Dev and test profiles continue to disable OIDC. No secrets in source.

## module-info.java Changes

`capsa.runtime` now explicitly requires:
- `jakarta.ws.rs` — for `ExceptionMapper` implementations in the error package
- `microprofile.jwt.auth.api` — for `JsonWebToken` type reference in `OidcCurrentUser`

## Tests Updated

Existing tests updated to expect `422` (not `400`) for validation failures and to assert the `code` field. New test added: `getByIdForNonExistentListReturns404` in `ListResourceTest`. Error code assertions added to existing 404 and 409 tests in `CaptureResourceTest`.

## Test Result

```
Tests run: 62, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

(19 unit + 43 Quarkus integration)

## No Behavioral Changes to Use Cases

All UC-01–UC-06 happy paths are unchanged. Error responses are now machine-readable with stable codes; previously they were inconsistent JSON strings.
