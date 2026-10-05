-- Add oidc_issuer column (temporary nullable to support existing rows)
ALTER TABLE users ADD COLUMN oidc_issuer VARCHAR(255);

-- Backfill existing rows with a placeholder issuer.
-- NOTE: rows with oidc_issuer='legacy' are pre-auth development records and
-- are NOT intended to match any real OIDC issuer. On first real-OIDC login,
-- these users will be re-provisioned as new rows keyed on (real-issuer, subject).
-- No production account migration path is guaranteed for v0.1 pre-auth data.
UPDATE users SET oidc_issuer = 'legacy' WHERE oidc_issuer IS NULL;

-- Make oidc_issuer NOT NULL now that backfill is done
ALTER TABLE users ALTER COLUMN oidc_issuer SET NOT NULL;

-- Replace the old single-column unique constraint on oidc_subject with the compound key
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_oidc_subject_key;
ALTER TABLE users ADD CONSTRAINT users_oidc_issuer_subject_key UNIQUE (oidc_issuer, oidc_subject);

-- Add role column with default USER for existing rows
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
