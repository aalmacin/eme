-- Add character guide table for mnemonic character associations
CREATE TABLE character_guide (
    id SERIAL PRIMARY KEY,
    language VARCHAR(10) NOT NULL,
    start_sound VARCHAR(50) NOT NULL,
    character_name VARCHAR(255) NOT NULL,
    character_context VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(language, start_sound)
);

-- Add translation sessions table for tracking async generation jobs
CREATE TABLE translation_sessions (
    id SERIAL PRIMARY KEY,
    word TEXT NOT NULL,
    source_language VARCHAR(10) NOT NULL,
    target_language VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, IN_PROGRESS, COMPLETED, FAILED
    image_generation_enabled BOOLEAN NOT NULL DEFAULT false,
    audio_generation_enabled BOOLEAN NOT NULL DEFAULT false,
    session_data TEXT, -- JSON with mnemonic data, file paths, errors, etc.
    zip_file_path TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX idx_character_guide_language ON character_guide(language);
CREATE INDEX idx_character_guide_start_sound ON character_guide(start_sound);
CREATE INDEX idx_translation_sessions_status ON translation_sessions(status);
CREATE INDEX idx_translation_sessions_created_at ON translation_sessions(created_at DESC);
CREATE INDEX idx_translation_sessions_word_langs ON translation_sessions(word, source_language, target_language);
