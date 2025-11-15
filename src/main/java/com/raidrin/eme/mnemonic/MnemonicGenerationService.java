package com.raidrin.eme.mnemonic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raidrin.eme.storage.entity.CharacterGuideEntity;
import com.raidrin.eme.storage.service.CharacterGuideService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Service for generating mnemonic keywords, sentences, and image prompts using OpenAI
 */
@Service
@RequiredArgsConstructor
public class MnemonicGenerationService {

    @Value("${openai.api.key}")
    private String openAiApiKey;

    private final CharacterGuideService characterGuideService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Generate mnemonic data for a word translation with transliteration for character matching
     *
     * @param sourceWord Word in source language
     * @param targetWord Word in target language (translation)
     * @param sourceLanguage Source language code
     * @param targetLanguage Target language code
     * @param sourceTransliteration Transliteration of source word (from translation service)
     * @return MnemonicData containing keyword, sentence, and image prompt
     */
    public MnemonicData generateMnemonic(String sourceWord, String targetWord,
                                         String sourceLanguage, String targetLanguage,
                                         String sourceTransliteration) {

        // Find character association for source word if transliteration is available
        Optional<CharacterGuideEntity> sourceCharacter = (sourceTransliteration != null && !sourceTransliteration.trim().isEmpty())
                ? characterGuideService.findMatchingCharacterForWord(sourceWord, sourceLanguage, sourceTransliteration)
                : Optional.empty();

        if (sourceCharacter.isEmpty() && (sourceTransliteration == null || sourceTransliteration.trim().isEmpty())) {
            System.out.println("No transliteration provided for mnemonic generation, skipping character matching: " + sourceWord);
        }

        // Build the prompt
        String prompt = buildMnemonicPrompt(sourceWord, targetWord, sourceLanguage, targetLanguage,
                sourceCharacter);

        System.out.println("Generating mnemonic with OpenAI for: " + sourceWord + " -> " + targetWord);

        // Call OpenAI API
        OpenAiRequest request = new OpenAiRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(
                new OpenAiMessage("system", "You are a mnemonic memory expert who creates vivid, memorable associations for language learning. You MUST use REAL, WELL-KNOWN characters from actual shows, movies, sports, or public life - NEVER create fictional characters. Always specify the character's origin (e.g., 'from Naruto', 'the NBA player'). Always respond with valid JSON only."),
                new OpenAiMessage("user", prompt)
        ));
        request.setMaxTokens(500);
        request.setTemperature(0.7);
        request.setResponseFormat(new ResponseFormat("json_object"));

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
                System.out.println("Mnemonic generation response: " + content);

