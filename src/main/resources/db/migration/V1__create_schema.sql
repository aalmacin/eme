-- Create the initial database schema for Eme application

CREATE TABLE translations (
    id SERIAL PRIMARY KEY,
    word TEXT NOT NULL,
    source_language VARCHAR(10) NOT NULL,
    target_language VARCHAR(10) NOT NULL,
    translations TEXT NOT NULL, -- JSON array of translation options
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(word, source_language, target_language)
);

CREATE TABLE sentences (
    id SERIAL PRIMARY KEY,
    word TEXT NOT NULL,
    source_language VARCHAR(10) NOT NULL,
    target_language VARCHAR(10) NOT NULL,
    word_romanized TEXT,
    sentence_source TEXT, -- Sentence in source language
    sentence_transliteration TEXT,
    sentence_target TEXT, -- Sentence in target language
    word_structure TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(word, source_language, target_language)
);

-- Create indexes for better performance
CREATE INDEX idx_translations_word_langs ON translations(word, source_language, target_language);
CREATE INDEX idx_sentences_word_langs ON sentences(word, source_language, target_language);
CREATE INDEX idx_translations_source_lang ON translations(source_language);
CREATE INDEX idx_sentences_source_lang ON sentences(source_language);
CREATE INDEX idx_translations_created_at ON translations(created_at);
CREATE INDEX idx_sentences_created_at ON sentences(created_at);

