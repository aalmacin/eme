package com.raidrin.eme.codec;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for TransliterationService to verify transliteration
 * of non-Latin scripts (Hindi, Japanese, Korean, etc.) to Latin characters.
 */
class TransliterationServiceTest {

    @Test
    void testHindiTransliteration() {
        String hindi = "नमस्ते";
        String result = TransliterationService.transliterate(hindi);
        System.out.println("Hindi '" + hindi + "' -> '" + result + "'");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // Should contain only Latin characters
        assertTrue(result.matches("[a-zA-Z\\s]+"));
    }

    @Test
    void testJapaneseTransliteration() {
        String japanese = "こんにちは";
        String result = TransliterationService.transliterate(japanese);
        System.out.println("Japanese '" + japanese + "' -> '" + result + "'");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // Should contain only Latin characters (may include apostrophes for transliteration)
        assertTrue(result.matches("[a-zA-Z\\s']+"));
    }

    @Test
    void testKoreanTransliteration() {
        String korean = "안녕하세요";
        String result = TransliterationService.transliterate(korean);
        System.out.println("Korean '" + korean + "' -> '" + result + "'");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // Should contain only Latin characters
        assertTrue(result.matches("[a-zA-Z\\s]+"));
    }

    @Test
    void testTransliterateForFileName() {
        String hindi = "नमस्ते दोस्त";
        String result = TransliterationService.transliterateForFileName(hindi);
        System.out.println("Hindi filename: '" + hindi + "' -> '" + result + "'");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // Should be file-name safe (only alphanumeric, underscore, dash)
        assertTrue(result.matches("[a-zA-Z0-9_-]+"));
        assertFalse(result.contains(" ")); // No spaces
    }

    @Test
    void testCodecEncodeForAudioFileName() {
        // Test with various languages
        String[] testTexts = {
            "Hello World",           // English
            "नमस्ते",                // Hindi
            "こんにちは",             // Japanese
            "안녕하세요",             // Korean
            "Bonjour le monde",     // French
            "Здравствуйте"          // Russian (Cyrillic)
        };

        for (String text : testTexts) {
            String encoded = Codec.encodeForAudioFileName(text);
            System.out.println("'" + text + "' -> '" + encoded + "'");

            assertNotNull(encoded);
            assertFalse(encoded.isEmpty());
            // Should not be just underscores
            assertFalse(encoded.matches("_+"));
            // Should contain some readable prefix (not just hash)
            assertTrue(encoded.contains("_"));
            // Should be file-name safe
            assertTrue(encoded.matches("[a-zA-Z0-9_-]+"));
        }
    }
}
