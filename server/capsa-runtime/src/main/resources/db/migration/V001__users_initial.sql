CREATE TABLE users (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    oidc_subject VARCHAR(255) NOT NULL UNIQUE,
    email        VARCHAR(255) NOT NULL,
    name         VARCHAR(255),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
