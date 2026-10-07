CREATE TABLE IF NOT EXISTS public.character_atomic_saves (
    transaction_id VARCHAR(36) PRIMARY KEY,
    payload_sha256 VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    committed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
)
