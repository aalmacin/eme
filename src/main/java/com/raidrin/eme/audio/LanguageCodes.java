package com.raidrin.eme.audio;

public enum LanguageCodes {
    Spanish("es-US"),
    French("fr-FR"),
    CanadianFrench("fr-CA"),
    Korean("ko-KR"),
    Japanese("ja-JP");

    private final String code;

    LanguageCodes(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
