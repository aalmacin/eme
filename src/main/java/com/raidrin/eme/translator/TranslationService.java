package com.raidrin.eme.translator;

public interface TranslationService {
    /**
     * Translate text from source language to target language
     * Returns TranslationData containing translations and transliteration
     *
     * @param text The text to translate
     * @param sourceLanguage Source language code
     * @param targetLanguage Target language code
     * @return TranslationData with translations and transliteration
     */
    TranslationData translateText(String text, String sourceLanguage, String targetLanguage);

    /**
     * Translate text from source language to target language with cache control
     * Returns TranslationData containing translations and transliteration
     *
     * @param text The text to translate
     * @param sourceLanguage Source language code
     * @param targetLanguage Target language code
     * @param skipCache If true, skip cached translations and force new translation
     * @return TranslationData with translations and transliteration
     */
    TranslationData translateText(String text, String sourceLanguage, String targetLanguage, boolean skipCache);

    /**
     * Get only the transliteration/romanization of a word
     *
     * @param text The text to transliterate
     * @param sourceLanguage Source language code
     * @return The transliteration/romanization of the word
     */
    String getTransliteration(String text, String sourceLanguage);
}
