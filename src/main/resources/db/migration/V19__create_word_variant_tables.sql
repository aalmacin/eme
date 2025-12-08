-- Create word variant tables for multi-variant support
-- Each word can have multiple translations, mnemonics, images, and sentences
-- with one marked as "current" for each type

-- Create word_translations table
CREATE TABLE word_translations (
    id BIGSERIAL PRIMARY KEY,
    word_id BIGINT NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    translation TEXT NOT NULL,
    transliteration TEXT,
    is_current BOOLEAN NOT NULL DEFAULT false,
    is_user_created BOOLEAN NOT NULL DEFAULT false,
    source VARCHAR(50),  -- 'openai', 'google', 'user', 'legacy'
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_word_translations_word_id ON word_translations(word_id);
CREATE INDEX idx_word_translations_current ON word_translations(word_id, is_current) WHERE is_current = true;

-- Create word_mnemonics table
CREATE TABLE word_mnemonics (
    id BIGSERIAL PRIMARY KEY,
    word_id BIGINT NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    mnemonic_keyword TEXT,
    mnemonic_sentence TEXT,
    character_guide_id BIGINT REFERENCES character_guide(id) ON DELETE SET NULL,
    is_current BOOLEAN NOT NULL DEFAULT false,
    is_user_created BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_word_mnemonics_word_id ON word_mnemonics(word_id);
CREATE INDEX idx_word_mnemonics_current ON word_mnemonics(word_id, is_current) WHERE is_current = true;

-- Create word_images table
CREATE TABLE word_images (
    id BIGSERIAL PRIMARY KEY,
    word_id BIGINT NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    image_file TEXT,
    image_gcs_url TEXT,
    image_prompt TEXT,
    image_style VARCHAR(50),  -- 'REALISTIC_CINEMATIC', 'CARTOON', etc.
    is_current BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_word_images_word_id ON word_images(word_id);
CREATE INDEX idx_word_images_current ON word_images(word_id, is_current) WHERE is_current = true;

-- Create word_sentences table
CREATE TABLE word_sentences (
    id BIGSERIAL PRIMARY KEY,
    word_id BIGINT NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    sentence_source TEXT,
    sentence_transliteration TEXT,
    sentence_target TEXT,
    word_structure TEXT,
    word_romanized TEXT,
    audio_file TEXT,
    is_current BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_word_sentences_word_id ON word_sentences(word_id);
CREATE INDEX idx_word_sentences_current ON word_sentences(word_id, is_current) WHERE is_current = true;