                // Parse JSON response
                MnemonicData mnemonicData = objectMapper.readValue(content, MnemonicData.class);
                return mnemonicData;
            } else {
                throw new RuntimeException("OpenAI API returned empty response");
            }
        } catch (Exception e) {
            System.err.println("Mnemonic generation error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to generate mnemonic: " + e.getMessage(), e);
        }
    }

    /**
     * Generate mnemonic without transliteration (backward compatibility)
     * Character matching will be skipped without transliteration.
     *
     * @param sourceWord Word in source language
     * @param targetWord Word in target language (translation)
     * @param sourceLanguage Source language code
     * @param targetLanguage Target language code
     * @return MnemonicData containing keyword, sentence, and image prompt
     */
    public MnemonicData generateMnemonic(String sourceWord, String targetWord,
                                         String sourceLanguage, String targetLanguage) {
        System.out.println("Warning: generateMnemonic called without transliteration, character matching will be skipped");
        return generateMnemonic(sourceWord, targetWord, sourceLanguage, targetLanguage, null);
    }

    /**
     * Generate multiple mnemonics for a list of translation pairs
     */
    public List<MnemonicData> generateMnemonics(List<TranslationPair> translations,
                                                String sourceLanguage, String targetLanguage) {
        return translations.stream()
                .map(pair -> generateMnemonic(pair.getSourceWord(), pair.getTargetWord(),
                        sourceLanguage, targetLanguage))
                .toList();
    }

    private String buildMnemonicPrompt(String sourceWord, String targetWord,
                                       String sourceLanguage, String targetLanguage,
                                       Optional<CharacterGuideEntity> sourceCharacter) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("Create a mnemonic to remember that '").append(sourceWord).append("' (")
                .append(getLanguageName(sourceLanguage)).append(") means '").append(targetWord)
                .append("' (").append(getLanguageName(targetLanguage)).append(").\n\n");

        // Character assignment section - USE ONLY ONE CHARACTER
        prompt.append("CHARACTER TO USE:\n");

        // Add character information if available (prioritize source character)
        if (sourceCharacter.isPresent()) {
            CharacterGuideEntity sc = sourceCharacter.get();
            prompt.append("- Use ONLY this character: ").append(sc.getCharacterName()).append(" from ").append(sc.getCharacterContext())
                    .append(" (REQUIRED - DO NOT CHANGE THIS CHARACTER)\n");
        } else {
            prompt.append("- You MUST choose EXACTLY ONE REAL, WELL-KNOWN character whose name starts with the EXACT SAME PHONETIC SOUND as '")
                    .append(sourceWord).append("'.\n");
            prompt.append("  CRITICAL: First determine how '").append(sourceWord).append("' is PRONOUNCED (romanization), then match that sound.\n");
            prompt.append("  Example: If '").append(sourceWord).append("' sounds like 'aa' or 'aap', choose 'Aang from Avatar: The Last Airbender' (starts with 'A').\n");
            prompt.append("  Example: If sounds like 'kha', choose 'Krillin from Dragon Ball' (starts with 'K').\n");
            prompt.append("  NEVER choose a character with a different starting sound!\n");
        }

        // Only show character priorities if we need to choose a character
        if (!sourceCharacter.isPresent()) {
            prompt.append("\nCHARACTER SELECTION PRIORITIES:\n");
            prompt.append("1. Anime Characters - MUST be from real shows (e.g., 'Aang from Avatar', 'Sasuke from Naruto')\n");
            prompt.append("2. Athletes - MUST be real (e.g., 'Kobe Bryant the NBA legend', 'Messi the soccer superstar')\n");
            prompt.append("3. Fictional Characters - MUST be from real media (e.g., 'Mario from Super Mario Bros', 'Harry Potter')\n");
            prompt.append("4. Filipino Celebrities - MUST be real (e.g., 'Manny Pacquiao the Filipino boxing champion')\n");
            prompt.append("5. Celebrities - MUST be real and well-known (e.g., 'Tom Cruise the Hollywood actor')\n");
            prompt.append("ALWAYS specify the character's origin. DO NOT create fictional characters. USE ONLY ONE CHARACTER TOTAL.\n");
        }

        prompt.append("\nCreate:\n");
        prompt.append("1. A 'mnemonic_keyword' - a VISUALIZABLE ENGLISH NOUN that sounds like '").append(sourceWord)
                .append("' but is easier to remember\n");
        prompt.append("   CRITICAL REQUIREMENTS for mnemonic_keyword:\n");
        prompt.append("   - MUST be an ENGLISH word (not in any other language)\n");
        prompt.append("   - MUST be a concrete, visualizable NOUN (something you can see/touch)\n");
        prompt.append("   - MUST NOT be the word '").append(sourceWord).append("' itself\n");
        prompt.append("   - MUST NOT be the word '").append(targetWord).append("' itself\n");
        prompt.append("   - Examples of GOOD keywords: 'apple', 'boat', 'crown', 'dragon', 'tree'\n");
        prompt.append("   - Examples of BAD keywords: verbs, adjectives, abstract concepts, the actual words being learned, or non-English words\n");
        prompt.append("2. A 'mnemonic_sentence' - a vivid, memorable sentence connecting the character")
                .append(" with the meaning '").append(targetWord).append("'\n");
        prompt.append("3. An 'image_prompt' - a detailed prompt for generating an image in REALISTIC CINEMATIC style showing:\n");
        prompt.append("   STYLE REQUIREMENT: The image MUST be described as 'Realistic Cinematic' style\n");
        prompt.append("   - ONLY ONE character (the one specified above) as the main focus\n");
        prompt.append("   - The mnemonic keyword represented VISUALLY through objects, actions, or scenery (NOT as text)\n");
        prompt.append("     Example: If keyword is 'sail', show the character sailing on a boat\n");
        prompt.append("   - Visual objects that represent the meaning '").append(targetWord).append("'\n");
        prompt.append("     Example: If meaning is 'year', show a calendar or clock\n");
        prompt.append("   - The character interacting with both the keyword object and the meaning object\n");
        prompt.append("   - Dynamic, vibrant atmosphere during golden hour\n");
        prompt.append("   CRITICAL: ABSOLUTELY NO text in the image prompt description:\n");
        prompt.append("   - NO book titles (e.g., NO 'book titled X', just 'book')\n");
        prompt.append("   - NO labels or signs with text\n");
        prompt.append("   - NO words in parentheses (e.g., NO '(").append(sourceWord).append(")')\n");
        prompt.append("   - NO speech bubbles or written words of any kind\n");
        prompt.append("   - NO captions or subtitles\n");
        prompt.append("   - Describe objects WITHOUT mentioning any text that would appear on them\n");
        prompt.append("   - NO additional people or characters beyond the one specified\n\n");

        prompt.append("Respond with valid JSON in this format:\n");
        prompt.append("{\n");
        prompt.append("  \"mnemonic_keyword\": \"...\",\n");
        prompt.append("  \"mnemonic_sentence\": \"...\",\n");
        prompt.append("  \"image_prompt\": \"...\"\n");
        prompt.append("}");

        return prompt.toString();
    }

    private String getLanguageName(String lang) {
        return switch (lang) {
            case "en" -> "English";
            case "es" -> "Spanish";
            case "fr" -> "French";
            case "cafr" -> "Canadian French";
            case "kr" -> "Korean";
            case "jp" -> "Japanese";
            case "hi" -> "Hindi";
            default -> "English";
        };
    }

    @Data
    private static class OpenAiRequest {
        private String model;
        private List<OpenAiMessage> messages;
        private Integer max_tokens;
        private Double temperature;
        private ResponseFormat response_format;

        public void setMaxTokens(Integer maxTokens) {
            this.max_tokens = maxTokens;
        }

        public void setResponseFormat(ResponseFormat responseFormat) {
            this.response_format = responseFormat;
        }
    }

    @Data
    private static class ResponseFormat {
        private String type;

        public ResponseFormat(String type) {
            this.type = type;
        }
    }

    @Data
    private static class OpenAiMessage {
        private String role;
        private String content;

        public OpenAiMessage() {}

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

    @Data
    public static class MnemonicData {
        private String mnemonic_keyword;
        private String mnemonic_sentence;
        private String image_prompt;

        // Convenience getters with camelCase
        public String getMnemonicKeyword() {
            return mnemonic_keyword;
        }

        public String getMnemonicSentence() {
            return mnemonic_sentence;
        }

        public String getImagePrompt() {
            return image_prompt;
        }
    }

    @Data
    public static class TranslationPair {
        private String sourceWord;
        private String targetWord;

        public TranslationPair() {}

        public TranslationPair(String sourceWord, String targetWord) {
            this.sourceWord = sourceWord;
            this.targetWord = targetWord;
        }
    }
}
