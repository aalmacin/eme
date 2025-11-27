-- Add character_guide_id, image_status, and audio_status to words table
ALTER TABLE words ADD COLUMN IF NOT EXISTS character_guide_id BIGINT;
ALTER TABLE words ADD COLUMN IF NOT EXISTS image_status VARCHAR(20);
ALTER TABLE words ADD COLUMN IF NOT EXISTS audio_status VARCHAR(20);

-- Add foreign key constraint to character_guide table
ALTER TABLE words ADD CONSTRAINT fk_words_character_guide
    FOREIGN KEY (character_guide_id) REFERENCES character_guide(id) ON DELETE SET NULL;

-- Add index for character_guide_id lookups
CREATE INDEX IF NOT EXISTS idx_words_character_guide_id ON words(character_guide_id);
