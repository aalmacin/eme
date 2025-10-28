package com.raidrin.eme.codec;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Codec {
    public static String encode(String originalString) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(originalString.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String encodedString) {
        byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedString);
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }

    /**
     * Encodes text for use as an audio file name.
     * Transliterates non-Latin scripts to Latin characters, then includes the first 100 characters
     * of the transliterated text (file-name safe) followed by a hash for uniqueness.
     *
     * @param text The text to encode
     * @return A file-name safe string with readable prefix and hash
     */
    public static String encodeForAudioFileName(String text) {
        // Transliterate to Latin characters first (handles Hindi, Japanese, Korean, etc.)
        String transliterated = TransliterationService.transliterateForFileName(text);

        // Create a file-name safe prefix from the first 100 characters of transliterated text
        String prefix = transliterated.length() > 100
            ? transliterated.substring(0, 100)
            : transliterated;

        // Remove trailing underscores from truncation
        prefix = prefix.replaceAll("_+$", "");

        // If prefix is empty or too short, use a default
        if (prefix.isEmpty() || prefix.length() < 3) {
            prefix = "audio";
        }

        // Generate Base64 hash for uniqueness
        String hash = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(text.getBytes(StandardCharsets.UTF_8));

        // Combine prefix with hash (limit hash to first 8 chars to keep filename shorter)
        return prefix + "_" + hash.substring(0, Math.min(8, hash.length()));
    }
}
