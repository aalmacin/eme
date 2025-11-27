-- Add audio_file column to sentences table
ALTER TABLE sentences
ADD COLUMN audio_file TEXT;
