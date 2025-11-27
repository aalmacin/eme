-- Create generation_presets table for storing preset configurations
CREATE TABLE generation_presets (
    id BIGSERIAL PRIMARY KEY,
    preset_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),

    -- Language settings
    source_language VARCHAR(10) NOT NULL,
    target_language VARCHAR(10) NOT NULL,
    source_language_code VARCHAR(10),
    target_language_code VARCHAR(10),

    -- Audio settings
    enable_source_audio BOOLEAN NOT NULL DEFAULT FALSE,
    enable_target_audio BOOLEAN NOT NULL DEFAULT FALSE,
    source_audio_language_code VARCHAR(50),
    target_audio_language_code VARCHAR(50),
    source_voice_gender VARCHAR(50),
    target_voice_gender VARCHAR(50),
    source_voice_name VARCHAR(100),
    target_voice_name VARCHAR(100),

    -- Feature flags
    enable_translation BOOLEAN NOT NULL DEFAULT TRUE,
    enable_sentence_generation BOOLEAN NOT NULL DEFAULT FALSE,
    enable_image_generation BOOLEAN NOT NULL DEFAULT FALSE,

    -- Anki settings
    anki_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    anki_deck VARCHAR(100),
    anki_front_template TEXT,
    anki_back_template TEXT,

    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    usage_count INTEGER NOT NULL DEFAULT 0,

    UNIQUE(preset_name)
);

-- Create indexes for better performance
CREATE INDEX idx_preset_name ON generation_presets(preset_name);
CREATE INDEX idx_is_default ON generation_presets(is_default);
CREATE INDEX idx_created_at ON generation_presets(created_at DESC);
