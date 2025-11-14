-- Add fields for Anki card creation and sentence generation to translation_sessions
ALTER TABLE translation_sessions
ADD COLUMN IF NOT EXISTS anki_enabled BOOLEAN NOT NULL DEFAULT false,
ADD COLUMN IF NOT EXISTS anki_deck TEXT,
ADD COLUMN IF NOT EXISTS anki_front_template TEXT,
ADD COLUMN IF NOT EXISTS anki_back_template TEXT,
ADD COLUMN IF NOT EXISTS sentence_generation_enabled BOOLEAN NOT NULL DEFAULT false;
