package com.raidrin.eme.audio;

public enum LanguageAudioCodes {
    English("en-US"),
    Spanish("es-US"),
    French("fr-FR"),
    CanadianFrench("fr-CA"),
    Korean("ko-KR"),
    Japanese("ja-JP"),
    Hindi("hi-IN"),
    Punjabi("pa-IN"),
    Tagalog("tl-PH");

    private final String code;

    LanguageAudioCodes(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
