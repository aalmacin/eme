package com.raidrin.eme.codec;

import com.ibm.icu.text.Transliterator;

/**
 * Service for transliterating non-Latin scripts to Latin characters.
 * Uses ICU4J library for comprehensive Unicode transliteration support.
 */
public class TransliterationService {

    // Transliterators for different scripts
    private static final Transliterator ANY_TO_LATIN = Transliterator.getInstance("Any-Latin");
    private static final Transliterator LATIN_TO_ASCII = Transliterator.getInstance("Latin-ASCII");

    /**
     * Transliterates text from any script to Latin characters.
     * This handles scripts like Devanagari (Hindi), Hangul (Korean),
     * Kanji/Hiragana/Katakana (Japanese), Cyrillic, Arabic, etc.
     *
     * @param text The text to transliterate
     * @return The transliterated text in Latin characters
     */
    public static String transliterate(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // First convert any script to Latin
        String latinText = ANY_TO_LATIN.transliterate(text);

        // Then convert Latin with diacritics to ASCII
        String asciiText = LATIN_TO_ASCII.transliterate(latinText);

        return asciiText;
    }

    /**
     * Transliterates text and makes it safe for use in file names.
     * Converts non-Latin scripts to Latin, then replaces spaces with underscores
     * and removes any remaining special characters.
     *
     * @param text The text to transliterate
     * @return A file-name safe transliterated string
     */
    public static String transliterateForFileName(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Transliterate to Latin/ASCII
        String transliterated = transliterate(text);

        // Make it file-name safe
        return transliterated
            .replace(" ", "_")
            .replaceAll("[^a-zA-Z0-9_-]", "_")
            .replaceAll("_{2,}", "_") // Replace multiple underscores with single
            .replaceAll("^_|_$", ""); // Remove leading/trailing underscores
    }
}
