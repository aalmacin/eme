-- Migrate existing word data to the new variant tables
-- All existing data will be marked as 'current' and source='legacy'

-- Migrate existing translations from words table
INSERT INTO word_translations (word_id, translation, transliteration, is_current, is_user_created, source, created_at)
SELECT
    id,
    translation,
    source_transliteration,
    true,  -- Mark as current
    CASE WHEN translation_override_at IS NOT NULL THEN true ELSE false END,
    'legacy',
    COALESCE(created_at, CURRENT_TIMESTAMP)
FROM words
WHERE translation IS NOT NULL AND translation != '';

-- Migrate existing mnemonics from words table
INSERT INTO word_mnemonics (word_id, mnemonic_keyword, mnemonic_sentence, character_guide_id, is_current, is_user_created, created_at)
SELECT
    id,
    mnemonic_keyword,
    mnemonic_sentence,
    character_guide_id,
    true,  -- Mark as current
    CASE WHEN mnemonic_keyword_updated_at IS NOT NULL THEN true ELSE false END,
    COALESCE(created_at, CURRENT_TIMESTAMP)
FROM words
WHERE mnemonic_keyword IS NOT NULL OR mnemonic_sentence IS NOT NULL;

-- Migrate existing images from words table
INSERT INTO word_images (word_id, image_file, image_prompt, is_current, created_at)
SELECT
    id,
    image_file,
    image_prompt,
    true,  -- Mark as current
    COALESCE(created_at, CURRENT_TIMESTAMP)
FROM words
WHERE image_file IS NOT NULL AND image_file != '';

-- Migrate existing sentences from sentences table
-- Join with words table to get the word_id
INSERT INTO word_sentences (word_id, sentence_source, sentence_transliteration, sentence_target, word_structure, word_romanized, audio_file, is_current, created_at)
SELECT
    w.id,
    s.sentence_source,
    s.sentence_transliteration,
    s.sentence_target,
    s.word_structure,
    s.word_romanized,
    s.audio_file,
    true,  -- Mark as current
    COALESCE(s.created_at, CURRENT_TIMESTAMP)
FROM sentences s
INNER JOIN words w ON s.word = w.word
    AND s.source_language = w.source_language
    AND s.target_language = w.target_language;
