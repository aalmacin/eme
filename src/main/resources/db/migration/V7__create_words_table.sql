-- Create words table for centralized word data storage
CREATE TABLE IF NOT EXISTS words (
    id BIGSERIAL PRIMARY KEY,
    word TEXT NOT NULL,
    source_language VARCHAR(10) NOT NULL,
    target_language VARCHAR(10) NOT NULL,
    translation TEXT,
    audio_source_file TEXT,
    audio_target_file TEXT,
    image_file TEXT,
    image_prompt TEXT,
    mnemonic_keyword TEXT,
    mnemonic_sentence TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (word, source_language, target_language)
);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_words_lookup ON words(word, source_language, target_language);
CREATE INDEX IF NOT EXISTS idx_words_source_lang ON words(source_language);
CREATE INDEX IF NOT EXISTS idx_words_updated_at ON words(updated_at DESC);
