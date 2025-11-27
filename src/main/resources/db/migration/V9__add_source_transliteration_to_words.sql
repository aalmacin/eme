-- Add source_transliteration column to words table
ALTER TABLE words ADD COLUMN IF NOT EXISTS source_transliteration TEXT;
