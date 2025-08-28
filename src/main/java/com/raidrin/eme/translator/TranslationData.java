package com.raidrin.eme.translator;

import lombok.Data;
import java.io.Serializable;
import java.util.Set;

@Data
public class TranslationData implements Serializable {
    private static final long serialVersionUID = 1L;
    private String word;                    // The original word
    private String sourceLanguage;          // Source language code
    private String targetLanguage;          // Target language code
    private Set<String> translations;       // Set of translations
}
