package com.raidrin.eme.translator;

import com.google.cloud.translate.v3.*;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Service
public class TranslatorService {
    public static final String PROJECT_ID = "translate-raidrin";

    @Cacheable(value = "translationCache", key = "#text + '_' + #lang")
    public Set<String> translateText(String text, String lang) {
        System.out.println("Translating...");
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
