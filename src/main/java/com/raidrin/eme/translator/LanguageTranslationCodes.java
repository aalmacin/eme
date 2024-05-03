package com.raidrin.eme.translator;

public enum LanguageTranslationCodes {
    English("en"),
    Spanish("es"),
    French("fr"),
    Korean("ko"),
    Japanese("ja");

    private final String code;

    LanguageTranslationCodes(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
