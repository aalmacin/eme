-- Add processing status fields to words table
ALTER TABLE words
ADD COLUMN translation_status VARCHAR(20) DEFAULT 'PENDING',
ADD COLUMN audio_generation_status VARCHAR(20) DEFAULT 'PENDING',
ADD COLUMN image_generation_status VARCHAR(20) DEFAULT 'PENDING';

-- Create indexes for status fields for efficient querying
CREATE INDEX IF NOT EXISTS idx_words_translation_status ON words(translation_status);
CREATE INDEX IF NOT EXISTS idx_words_audio_status ON words(audio_generation_status);
CREATE INDEX IF NOT EXISTS idx_words_image_status ON words(image_generation_status);

-- Update existing records to set appropriate status based on existing data
UPDATE words
SET translation_status = CASE
    WHEN translation IS NOT NULL AND translation != '' THEN 'COMPLETED'
    ELSE 'PENDING'
END;

UPDATE words
SET audio_generation_status = CASE
    WHEN audio_source_file IS NOT NULL OR audio_target_file IS NOT NULL THEN 'COMPLETED'
    ELSE 'PENDING'
END;

UPDATE words
SET image_generation_status = CASE
    WHEN image_file IS NOT NULL THEN 'COMPLETED'
    ELSE 'PENDING'
END;
