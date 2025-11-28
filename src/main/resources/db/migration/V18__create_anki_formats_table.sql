-- Create anki_formats table for storing reusable Anki card format templates
CREATE TABLE anki_formats (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    format_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(name)
);

-- Create indexes for better performance
CREATE INDEX idx_anki_formats_name ON anki_formats(name);
CREATE INDEX idx_anki_formats_is_default ON anki_formats(is_default);

-- Add anki_format_id to translation_sessions and generation_presets
ALTER TABLE translation_sessions
ADD COLUMN IF NOT EXISTS anki_format_id BIGINT,
ADD CONSTRAINT fk_translation_sessions_anki_format
    FOREIGN KEY (anki_format_id) REFERENCES anki_formats(id) ON DELETE SET NULL;

ALTER TABLE generation_presets
ADD COLUMN IF NOT EXISTS anki_format_id BIGINT,
ADD CONSTRAINT fk_generation_presets_anki_format
    FOREIGN KEY (anki_format_id) REFERENCES anki_formats(id) ON DELETE SET NULL;
