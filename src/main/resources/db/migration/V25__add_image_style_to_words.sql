-- Add image_style column to words table
-- This allows each word to have its own preferred image style for regeneration
ALTER TABLE words ADD COLUMN IF NOT EXISTS image_style VARCHAR(50) DEFAULT 'REALISTIC_CINEMATIC';

-- Create index for filtering by image style
CREATE INDEX IF NOT EXISTS idx_words_image_style ON words(image_style);
