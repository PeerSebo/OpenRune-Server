CREATE TABLE IF NOT EXISTS public.character_save_revisions (
    character_id INTEGER PRIMARY KEY REFERENCES public.account_characters(id) ON DELETE CASCADE,
    generation VARCHAR(36) NOT NULL,
    sequence BIGINT NOT NULL CHECK (sequence >= 0)
)
