-- Add mnemonic_keyword_updated_at column to words table
-- This field tracks when a user manually updates the mnemonic keyword
-- Automated processes should not override mnemonic keyword when this field is set

ALTER TABLE words ADD COLUMN IF NOT EXISTS mnemonic_keyword_updated_at TIMESTAMP;

-- Add index for querying words with manual mnemonic keyword updates
CREATE INDEX IF NOT EXISTS idx_words_mnemonic_keyword_updated_at ON words(mnemonic_keyword_updated_at);
