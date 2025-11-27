ALTER TABLE translation_sessions
ADD COLUMN override_translation_enabled BOOLEAN NOT NULL DEFAULT FALSE;
