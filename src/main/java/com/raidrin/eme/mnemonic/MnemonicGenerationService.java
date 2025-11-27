package com.raidrin.eme.mnemonic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raidrin.eme.image.ImageStyle;
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

import java.text.Normalizer;
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
     * @param imageStyle Style for image generation (defaults to REALISTIC_CINEMATIC if null)
     * @return MnemonicData containing keyword, sentence, and image prompt
     */
    public MnemonicData generateMnemonic(String sourceWord, String targetWord,
                                         String sourceLanguage, String targetLanguage,
                                         String sourceTransliteration, ImageStyle imageStyle) {

        // Default to REALISTIC_CINEMATIC if no style provided
        if (imageStyle == null) {
            imageStyle = ImageStyle.REALISTIC_CINEMATIC;
        }

        // Find character association for source word if transliteration is available
        Optional<CharacterGuideEntity> sourceCharacter = (sourceTransliteration != null && !sourceTransliteration.trim().isEmpty())
                ? characterGuideService.findMatchingCharacterForWord(sourceWord, sourceLanguage, sourceTransliteration)
                : Optional.empty();

        // FAIL if no character guide found
        if (sourceCharacter.isEmpty()) {
            if (sourceTransliteration == null || sourceTransliteration.trim().isEmpty()) {
                throw new RuntimeException("No transliteration provided for mnemonic generation: " + sourceWord);
            }
            // Determine what was searched (3, 2, or 1 chars)
            String strippedTransliteration = stripAccents(sourceTransliteration.toLowerCase().trim());
            String searchedPrefix = strippedTransliteration.length() >= 1 ? strippedTransliteration.substring(0, 1) : strippedTransliteration;
            throw new RuntimeException("No character guide found for '" + searchedPrefix + "' (from word: " + sourceWord + ", transliteration: " + sourceTransliteration + "). Please add a character guide for this sound.");
        }

        // Build the prompt
        String prompt = buildMnemonicPrompt(sourceWord, targetWord, sourceLanguage, targetLanguage,
                sourceCharacter, sourceTransliteration, imageStyle);

        System.out.println("Generating mnemonic with OpenAI for: " + sourceWord + " -> " + targetWord);

        // Call OpenAI API
        OpenAiRequest request = new OpenAiRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(
                new OpenAiMessage("system", "You are a mnemonic memory expert who creates vivid, memorable associations for language learning. You MUST use REAL, WELL-KNOWN characters from actual shows, movies, sports, or public life - NEVER create fictional characters. Always specify the character's origin (e.g., 'from Naruto', 'the NBA player'). Always respond with valid JSON only.\n\nIMPORTANT SAFETY GUIDELINES for image prompts:\n- Keep all content family-friendly and appropriate for all ages\n- Avoid violence, weapons, blood, injuries, or dangerous situations\n- Avoid suggestive, romantic, or intimate scenarios\n- Avoid controversial political or religious imagery\n- Avoid copyrighted brand names or logos\n- Focus on positive, educational, and wholesome scenes\n- Use everyday activities, nature scenes, and safe interactions"),
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

                // Validate the generated mnemonic data
                validateMnemonicData(mnemonicData, sourceWord, targetWord, sourceCharacter);

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
     * @param imageStyle Style for image generation (defaults to REALISTIC_CINEMATIC if null)
     * @return MnemonicData containing keyword, sentence, and image prompt
     */
    public MnemonicData generateMnemonic(String sourceWord, String targetWord,
                                         String sourceLanguage, String targetLanguage,
                                         ImageStyle imageStyle) {
        System.out.println("Warning: generateMnemonic called without transliteration, character matching will be skipped");
        return generateMnemonic(sourceWord, targetWord, sourceLanguage, targetLanguage, null, imageStyle);
    }

    /**
     * Generate mnemonic without transliteration and with default style (backward compatibility)
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
        return generateMnemonic(sourceWord, targetWord, sourceLanguage, targetLanguage, null, null);
    }

    /**
     * Generate mnemonic sentence and image prompt from an existing keyword
     * This is used when the keyword is manually set or when regenerating with the same keyword
     *
     * @param mnemonicKeyword The mnemonic keyword to base the generation on
     * @param sourceWord Word in source language
     * @param targetWord Word in target language (translation)
     * @param sourceLanguage Source language code
     * @param targetLanguage Target language code
     * @param sourceTransliteration Transliteration of source word (for character matching)
     * @param imageStyle Style for image generation
     * @return MnemonicData containing the provided keyword, generated sentence, and image prompt
     */
    public MnemonicData generateMnemonicFromKeyword(String mnemonicKeyword, String sourceWord, String targetWord,
                                                    String sourceLanguage, String targetLanguage,
                                                    String sourceTransliteration, ImageStyle imageStyle) {

        // Default to REALISTIC_CINEMATIC if no style provided
        if (imageStyle == null) {
            imageStyle = ImageStyle.REALISTIC_CINEMATIC;
        }

        // Find character association for source word if transliteration is available
        Optional<CharacterGuideEntity> sourceCharacter = (sourceTransliteration != null && !sourceTransliteration.trim().isEmpty())
                ? characterGuideService.findMatchingCharacterForWord(sourceWord, sourceLanguage, sourceTransliteration)
                : Optional.empty();

        // FAIL if no character guide found
        if (sourceCharacter.isEmpty()) {
            if (sourceTransliteration == null || sourceTransliteration.trim().isEmpty()) {
                throw new RuntimeException("No transliteration provided for mnemonic generation: " + sourceWord);
            }
            // Determine what was searched (3, 2, or 1 chars)
            String strippedTransliteration = stripAccents(sourceTransliteration.toLowerCase().trim());
            String searchedPrefix = strippedTransliteration.length() >= 1 ? strippedTransliteration.substring(0, 1) : strippedTransliteration;
            throw new RuntimeException("No character guide found for '" + searchedPrefix + "' (from word: " + sourceWord + ", transliteration: " + sourceTransliteration + "). Please add a character guide for this sound.");
        }

        // Build the prompt for generating sentence and image from keyword
        String prompt = buildMnemonicFromKeywordPrompt(mnemonicKeyword, sourceWord, targetWord,
                sourceLanguage, targetLanguage, sourceCharacter, sourceTransliteration, imageStyle);

        System.out.println("Generating mnemonic from keyword '" + mnemonicKeyword + "' for: " + sourceWord + " -> " + targetWord);

        // Call OpenAI API
        OpenAiRequest request = new OpenAiRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(
                new OpenAiMessage("system", "You are a mnemonic memory expert who creates vivid, memorable associations for language learning. You MUST use REAL, WELL-KNOWN characters from actual shows, movies, sports, or public life - NEVER create fictional characters. Always specify the character's origin. Always respond with valid JSON only.\n\nIMPORTANT SAFETY GUIDELINES for image prompts:\n- Keep all content family-friendly and appropriate for all ages\n- Avoid violence, weapons, blood, injuries, or dangerous situations\n- Avoid suggestive, romantic, or intimate scenarios\n- Avoid controversial political or religious imagery\n- Avoid copyrighted brand names or logos\n- Focus on positive, educational, and wholesome scenes\n- Use everyday activities, nature scenes, and safe interactions"),
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
                System.out.println("Mnemonic from keyword response: " + content);

                // Parse JSON response
                MnemonicData mnemonicData = objectMapper.readValue(content, MnemonicData.class);

                // Ensure the keyword matches the input (OpenAI should return it, but we enforce it)
                mnemonicData.mnemonic_keyword = mnemonicKeyword;

                // Validate the generated mnemonic data
                validateMnemonicData(mnemonicData, sourceWord, targetWord, sourceCharacter);

                return mnemonicData;
            } else {
                throw new RuntimeException("OpenAI API returned empty response");
            }
        } catch (Exception e) {
            System.err.println("Mnemonic from keyword generation error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to generate mnemonic from keyword: " + e.getMessage(), e);
        }
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

    private String buildMnemonicFromKeywordPrompt(String mnemonicKeyword, String sourceWord, String targetWord,
                                                  String sourceLanguage, String targetLanguage,
                                                  Optional<CharacterGuideEntity> sourceCharacter,
                                                  String sourceTransliteration, ImageStyle imageStyle) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("Create a mnemonic to remember that '").append(sourceWord).append("' (")
                .append(getLanguageName(sourceLanguage)).append(") means '").append(targetWord)
                .append("' (").append(getLanguageName(targetLanguage)).append(").\n\n");

        prompt.append("MNEMONIC KEYWORD PROVIDED: '").append(mnemonicKeyword).append("'\n");
        prompt.append("You MUST use this exact keyword - do NOT change it or create a new one!\n\n");

        // Character assignment section - USE ONLY ONE CHARACTER
        prompt.append("CHARACTER TO USE:\n");

        // Add character information if available
        if (sourceCharacter.isPresent()) {
            CharacterGuideEntity sc = sourceCharacter.get();
            prompt.append("- Use ONLY this character: ").append(sc.getCharacterName()).append(" from ").append(sc.getCharacterContext())
                    .append(" (REQUIRED - DO NOT CHANGE THIS CHARACTER)\n");
        } else {
            // Include transliteration if available
            if (sourceTransliteration != null && !sourceTransliteration.trim().isEmpty()) {
                prompt.append("- The word '").append(sourceWord).append("' is romanized as: ").append(sourceTransliteration).append("\n");
            }
            prompt.append("- CRITICAL CHARACTER SELECTION RULE: You MUST choose EXACTLY ONE REAL, WELL-KNOWN character whose name starts with the FIRST 2-3 LETTERS of the source word's ROMANIZATION.\n");
            prompt.append("  STEP 1: Determine the romanization (pronunciation in English letters) of '").append(sourceWord).append("'");
            if (sourceTransliteration != null && !sourceTransliteration.trim().isEmpty()) {
                prompt.append(" (provided above: ").append(sourceTransliteration).append(")");
            }
            prompt.append(".\n");
            prompt.append("  STEP 2: Try to find a character whose name starts with the FIRST 3 LETTERS of the romanization.\n");
            prompt.append("  STEP 3: If no 3-letter match exists, find a character whose name starts with the FIRST 2 LETTERS.\n");
            prompt.append("  IMPORTANT: DO NOT use the mnemonic keyword for character selection! The mnemonic keyword is ONLY for scene objects.\n");
            prompt.append("  NEVER choose a character with a different starting sound!\n");

            prompt.append("\nCHARACTER SELECTION PRIORITIES:\n");
            prompt.append("1. Anime Characters - MUST be from real shows\n");
            prompt.append("2. Athletes - MUST be real\n");
            prompt.append("3. Fictional Characters - MUST be from real media\n");
            prompt.append("4. Filipino Celebrities - MUST be real\n");
            prompt.append("5. Celebrities - MUST be real and well-known\n");
            prompt.append("ALWAYS specify the character's origin. USE ONLY ONE CHARACTER TOTAL.\n");
        }

        prompt.append("\nUsing the provided mnemonic keyword '").append(mnemonicKeyword).append("', create:\n");
        prompt.append("1. A 'mnemonic_keyword' field - you MUST return the EXACT keyword provided: '").append(mnemonicKeyword).append("'\n");
        prompt.append("2. A 'mnemonic_sentence' - A SIMPLE, MEMORABLE sentence that can be easily remembered and visualized\n");
        prompt.append("   REQUIRED STRUCTURE:\n");
        prompt.append("   - Character from character guide + their context (e.g., '").append(sourceCharacter.isPresent() ? sourceCharacter.get().getCharacterName() : "the character").append("')\n");
        prompt.append("   - Mnemonic keyword '").append(mnemonicKeyword).append("' incorporated as object/action\n");
        prompt.append("   - 2-10 additional items/characters/actions that sound like OR start with same letter as first syllable/letter of EITHER source OR translation\n");
        prompt.append("   - Setting that sounds like source or translation\n");
        prompt.append("   - MUST include facial and body expressions (e.g., 'put their index finger horizontally under her nostrils to cover smell')\n");
        prompt.append("   - Keep it SIMPLE and MEMORABLE (one sentence that connects all elements)\n");
        prompt.append("   - Creates memorable link: sound ('").append(sourceWord).append("') -> keyword ('").append(mnemonicKeyword).append("') -> meaning ('").append(targetWord).append("')\n");
        prompt.append("3. An 'image_prompt' - a detailed prompt for generating an image in ").append(imageStyle.getDisplayName().toUpperCase()).append(" style.\n\n");
        prompt.append("   === IMAGE FOCUS: TRANSLATION MEANING ===\n");
        prompt.append("   The image MUST FOCUS on the TRANSLATION '").append(targetWord).append("' meaning.\n");
        prompt.append("   The character should be performing an action or in a scene that demonstrates the translation meaning.\n");
        prompt.append("   Example: For 'to become', show ").append(sourceCharacter.isPresent() ? sourceCharacter.get().getCharacterName() : "the character").append(" becoming something (e.g., becoming a leader, transformation)\n\n");
        prompt.append("   === MANDATORY REQUIREMENTS - ALL ELEMENTS MUST BE PRESENT ===\n");
        prompt.append("   The image MUST include:\n");
        prompt.append("   1️⃣ CHARACTER: ").append(sourceCharacter.isPresent() ? sourceCharacter.get().getCharacterName() + " from " + sourceCharacter.get().getCharacterContext() : "character matching the word's sound").append("\n");
        prompt.append("   2️⃣ MNEMONIC KEYWORD: '").append(mnemonicKeyword).append("' represented VISUALLY through objects in the scene\n");
        prompt.append("      Example: If keyword is 'leaf', include leaves or leafy elements in the scene\n");
        prompt.append("   3️⃣ ADDITIONAL ITEMS: 2-10 items that match the phonetic sound or starting letter of source OR translation\n");
        prompt.append("      Example: For 'liye' → 'for', include 'foreman', 'ford', '4', etc.\n");
        prompt.append("   4️⃣ SETTING: Environment that sounds like source or translation\n");
        prompt.append("   5️⃣ FACIAL & BODY EXPRESSIONS: Show clear emotions and body language\n");
        prompt.append("      Example: 'smiling while holding', 'index finger under nostrils', 'hopeful gesture with hands together'\n");
        prompt.append("   ================================================================\n\n");
        prompt.append("   STYLE REQUIREMENT: The image MUST be described as '").append(imageStyle.getDisplayName()).append("' style\n");
        prompt.append("   COMPOSITION:\n");
        prompt.append("   - The character is the main focus performing the translation action/meaning\n");
        prompt.append("   - Include mnemonic keyword '").append(mnemonicKeyword).append("' object visible in the scene\n");
        prompt.append("   - Include 2-10 additional items for memory anchoring\n");
        prompt.append("   - Dynamic, vibrant atmosphere\n");
        prompt.append("   - NO additional people or characters beyond the one specified\n");
        prompt.append("   CRITICAL: ABSOLUTELY NO text in the image:\n");
        prompt.append("   - NO words, labels, signs, or captions anywhere\n");
        prompt.append("   - Source word '").append(sourceWord).append("' MUST NEVER appear in the image\n");
        prompt.append("   - NO speech bubbles or written words of any kind\n");
        prompt.append("   SAFETY REQUIREMENTS for image_prompt:\n");
        prompt.append("   - Keep the scene family-friendly and appropriate for all ages\n");
        prompt.append("   - NO violence, weapons, fighting, blood, or injuries\n");
        prompt.append("   - NO suggestive poses, romantic scenarios, or intimate situations\n");
        prompt.append("   - Focus on safe, positive, everyday activities and interactions\n");
        prompt.append("   - Character should be engaged in wholesome, educational activities\n\n");

        prompt.append("Respond with valid JSON in this format:\n");
        prompt.append("{\n");
        prompt.append("  \"mnemonic_keyword\": \"").append(mnemonicKeyword).append("\",\n");
        prompt.append("  \"mnemonic_sentence\": \"...\",\n");
        prompt.append("  \"image_prompt\": \"...\"\n");
        prompt.append("}");

        return prompt.toString();
    }

    private String buildMnemonicPrompt(String sourceWord, String targetWord,
                                       String sourceLanguage, String targetLanguage,
                                       Optional<CharacterGuideEntity> sourceCharacter,
                                       String sourceTransliteration, ImageStyle imageStyle) {

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
            // Include transliteration if available
            if (sourceTransliteration != null && !sourceTransliteration.trim().isEmpty()) {
                prompt.append("- The word '").append(sourceWord).append("' is romanized as: ").append(sourceTransliteration).append("\n");
            }
            prompt.append("- CRITICAL CHARACTER SELECTION RULE: You MUST choose EXACTLY ONE REAL, WELL-KNOWN character whose name starts with the FIRST 2-3 LETTERS of the source word's ROMANIZATION.\n");
            prompt.append("  STEP 1: Determine the romanization (pronunciation in English letters) of '").append(sourceWord).append("'");
            if (sourceTransliteration != null && !sourceTransliteration.trim().isEmpty()) {
                prompt.append(" (provided above: ").append(sourceTransliteration).append(")");
            }
            prompt.append(".\n");
            prompt.append("  STEP 2: Try to find a character whose name starts with the FIRST 3 LETTERS of the romanization.\n");
            prompt.append("  STEP 3: If no 3-letter match exists, find a character whose name starts with the FIRST 2 LETTERS.\n");
            prompt.append("  Example: If romanization is 'motsu', look for characters starting with 'mot' first (e.g., 'Motoko from Ghost in the Shell').\n");
            prompt.append("  Example: If no 'mot' match, look for 'mo' (e.g., 'Monkey D. Luffy from One Piece' starts with 'mo').\n");
            prompt.append("  IMPORTANT: DO NOT use the mnemonic keyword for character selection! The mnemonic keyword is ONLY for scene objects.\n");
            prompt.append("  Example: If the word is 'motsu' (romanized), DO NOT choose 'Matsuno' just because the keyword is 'mat'.\n");
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
        prompt.append("1. A 'mnemonic_keyword' - a VISUALIZABLE object/action that sounds like '").append(sourceWord)
                .append("' (based on '").append(sourceTransliteration != null ? sourceTransliteration : sourceWord).append("')\n");
        prompt.append("   PHONETIC MATCHING PRIORITY (in order):\n");
        prompt.append("   a) Match first syllable SOUND of source (e.g., 'liye' → 'leaf', 'li' sound)\n");
        prompt.append("   b) Match first syllable TEXT characters (e.g., 'ma' → 'mat')\n");
        prompt.append("   c) Match first LETTER (e.g., 'm' → 'moon')\n");
        prompt.append("   CRITICAL REQUIREMENTS for mnemonic_keyword:\n");
        prompt.append("   - MUST be phonetically similar to SOURCE word (not translation!)\n");
        prompt.append("   - MUST be a concrete, VISUALIZABLE object (noun) or VISUALIZABLE/GESTURABLE action (verb/adjective)\n");
        prompt.append("   - CANNOT be abstract concepts (e.g., 'hoping' is OK if visualized as hopeful gesture, but 'hope' alone is too abstract)\n");
        prompt.append("   - MUST NOT be the word '").append(sourceWord).append("' itself\n");
        prompt.append("   - MUST NOT be the word '").append(targetWord).append("' itself\n");
        prompt.append("   - Examples of GOOD keywords: 'leaf' (concrete object), 'hoping' (visualizable gesture), 'mat' (object), 'mall' (place)\n");
        prompt.append("   - Examples of BAD keywords: abstract nouns, non-visualizable concepts, words that don't match source phonetically\n");
        prompt.append("2. A 'mnemonic_sentence' - A SIMPLE, MEMORABLE sentence that can be easily remembered and visualized\n");
        prompt.append("   REQUIRED STRUCTURE:\n");
        prompt.append("   - Character from character guide + their context (e.g., 'Lisa Soberano')\n");
        prompt.append("   - Mnemonic keyword object/action (e.g., '4 leaf clover')\n");
        prompt.append("   - 2-10 additional items/characters/actions that sound like OR start with same letter as first syllable/letter of EITHER source OR translation\n");
        prompt.append("   - Setting that sounds like source or translation (e.g., 'leafy forest' for 'liye')\n");
        prompt.append("   - MUST include facial and body expressions (e.g., 'put their index finger horizontally under her nostrils to cover smell')\n");
        prompt.append("   - Keep it SIMPLE and MEMORABLE (one sentence that connects all elements)\n");
        prompt.append("   Example for 'liye' → 'for': 'Lisa Soberano gave 4 leaf clover for the foreman who has a ford, in a leafy forest.'\n");
        prompt.append("3. An 'image_prompt' - a detailed prompt for generating an image in ").append(imageStyle.getDisplayName().toUpperCase()).append(" style.\n\n");
        prompt.append("   === IMAGE FOCUS: TRANSLATION MEANING ===\n");
        prompt.append("   The image MUST FOCUS on the TRANSLATION '").append(targetWord).append("' meaning.\n");
        prompt.append("   The character should be performing an action or in a scene that demonstrates the translation meaning.\n");
        prompt.append("   Example: For 'to become', show ").append(sourceCharacter.isPresent() ? sourceCharacter.get().getCharacterName() : "the character").append(" becoming something (e.g., becoming a leader, transformation)\n\n");
        prompt.append("   === MANDATORY REQUIREMENTS - ALL ELEMENTS MUST BE PRESENT ===\n");
        prompt.append("   The image MUST include:\n");
        prompt.append("   1️⃣ CHARACTER: ").append(sourceCharacter.isPresent() ? sourceCharacter.get().getCharacterName() + " from " + sourceCharacter.get().getCharacterContext() : "character matching the word's sound").append("\n");
        prompt.append("   2️⃣ MNEMONIC KEYWORD: The keyword represented VISUALLY through objects in the scene\n");
        prompt.append("      Example: If keyword is 'leaf', include leaves or leafy elements in the scene\n");
        prompt.append("   3️⃣ ADDITIONAL ITEMS: 2-10 items that match the phonetic sound or starting letter of source OR translation\n");
        prompt.append("      Example: For 'liye' → 'for', include 'foreman', 'ford', '4', etc.\n");
        prompt.append("   4️⃣ SETTING: Environment that sounds like source or translation\n");
        prompt.append("      Example: 'leafy forest' for 'liye'\n");
        prompt.append("   5️⃣ FACIAL & BODY EXPRESSIONS: Show clear emotions and body language\n");
        prompt.append("      Example: 'smiling while holding', 'index finger under nostrils', 'hopeful gesture with hands together'\n");
        prompt.append("   ================================================================\n\n");
        prompt.append("   STYLE REQUIREMENT: The image MUST be described as '").append(imageStyle.getDisplayName()).append("' style\n");
        prompt.append("   COMPOSITION:\n");
        prompt.append("   - The character is the main focus performing the translation action/meaning\n");
        prompt.append("   - Include mnemonic keyword object visible in the scene\n");
        prompt.append("   - Include 2-10 additional items for memory anchoring\n");
        prompt.append("   - Dynamic, vibrant atmosphere\n");
        prompt.append("   - NO additional people or characters beyond the one specified\n");
        prompt.append("   CRITICAL: ABSOLUTELY NO text in the image:\n");
        prompt.append("   - NO words, labels, signs, or captions anywhere\n");
        prompt.append("   - Source word '").append(sourceWord).append("' MUST NEVER appear in the image\n");
        prompt.append("   - NO speech bubbles or written words of any kind\n");
        prompt.append("   - Describe objects WITHOUT mentioning any text that would appear on them\n");
        prompt.append("   SAFETY REQUIREMENTS for image_prompt:\n");
        prompt.append("   - Keep the scene family-friendly and appropriate for all ages\n");
        prompt.append("   - NO violence, weapons, fighting, blood, or injuries\n");
        prompt.append("   - NO suggestive poses, romantic scenarios, or intimate situations\n");
        prompt.append("   - Focus on safe, positive, everyday activities and interactions\n");
        prompt.append("   - Character should be engaged in wholesome, educational activities\n\n");

        prompt.append("Respond with valid JSON in this format:\n");
        prompt.append("{\n");
        prompt.append("  \"mnemonic_keyword\": \"...\",\n");
        prompt.append("  \"mnemonic_sentence\": \"...\",\n");
        prompt.append("  \"image_prompt\": \"...\"\n");
        prompt.append("}");

        return prompt.toString();
    }

    /**
     * Sanitize image prompt to ensure it's safe for image generation APIs
     * This removes potentially problematic words and phrases that might trigger safety systems
     */
    public String sanitizeImagePrompt(String imagePrompt) {
        if (imagePrompt == null || imagePrompt.trim().isEmpty()) {
            return imagePrompt;
        }

        String sanitized = imagePrompt;

        // List of potentially problematic words/phrases to filter out or replace
        // Violence-related
        sanitized = sanitized.replaceAll("(?i)\\b(weapon|gun|knife|sword|blade|blood|violence|fight|fighting|attack|kill|death|dead|murder|war|battle|combat|injury|hurt|wound|dangerous)\\b", "item");

        // Inappropriate/suggestive content
        sanitized = sanitized.replaceAll("(?i)\\b(sexy|sensual|seductive|romantic|intimate|kiss|embrace|hug|love|dating|flirt)\\b", "friendly");

        // Political/controversial
        sanitized = sanitized.replaceAll("(?i)\\b(political|politics|politician|election|vote|protest|rally|demonstration)\\b", "gathering");

        // Religious (to avoid potential controversies)
        sanitized = sanitized.replaceAll("(?i)\\b(religious|religion|worship|pray|prayer|sacred|holy|divine|god|goddess|deity)\\b", "peaceful");

        // Alcohol/drugs
        sanitized = sanitized.replaceAll("(?i)\\b(alcohol|beer|wine|liquor|drunk|drug|smoking|cigarette|cigar|tobacco)\\b", "beverage");

        // Negative emotions/scenarios that might be problematic
        sanitized = sanitized.replaceAll("(?i)\\b(scary|horror|terrifying|nightmare|fear|afraid|panic|scream|crying|sad|depressed)\\b", "surprised");

        // Clean up any multiple spaces created by replacements
        sanitized = sanitized.replaceAll("\\s+", " ").trim();

        // Add safety prefix to guide the image generation model
        String safetyPrefix = "Family-friendly educational image: ";
        sanitized = safetyPrefix + sanitized;

        // Log if changes were made
        if (!sanitized.equals(imagePrompt)) {
            System.out.println("Image prompt was sanitized:");
            System.out.println("  Original: " + imagePrompt);
            System.out.println("  Sanitized: " + sanitized);
        }

        return sanitized;
    }

    /**
     * Validate that the generated mnemonic data contains all required elements
     */
    private void validateMnemonicData(MnemonicData mnemonicData, String sourceWord, String targetWord,
                                      Optional<CharacterGuideEntity> sourceCharacter) {
        StringBuilder validationIssues = new StringBuilder();

        // Validate mnemonic keyword is present
        if (mnemonicData.getMnemonicKeyword() == null || mnemonicData.getMnemonicKeyword().trim().isEmpty()) {
            validationIssues.append("⚠️ WARNING: Mnemonic keyword is missing!\n");
        } else {
            System.out.println("✓ Mnemonic keyword present: " + mnemonicData.getMnemonicKeyword());
        }

        // Validate mnemonic sentence is present
        if (mnemonicData.getMnemonicSentence() == null || mnemonicData.getMnemonicSentence().trim().isEmpty()) {
            validationIssues.append("⚠️ WARNING: Mnemonic sentence is missing!\n");
        } else {
            System.out.println("✓ Mnemonic sentence present");
        }

        // Validate image prompt is present
        if (mnemonicData.getImagePrompt() == null || mnemonicData.getImagePrompt().trim().isEmpty()) {
            validationIssues.append("⚠️ WARNING: Image prompt is missing!\n");
        } else {
            String imagePrompt = mnemonicData.getImagePrompt().toLowerCase();

            // Check if character is mentioned in image prompt
            if (sourceCharacter.isPresent()) {
                String characterName = sourceCharacter.get().getCharacterName().toLowerCase();
                if (!imagePrompt.contains(characterName)) {
                    validationIssues.append("⚠️ WARNING: Character '")
                            .append(sourceCharacter.get().getCharacterName())
                            .append("' from character guide is NOT mentioned in image prompt!\n");
                    validationIssues.append("   Expected character: ")
                            .append(sourceCharacter.get().getCharacterName())
                            .append(" from ")
                            .append(sourceCharacter.get().getCharacterContext())
                            .append("\n");
                } else {
                    System.out.println("✓ Character from guide found in prompt: " + sourceCharacter.get().getCharacterName());
                }
            } else {
                System.out.println("ℹ️ INFO: No character guide entry found, OpenAI will choose character based on transliteration");
            }

            // Check if mnemonic keyword is likely represented (keyword should influence the scene)
            String keyword = mnemonicData.getMnemonicKeyword();
            if (keyword != null && !keyword.trim().isEmpty()) {
                // The keyword might not be directly mentioned (it's visual), but let's check
                if (imagePrompt.contains(keyword.toLowerCase())) {
                    System.out.println("✓ Mnemonic keyword found in prompt: " + keyword);
                } else {
                    System.out.println("ℹ️ INFO: Mnemonic keyword '" + keyword + "' not explicitly in prompt (might be represented visually)");
                }
            }

            // Check if translation/meaning is likely represented
            if (targetWord != null && !targetWord.trim().isEmpty()) {
                if (imagePrompt.contains(targetWord.toLowerCase())) {
                    System.out.println("✓ Translation/meaning found in prompt: " + targetWord);
                } else {
                    System.out.println("ℹ️ INFO: Translation '" + targetWord + "' not explicitly in prompt (might be represented visually)");
                }
            }

            System.out.println("✓ Image prompt present: " + mnemonicData.getImagePrompt().substring(0, Math.min(100, mnemonicData.getImagePrompt().length())) + "...");
        }

        // Log any validation issues
        if (validationIssues.length() > 0) {
            System.err.println("\n========== MNEMONIC VALIDATION WARNINGS ==========");
            System.err.println("Source word: " + sourceWord + " -> Translation: " + targetWord);
            System.err.println(validationIssues.toString());
            System.err.println("Full mnemonic data:");
            System.err.println("  Keyword: " + mnemonicData.getMnemonicKeyword());
            System.err.println("  Sentence: " + mnemonicData.getMnemonicSentence());
            System.err.println("  Image prompt: " + mnemonicData.getImagePrompt());
            System.err.println("==================================================\n");
        } else {
            System.out.println("✓ All mnemonic data validation passed for: " + sourceWord + " -> " + targetWord);
        }
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

    /**
     * Strip accents from text (e.g., mā -> ma, é -> e, ñ -> n)
     */
    private String stripAccents(String text) {
        if (text == null) {
            return null;
        }

        // Normalize to NFD (decomposed form) and remove diacritical marks
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        // Remove all combining diacritical marks (accents)
        String stripped = normalized.replaceAll("\\p{M}", "");

        return stripped;
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
