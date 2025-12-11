-- Remove obsolete fields from translation_sessions table
-- Phase 2 simplification: removing session-level status tracking and feature flags
-- These fields added unnecessary complexity and are being removed in favor of simpler architecture

-- Drop index on status column before removing it
DROP INDEX IF EXISTS idx_translation_sessions_status;

-- Remove status tracking columns
ALTER TABLE translation_sessions
    DROP COLUMN IF EXISTS status,
    DROP COLUMN IF EXISTS completed_at,
    DROP COLUMN IF EXISTS cancelled_at,
    DROP COLUMN IF EXISTS cancellation_reason;

-- Remove feature flag columns (functionality moved to per-operation controls)
ALTER TABLE translation_sessions
    DROP COLUMN IF EXISTS image_generation_enabled,
    DROP COLUMN IF EXISTS audio_generation_enabled,
    DROP COLUMN IF EXISTS sentence_generation_enabled,
    DROP COLUMN IF EXISTS override_translation_enabled;

-- Remove old Anki template columns (replaced by anki_format_id relationship)
ALTER TABLE translation_sessions
    DROP COLUMN IF EXISTS anki_front_template,
    DROP COLUMN IF EXISTS anki_back_template;

-- Note: Keeping anki_enabled, anki_deck, and anki_format_id as these are still needed for Anki integration
