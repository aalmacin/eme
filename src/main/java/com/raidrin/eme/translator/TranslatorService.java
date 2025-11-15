package com.raidrin.eme.translator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * @deprecated This class is deprecated. Use {@link TranslationService} interface with dependency injection instead.
 * The active implementation is {@link OpenAITranslationService}.
 * For Google Translate, use {@link GoogleTranslationService}.
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class TranslatorService {

    private final TranslationService translationService;

    /**
     * @deprecated Use {@link TranslationService#translateText(String, String, String)} instead.
     */
    @Deprecated
    public Set<String> translateText(String text, String sourceLanguage, String targetLanguage) {
        TranslationData data = translationService.translateText(text, sourceLanguage, targetLanguage);
        return data.getTranslations();
    }
}
