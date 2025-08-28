package com.raidrin.eme.sentence;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import com.raidrin.eme.cache.CacheKeyTracker;
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
    
    @Autowired
    private CacheKeyTracker cacheKeyTracker;
    
    private final RestTemplate restTemplate;
    
    public SentenceGenerationService() {
        this.restTemplate = new RestTemplate();
    }
    
    @Cacheable(value = "sentenceCache", key = "#word + '_' + #sourceLanguage + '_' + #targetLanguage")
    public SentenceData generateSentence(String word, String sourceLanguage, String targetLanguage) {
        String cacheKey = word + "_" + sourceLanguage + "_" + targetLanguage;
        cacheKeyTracker.addSentenceKey(cacheKey);
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
            
            System.out.println("Making OpenAI API request...");
            ResponseEntity<OpenAiResponse> response = restTemplate.exchange(
                "https://api.openai.com/v1/chat/completions",
                HttpMethod.POST,
                entity,
                OpenAiResponse.class
            );
            
            System.out.println("OpenAI API response status: " + response.getStatusCode());
            
            if (response.getBody() != null && !response.getBody().getChoices().isEmpty()) {
                String content = response.getBody().getChoices().get(0).getMessage().getContent();
                System.out.println("OpenAI response content: " + content);
                return parseSentenceResponse(content);
            } else {
                System.out.println("OpenAI response body is null or has no choices");
                throw new RuntimeException("OpenAI API returned empty response");
            }
        } catch (Exception e) {
            System.err.println("OpenAI API error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to generate sentence using OpenAI API: " + e.getMessage(), e);
        }
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
        
        public OpenAiMessage() {} // Default constructor for Jackson
        
        public OpenAiMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
    
    @Data
    private static class OpenAiResponse {
        private List<OpenAiChoice> choices;
        
        public OpenAiResponse() {} // Default constructor for Jackson
    }
    
    @Data
    private static class OpenAiChoice {
        private OpenAiMessage message;
        
        public OpenAiChoice() {} // Default constructor for Jackson
    }
}
