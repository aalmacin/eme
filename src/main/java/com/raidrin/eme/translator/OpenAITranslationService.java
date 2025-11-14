package com.raidrin.eme.translator;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.raidrin.eme.storage.service.TranslationStorageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Primary
@Qualifier("openai")
@RequiredArgsConstructor
public class OpenAITranslationService implements TranslationService {

    @Value("${openai.api.key}")
    private String openAiApiKey;

    private final TranslationStorageService translationStorageService;
    private final RestTemplate restTemplate;

    @Override
    public Set<String> translateText(String text, String sourceLanguage, String targetLanguage) {
        if (sourceLanguage == null || sourceLanguage.trim().isEmpty()) {
            throw new IllegalArgumentException("Source language must be provided");
        }
        if (targetLanguage == null || targetLanguage.trim().isEmpty()) {
            throw new IllegalArgumentException("Target language must be provided");
        }

        // Check if translation already exists in storage
        Optional<Set<String>> existingTranslation = translationStorageService.findTranslations(text, sourceLanguage, targetLanguage);
        if (existingTranslation.isPresent()) {
            System.out.println("Found existing translation for: " + text + " (" + sourceLanguage + " -> " + targetLanguage + ")");
            return existingTranslation.get();
        }

        // Perform new translation
        System.out.println("Translating with OpenAI API: " + text + " (" + sourceLanguage + " -> " + targetLanguage + ")");
        Set<String> translations = performTranslation(text, sourceLanguage, targetLanguage);

        // Save the translation
        translationStorageService.saveTranslations(text, sourceLanguage, targetLanguage, translations);

        return translations;
    }

    private Set<String> performTranslation(String text, String sourceLanguage, String targetLanguage) {
        String sourceLangName = getLanguageName(sourceLanguage);
        String targetLangName = getLanguageName(targetLanguage);

        String prompt = String.format(
                "Translate the following text from %s to %s. Provide only the translation(s), " +
                "separated by newlines if there are multiple valid translations. " +
                "Do not include explanations or additional text.\n\nText to translate: %s",
                sourceLangName, targetLangName, text
        );

        OpenAiRequest request = new OpenAiRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(
                new OpenAiMessage("system", "You are a professional translator. Provide only the translation(s), one per line. No explanations."),
                new OpenAiMessage("user", prompt)
        ));
        request.setMaxTokens(200);
        request.setTemperature(0.3);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openAiApiKey);
            headers.set("Content-Type", "application/json");

            HttpEntity<OpenAiRequest> entity = new HttpEntity<>(request, headers);

            System.out.println("Making OpenAI API request for translation...");
            ResponseEntity<OpenAiResponse> response = restTemplate.exchange(
                    "https://api.openai.com/v1/chat/completions",
                    HttpMethod.POST,
                    entity,
                    OpenAiResponse.class
            );

            System.out.println("OpenAI API response status: " + response.getStatusCode());

            if (response.getBody() != null && !response.getBody().getChoices().isEmpty()) {
                String content = response.getBody().getChoices().get(0).getMessage().getContent();
                System.out.println("OpenAI translation response: " + content);
                return parseTranslationResponse(content);
            } else {
                System.out.println("OpenAI response body is null or has no choices");
                throw new RuntimeException("OpenAI API returned empty response");
            }
        } catch (Exception e) {
            System.err.println("OpenAI API error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to translate using OpenAI API: " + e.getMessage(), e);
        }
    }

    private Set<String> parseTranslationResponse(String response) {
        Set<String> translations = new HashSet<>();
        String[] lines = response.split("\n");

        for (String line : lines) {
            String cleanedLine = line.trim();
            // Remove any numbering or bullet points
            cleanedLine = cleanedLine.replaceAll("^[\\d\\-\\*\\.]+\\s*", "");
            if (!cleanedLine.isEmpty()) {
                translations.add(cleanedLine);
            }
        }

        // If no translations were parsed (shouldn't happen), add the raw response
        if (translations.isEmpty()) {
            translations.add(response.trim());
        }

        return translations;
    }

    private String getLanguageName(String lang) {
        switch (lang) {
            case "en" -> {
                return "English";
            }
            case "es" -> {
                return "Spanish";
            }
            case "fr" -> {
                return "French";
            }
            case "cafr" -> {
                return "Canadian French";
            }
            case "kr" -> {
                return "Korean";
            }
            case "jp" -> {
                return "Japanese";
            }
            case "hi" -> {
                return "Hindi";
            }
            default -> {
                return "English";
            }
        }
    }

    @Data
    private static class OpenAiRequest {
        private String model;
        private List<OpenAiMessage> messages;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        private Double temperature;
    }

    @Data
    private static class OpenAiMessage {
        private String role;
        private String content;

        public OpenAiMessage() {
        }

        public OpenAiMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Data
    private static class OpenAiResponse {
        private List<OpenAiChoice> choices;

        public OpenAiResponse() {
        }
    }

    @Data
    private static class OpenAiChoice {
        private OpenAiMessage message;

        public OpenAiChoice() {
        }
    }
}
