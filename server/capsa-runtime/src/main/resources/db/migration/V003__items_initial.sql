CREATE TABLE items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    list_id      UUID NOT NULL REFERENCES lists(id),
    capture_id   UUID,
    name         VARCHAR(1000) NOT NULL,
    notes        TEXT,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_items_capture_id UNIQUE (capture_id)
);

CREATE INDEX idx_items_list_id ON items(list_id);
CREATE INDEX idx_items_list_status ON items(list_id, status);
