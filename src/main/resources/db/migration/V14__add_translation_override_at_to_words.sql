-- Add translation_override_at column to words table
-- This field tracks when a user manually overrides a translation
-- Automated processes should not override translations when this field is set

ALTER TABLE words ADD COLUMN IF NOT EXISTS translation_override_at TIMESTAMP;

-- Add index for querying words with manual overrides
CREATE INDEX IF NOT EXISTS idx_words_translation_override_at ON words(translation_override_at);
