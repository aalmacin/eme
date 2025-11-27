package com.raidrin.eme.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Utility class for sanitizing file names
 */
public class FileNameSanitizer {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern INVALID_CHARS_PATTERN = Pattern.compile("[^a-zA-Z0-9_-]");
    private static final int MAX_FILENAME_LENGTH = 200;

    /**
     * Sanitize a string to make it safe for use as a filename
     *
     * @param input The input string (e.g., mnemonic sentence)
     * @param extension The file extension to append (e.g., "jpg", "png")
     * @return Sanitized filename
     */
    public static String sanitize(String input, String extension) {
        if (input == null || input.trim().isEmpty()) {
            return "unnamed_" + System.currentTimeMillis() + "." + extension;
        }

        // Normalize unicode characters
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);

        // Convert to lowercase
        String lowercase = normalized.toLowerCase();

        // Replace whitespace with underscores
        String withUnderscores = WHITESPACE_PATTERN.matcher(lowercase).replaceAll("_");

        // Remove invalid characters (keep only alphanumeric, underscore, and hyphen)
        String cleaned = INVALID_CHARS_PATTERN.matcher(withUnderscores).replaceAll("");

        // Remove multiple consecutive underscores
        String singleUnderscores = cleaned.replaceAll("_+", "_");

        // Remove leading/trailing underscores
        String trimmed = singleUnderscores.replaceAll("^_+|_+$", "");

        // Truncate if too long (leave room for extension)
        String truncated = trimmed;
        if (truncated.length() > MAX_FILENAME_LENGTH) {
            truncated = truncated.substring(0, MAX_FILENAME_LENGTH);
            // Remove trailing underscore if truncation created one
            truncated = truncated.replaceAll("_+$", "");
        }

        // If empty after sanitization, use a default name
        if (truncated.isEmpty()) {
            truncated = "unnamed_" + System.currentTimeMillis();
        }

        // Add extension
        return truncated + "." + extension;
    }

    /**
     * Sanitize a string to make it safe for use as a filename (without extension)
     */
    public static String sanitize(String input) {
        return sanitize(input, "");
    }

    /**
     * Create a filename from a mnemonic sentence with timestamp for uniqueness
     */
    public static String fromMnemonicSentence(String sentence, String extension) {
        String baseName = sanitize(sentence, "").replaceAll("\\.$", ""); // Remove trailing dot if any
        return baseName + "_" + System.currentTimeMillis() + "." + extension;
    }

    /**
     * Create a unique filename from a base name with timestamp
     */
    public static String makeUnique(String baseName, String extension) {
        String sanitized = sanitize(baseName, "").replaceAll("\\.$", "");
        return sanitized + "_" + System.currentTimeMillis() + "." + extension;
    }
}
