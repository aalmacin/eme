package com.raidrin.eme.sentence;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class SentenceGenerationService {
    
    @Value("${openai.api.key}")
    private String openAiApiKey;
    
    private final RestTemplate restTemplate;
    
    public SentenceGenerationService() {
        this.restTemplate = new RestTemplate();
    }
    
    @Cacheable(value = "sentenceCache", key = "#word + '_' + #sourceLanguage + '_' + #targetLanguage")
    public SentenceData generateSentence(String word, String sourceLanguage, String targetLanguage) {
        System.out.println("Generating sentences with OpenAI for: " + word + " (" + sourceLanguage + " -> " + targetLanguage + ")");
        
        String prompt = String.format(
            "Given the word '%s', create a simple sentence in %s using this word. Provide the following 5 elements separated by newlines:\n" +
            "1. The word in Latin characters (romanized)\n" +
            "2. A simple sentence in %s using this word\n" +
            "3. The sentence transliteration in Latin characters\n" +
            "4. The sentence translated to %s\n" +
            "5. Word-by-word structure analysis of the %s sentence\n\n" +
            "Format your response exactly as 5 lines, one element per line.",
            word, targetLanguage, targetLanguage, sourceLanguage, targetLanguage
        );
        
        OpenAiRequest request = new OpenAiRequest();
        request.setModel("gpt-4o-mini");  // Using the latest cost-effective model
        request.setMessages(List.of(
            new OpenAiMessage("system", "You are a language learning assistant. Provide exactly 5 lines as requested, nothing more."),
            new OpenAiMessage("user", prompt)
        ));
        request.setMaxTokens(300);
        request.setTemperature(0.7);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openAiApiKey);
            headers.set("Content-Type", "application/json");
            
            HttpEntity<OpenAiRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<OpenAiResponse> response = restTemplate.exchange(
                "https://api.openai.com/v1/chat/completions",
                HttpMethod.POST,
                entity,
                OpenAiResponse.class
            );
            
            if (response.getBody() != null && !response.getBody().getChoices().isEmpty()) {
                String content = response.getBody().getChoices().get(0).getMessage().getContent();
                return parseSentenceResponse(content);
            }
        } catch (Exception e) {
            // Return a fallback response if OpenAI fails
            return createFallbackResponse(word, targetLanguage);
        }
        
        return createFallbackResponse(word, targetLanguage);
    }
    
    private SentenceData parseSentenceResponse(String response) {
        String[] lines = response.split("\n");
        
        SentenceData sentenceData = new SentenceData();
        
        if (lines.length >= 5) {
            sentenceData.setTargetLanguageLatinCharacters(cleanLine(lines[0]));
            sentenceData.setTargetLanguageSentence(cleanLine(lines[1]));
            sentenceData.setTargetLanguageTransliteration(cleanLine(lines[2]));
            sentenceData.setSourceLanguageSentence(cleanLine(lines[3]));
            sentenceData.setSourceLanguageStructure(cleanLine(lines[4]));
        }
        
        return sentenceData;
    }
    
    private String cleanLine(String line) {
        // Remove any numbering or formatting from the response
        return line.replaceAll("^\\d+\\.\\s*", "").trim();
    }
    
    private SentenceData createFallbackResponse(String word, String targetLanguage) {
        SentenceData fallback = new SentenceData();
        fallback.setTargetLanguageLatinCharacters(word);
        fallback.setTargetLanguageSentence("Sample " + targetLanguage + " sentence with " + word);
        fallback.setTargetLanguageTransliteration("Sample transliteration");
        fallback.setSourceLanguageSentence("Sample English translation");
        fallback.setSourceLanguageStructure("Sample word-by-word structure");
        return fallback;
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
        
        public OpenAiMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
    
    @Data
    private static class OpenAiResponse {
        private List<OpenAiChoice> choices;
    }
    
    @Data
    private static class OpenAiChoice {
        private OpenAiMessage message;
    }
}
