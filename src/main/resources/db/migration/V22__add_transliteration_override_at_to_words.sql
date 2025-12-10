-- Add transliteration_override_at column to words table
-- This tracks when a transliteration was manually overridden by the user
-- Used to determine which words should be skipped during bulk regeneration operations

ALTER TABLE words
    ADD COLUMN transliteration_override_at TIMESTAMP;

-- Add index for efficient querying of overridden words
CREATE INDEX idx_words_transliteration_override_at ON words(transliteration_override_at);
