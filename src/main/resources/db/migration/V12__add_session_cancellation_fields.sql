-- Add cancellation tracking fields to translation_sessions table
ALTER TABLE translation_sessions ADD COLUMN cancelled_at TIMESTAMP;
ALTER TABLE translation_sessions ADD COLUMN cancellation_reason TEXT;
