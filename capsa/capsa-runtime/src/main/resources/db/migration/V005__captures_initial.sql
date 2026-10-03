CREATE TABLE captures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    original_content TEXT NOT NULL,
    normalized_content TEXT NOT NULL,
    processing_status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING',
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_captures_user_id ON captures(user_id);
