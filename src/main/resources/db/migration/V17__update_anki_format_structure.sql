-- Update Anki format structure in translation_sessions table
-- Drop old template columns and add new anki_format column
ALTER TABLE translation_sessions
DROP COLUMN IF EXISTS anki_front_template,
DROP COLUMN IF EXISTS anki_back_template,
ADD COLUMN IF NOT EXISTS anki_format TEXT;

-- Update Anki format structure in generation_presets table
-- Drop old template columns and add new anki_format column
ALTER TABLE generation_presets
DROP COLUMN IF EXISTS anki_front_template,
DROP COLUMN IF EXISTS anki_back_template,
ADD COLUMN IF NOT EXISTS anki_format TEXT;
