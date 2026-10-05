CREATE TABLE classification_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    capture_id UUID NOT NULL REFERENCES captures(id),
    outcome VARCHAR(20),
    candidates TEXT,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE classification_resolutions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    capture_id UUID NOT NULL UNIQUE REFERENCES captures(id),
    selected_list_id UUID NOT NULL,
    resolved_by VARCHAR(20) NOT NULL,
    resolved_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
