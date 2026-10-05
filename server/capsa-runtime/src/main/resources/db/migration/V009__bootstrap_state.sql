-- Remove the old setup_state table (superseded by bootstrap_state)
DROP TABLE IF EXISTS setup_state;

-- Bootstrap singleton: exactly one row, fixed primary key enforced by CHECK constraint.
-- ON CONFLICT(id) DO NOTHING at the application layer uses this as the concurrency anchor.
CREATE TABLE bootstrap_state (
    id                  INTEGER     PRIMARY KEY CHECK (id = 1),
    claimed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_by_user_id  UUID        NOT NULL REFERENCES users(id)
);
