CREATE TABLE classification_memory_entries (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id),
    normalized_content TEXT NOT NULL,
    selected_list_id  UUID NOT NULL,
    source            VARCHAR(20) NOT NULL,
    recorded_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cme_user_normalized ON classification_memory_entries(user_id, normalized_content);
CREATE INDEX idx_cme_user_source ON classification_memory_entries(user_id, source);
