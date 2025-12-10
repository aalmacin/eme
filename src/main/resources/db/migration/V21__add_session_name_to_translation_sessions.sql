-- Add session_name column to translation_sessions table
-- This allows users to provide custom names for their translation sessions
-- If not provided, the session name will default to the first source word

ALTER TABLE translation_sessions
    ADD COLUMN session_name VARCHAR(200);

-- Set existing sessions' names to their first word (backward compatibility)
UPDATE translation_sessions
SET session_name = word
WHERE session_name IS NULL;
