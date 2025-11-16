package com.raidrin.eme.image;

/**
 * Enum representing different image generation styles.
 */
public enum ImageStyle {
    REALISTIC_CINEMATIC("Realistic Cinematic"),
    ANIMATED_2D_CINEMATIC("2D Animated Cinematic"),
    ANIMATED_3D_CINEMATIC("3D Animated Cinematic");

    private final String displayName;

    ImageStyle(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get ImageStyle from string value (case-insensitive).
     * Returns REALISTIC_CINEMATIC as default if not found.
     */
    public static ImageStyle fromString(String value) {
        if (value == null) {
            return REALISTIC_CINEMATIC;
        }

        for (ImageStyle style : ImageStyle.values()) {
            if (style.name().equalsIgnoreCase(value) ||
                style.displayName.equalsIgnoreCase(value)) {
                return style;
            }
        }

        return REALISTIC_CINEMATIC; // default fallback
    }
}
