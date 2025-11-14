package com.raidrin.eme.translator;

import java.util.Set;

public interface TranslationService {
    Set<String> translateText(String text, String sourceLanguage, String targetLanguage);
}
