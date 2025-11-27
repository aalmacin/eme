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

    // Language-specific transliterators for better accuracy
    // For Japanese: Convert Hiragana/Katakana but NOT Kanji (to avoid Chinese readings)
    // Kanji require dictionary lookup for proper Japanese readings
    private static final Transliterator JAPANESE_TO_LATIN = Transliterator.getInstance("Hiragana-Latin; Katakana-Latin");
    private static final Transliterator KOREAN_TO_LATIN = Transliterator.getInstance("Hangul-Latin");
    private static final Transliterator HINDI_TO_LATIN = Transliterator.getInstance("Devanagari-Latin");
    private static final Transliterator PUNJABI_TO_LATIN = Transliterator.getInstance("Gurmukhi-Latin");

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
     * Transliterates text using language-specific rules for better accuracy.
     * This method should be used when the source language is known.
     *
     * @param text The text to transliterate
     * @param sourceLanguage The source language code (e.g., "jp", "kr", "hi")
     * @return The transliterated text in Latin characters
     */
    public static String transliterate(String text, String sourceLanguage) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        if (sourceLanguage == null || sourceLanguage.isEmpty()) {
            return transliterate(text);
        }

        String latinText;

        // Use language-specific transliterators for better accuracy
        switch (sourceLanguage) {
            case "jp" -> {
                // Japanese: Only convert Hiragana and Katakana
                // For Kanji, we need to use a workaround to get Japanese readings
                // First, try to use the katakana version of the text if available
                String kanaOnlyText = JAPANESE_TO_LATIN.transliterate(text);

                // If there are still Han characters (kanji) that weren't converted,
                // we need to handle them differently
                // Unfortunately ICU4J doesn't support Kanji->Japanese readings without a dictionary
                // So we'll just strip remaining kanji characters to avoid Chinese readings
                latinText = kanaOnlyText.replaceAll("[\\p{IsHan}]", "");
            }
            case "kr" -> {
                // Korean: Use Hangul transliterator
                latinText = KOREAN_TO_LATIN.transliterate(text);
            }
            case "hi" -> {
                // Hindi: Use Devanagari transliterator
                latinText = HINDI_TO_LATIN.transliterate(text);
            }
            case "pa" -> {
                // Punjabi: Use Gurmukhi transliterator
                latinText = PUNJABI_TO_LATIN.transliterate(text);
            }
            default -> {
                // For other languages, use generic Any-Latin
                latinText = ANY_TO_LATIN.transliterate(text);
            }
        }

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
