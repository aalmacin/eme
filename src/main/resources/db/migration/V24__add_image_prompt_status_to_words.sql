-- Add image_prompt_status column to words table
-- This allows controlling image generation flow: generate prompts first, review/edit, then generate images

-- Add image_prompt_status column with default 'NONE' for existing records
ALTER TABLE words
    ADD COLUMN IF NOT EXISTS image_prompt_status VARCHAR(20) DEFAULT 'NONE';

-- Status values:
-- NONE: No prompt generated yet
-- GENERATED: Prompt has been generated and is ready for review
-- APPROVED: Prompt has been approved and is ready for image generation
-- REJECTED: Prompt was rejected (user wants to regenerate)

-- Add index for filtering by status
CREATE INDEX IF NOT EXISTS idx_words_image_prompt_status ON words(image_prompt_status);

-- Set existing words with image_prompt to APPROVED (they already have prompts that were used)
UPDATE words SET image_prompt_status = 'APPROVED' WHERE image_prompt IS NOT NULL AND image_prompt != '';

-- Set existing words without image_prompt to NONE
UPDATE words SET image_prompt_status = 'NONE' WHERE image_prompt IS NULL OR image_prompt = '';
