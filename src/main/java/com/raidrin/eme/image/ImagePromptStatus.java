package com.raidrin.eme.image;

/**
 * Status of image prompt generation for a word.
 * Controls the workflow: generate prompts -> review/edit -> generate images
 */
public enum ImagePromptStatus {
    /**
     * No prompt generated yet
     */
    NONE,

    /**
     * Prompt has been generated and is ready for review
     */
    GENERATED,

    /**
     * Prompt has been approved and is ready for image generation
     */
    APPROVED,

    /**
     * Prompt was rejected (user wants to regenerate)
     */
    REJECTED;

    /**
     * Parse status from string (case-insensitive)
     */
    public static ImagePromptStatus fromString(String status) {
        if (status == null || status.trim().isEmpty()) {
            return NONE;
        }
        try {
            return valueOf(status.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
