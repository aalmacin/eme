package com.raidrin.eme.translator;

import com.google.cloud.translate.v3.*;
import com.raidrin.eme.storage.service.TranslationStorageService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TranslatorService {
    public static final String PROJECT_ID = "translate-raidrin";
    
    private final TranslationStorageService translationStorageService;

    public Set<String> translateText(String text, String lang) {
        // Determine source language (assume Hindi for now, could be improved later)
        String sourceLanguage = "hi";
        String targetLanguage = lang;
        
        // Check if translation already exists in storage
        Optional<Set<String>> existingTranslation = translationStorageService.findTranslations(text, sourceLanguage, targetLanguage);
        if (existingTranslation.isPresent()) {
            System.out.println("Found existing translation for: " + text + " (" + sourceLanguage + " -> " + targetLanguage + ")");
            return existingTranslation.get();
        }
        
        // Perform new translation
        System.out.println("Translating with Google Translate API: " + text + " (" + sourceLanguage + " -> " + targetLanguage + ")");
        Set<String> translations = performTranslation(text, lang);
        
        // Save the translation
        translationStorageService.saveTranslations(text, sourceLanguage, targetLanguage, translations);
        
        return translations;
    }
    
    private Set<String> performTranslation(String text, String lang) {
        try {
            try (TranslationServiceClient client = TranslationServiceClient.create()) {
                // Supported Locations: `global`, [glossary location], or [model location]
                // Glossaries must be hosted in `us-central1`
                // Custom Models must use the same location as your model. (us-central1)
                LocationName parent = LocationName.of(PROJECT_ID, "global");

                // Supported Mime Types: https://cloud.google.com/translate/docs/supported-formats
                TranslateTextRequest request =
                        TranslateTextRequest.newBuilder()
                                .setParent(parent.toString())
                                .setMimeType("text/plain")
                                .setTargetLanguageCode(lang)
                                .addContents(text)
                                .build();

                System.out.println("Request translation to Google: " + request);
                TranslateTextResponse response = client.translateText(request);

                Set<String> translations = new HashSet<>();

                // Display the translation for each input text provided
                for (Translation translation : response.getTranslationsList()) {
                    String translatedText = translation.getTranslatedText();
                    if(!translatedText.isBlank())
                        translations.add(translatedText);
                }
                return translations;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to open client");
        }
    }
}
