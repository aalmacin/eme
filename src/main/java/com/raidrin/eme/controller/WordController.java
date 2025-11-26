package com.raidrin.eme.controller;

import com.raidrin.eme.image.ImageProvider;
import com.raidrin.eme.image.ImageStyle;
import com.raidrin.eme.image.OpenAiImageService;
import com.raidrin.eme.mnemonic.MnemonicGenerationService;
import com.raidrin.eme.storage.entity.CharacterGuideEntity;
import com.raidrin.eme.storage.entity.WordEntity;
import com.raidrin.eme.storage.service.CharacterGuideService;
import com.raidrin.eme.storage.service.GcpStorageService;
import com.raidrin.eme.storage.service.WordService;
import com.raidrin.eme.translator.TranslationService;
import com.raidrin.eme.util.FileNameSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/words")
@RequiredArgsConstructor
public class WordController {

    private final WordService wordService;
    private final OpenAiImageService openAiImageService;
    private final GcpStorageService gcpStorageService;
    private final MnemonicGenerationService mnemonicGenerationService;
    private final CharacterGuideService characterGuideService;
    private final TranslationService translationService;

    @Value("${image.output.directory:./generated_images}")
    private String imageOutputDirectory;

    /**
     * Update image prompt and regenerate image for a word
     *
     * @param wordId The ID of the word to regenerate image for
     * @param request Optional request body with parameters:
     *                - imagePrompt: Custom prompt (overrides useSamePrompt)
     *                - useSamePrompt: Boolean, if true uses existing prompt instead of generating new one
     *                - model: "gpt-image-1-mini" or "gpt-image-1" (default: gpt-image-1-mini)
     */
    @PostMapping("/{wordId}/regenerate-image")
    public ResponseEntity<Map<String, Object>> regenerateImage(
            @PathVariable Long wordId,
            @RequestBody(required = false) Map<String, Object> request) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Find the word
            Optional<WordEntity> wordOpt = wordService.getAllWords().stream()
                    .filter(w -> w.getId().equals(wordId))
                    .findFirst();

            if (!wordOpt.isPresent()) {
                response.put("success", false);
                response.put("error", "Word not found");
                return ResponseEntity.notFound().build();
            }

            WordEntity word = wordOpt.get();

            // Parse parameters
            String customPrompt = null;
            Boolean useSamePrompt = false;
            String model = "gpt-image-1-mini"; // Default model
            ImageStyle imageStyle = ImageStyle.REALISTIC_CINEMATIC; // Default style

            if (request != null) {
                if (request.containsKey("imagePrompt")) {
                    customPrompt = (String) request.get("imagePrompt");
                }
                if (request.containsKey("useSamePrompt")) {
                    useSamePrompt = Boolean.parseBoolean(request.get("useSamePrompt").toString());
                }
                if (request.containsKey("model")) {
                    model = request.get("model").toString();
                    // Validate model
                    if (!"gpt-image-1-mini".equals(model) && !"gpt-image-1".equals(model)) {
                        response.put("success", false);
                        response.put("error", "Invalid model. Must be gpt-image-1-mini or gpt-image-1");
                        return ResponseEntity.badRequest().body(response);
                    }
                }
                if (request.containsKey("imageStyle")) {
                    String styleValue = request.get("imageStyle").toString();
                    imageStyle = ImageStyle.fromString(styleValue);
                }
            }

            // Determine the image prompt to use
            String imagePrompt;

            if (customPrompt != null && !customPrompt.trim().isEmpty()) {
                // Use custom prompt if provided
                imagePrompt = customPrompt;
            } else if (useSamePrompt) {
                // Reuse existing prompt
                imagePrompt = word.getImagePrompt();
                if (imagePrompt == null || imagePrompt.trim().isEmpty()) {
                    response.put("success", false);
                    response.put("error", "No existing prompt found. Please provide a custom prompt or disable useSamePrompt.");
                    return ResponseEntity.badRequest().body(response);
                }
                System.out.println("Reusing existing prompt for regeneration");
            } else {
                // Generate new prompt using mnemonic service
                String translation = null;
                if (word.getTranslation() != null) {
                    try {
                        Set<String> translations = wordService.deserializeTranslations(word.getTranslation());
                        if (!translations.isEmpty()) {
                            translation = translations.iterator().next();
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }

                if (translation == null) {
                    response.put("success", false);
                    response.put("error", "No translation available to generate prompt");
                    return ResponseEntity.badRequest().body(response);
                }

                // Generate mnemonic based on whether keyword was manually set
                String transliteration = word.getSourceTransliteration();
                MnemonicGenerationService.MnemonicData mnemonicData;
                String mnemonicKeywordToUse;

                // Check if mnemonic keyword was manually updated
                if (word.getMnemonicKeywordUpdatedAt() != null) {
                    // Keyword was manually set - generate sentence and prompt FROM the manual keyword
                    String manualKeyword = word.getMnemonicKeyword();
                    System.out.println("Preserving manually updated mnemonic keyword and regenerating from it: " + manualKeyword +
                            " (updated at: " + word.getMnemonicKeywordUpdatedAt() + ")");

                    mnemonicData = mnemonicGenerationService.generateMnemonicFromKeyword(
                            manualKeyword,
                            word.getWord(),
                            translation,
                            word.getSourceLanguage(),
                            word.getTargetLanguage(),
                            transliteration,
                            imageStyle
                    );
                    mnemonicKeywordToUse = manualKeyword;
                } else {
                    // No manual keyword - generate everything including a new keyword
                    mnemonicData = mnemonicGenerationService.generateMnemonic(
                            word.getWord(),
                            translation,
                            word.getSourceLanguage(),
                            word.getTargetLanguage(),
                            transliteration,
                            imageStyle
                    );
                    mnemonicKeywordToUse = mnemonicData.getMnemonicKeyword();
                }

                imagePrompt = mnemonicData.getImagePrompt();

                // Update mnemonic data and clear old image in a single transaction
                // (keyword will be null if manually set, preserving it)
                wordService.updateMnemonicAndClearImage(
                        word.getWord(),
                        word.getSourceLanguage(),
                        word.getTargetLanguage(),
                        word.getMnemonicKeywordUpdatedAt() != null ? null : mnemonicKeywordToUse,
                        mnemonicData.getMnemonicSentence(),
                        imagePrompt
                );
            }

            // Clear the old image if we didn't already do it above (when using same prompt)
            if (useSamePrompt) {
                wordService.updateImagePromptAndClearImage(wordId, imagePrompt);
            }

            // Sanitize the image prompt before sending to image generation API
            String sanitizedPrompt = mnemonicGenerationService.sanitizeImagePrompt(imagePrompt);
            System.out.println("Sanitized image prompt: " + sanitizedPrompt);

            // Generate new image using OpenAI with selected model
            String imageUrl;
            String revisedPrompt = null;

            System.out.println("Generating image with OpenAI model: " + model);

            OpenAiImageService.GeneratedImage openAiImage = openAiImageService.generateImage(sanitizedPrompt, model);
            imageUrl = openAiImage.getImageUrl();
            revisedPrompt = openAiImage.getRevisedPrompt();

            // Download image to local directory
            String imageFileName = FileNameSanitizer.fromMnemonicSentence(
                    word.getMnemonicSentence() != null ? word.getMnemonicSentence() : word.getWord(),
                    "jpg"
            );
            downloadImageToLocal(imageUrl, imageFileName);

            // Upload to GCP
            String gcsUrl = gcpStorageService.downloadAndUpload(imageUrl, imageFileName);

            // Update word with new image
            wordService.updateImage(word.getWord(), word.getSourceLanguage(), word.getTargetLanguage(),
                    imageFileName, imagePrompt);

            response.put("success", true);
            response.put("imageFile", imageFileName);
            response.put("imageUrl", gcsUrl);
            response.put("model", model);
            response.put("imageStyle", imageStyle.name());
            response.put("usedSamePrompt", useSamePrompt);

            if (revisedPrompt != null) {
                response.put("revisedPrompt", revisedPrompt);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Download an image file for a word
     */
    @GetMapping("/{wordId}/download-image")
    public ResponseEntity<Resource> downloadImage(@PathVariable Long wordId) {
        try {
            Optional<WordEntity> wordOpt = wordService.getAllWords().stream()
                    .filter(w -> w.getId().equals(wordId))
                    .findFirst();

            if (!wordOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            WordEntity word = wordOpt.get();
            if (word.getImageFile() == null) {
                return ResponseEntity.notFound().build();
            }

            Path imagePath = Paths.get(imageOutputDirectory, word.getImageFile());
            if (!Files.exists(imagePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(imagePath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + word.getImageFile() + "\"")
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get word details by ID
     */
    @GetMapping("/{wordId}")
    public ResponseEntity<WordEntity> getWord(@PathVariable Long wordId) {
        Optional<WordEntity> wordOpt = wordService.getAllWords().stream()
                .filter(w -> w.getId().equals(wordId))
                .findFirst();

        return wordOpt.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all words
     */
    @GetMapping
    public ResponseEntity<List<WordEntity>> getAllWords() {
        List<WordEntity> words = wordService.getAllWords();
        return ResponseEntity.ok(words);
    }

    /**
     * Create a new word
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createWord(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String word = (String) request.get("word");
            String sourceLanguage = (String) request.get("sourceLanguage");
            String targetLanguage = (String) request.get("targetLanguage");

            if (word == null || word.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Word is required");
                return ResponseEntity.badRequest().body(response);
            }

            if (sourceLanguage == null || sourceLanguage.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Source language is required");
                return ResponseEntity.badRequest().body(response);
            }

            if (targetLanguage == null || targetLanguage.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Target language is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Check if word already exists
            if (wordService.hasWord(word, sourceLanguage, targetLanguage)) {
                response.put("success", false);
                response.put("error", "Word already exists with this language pair");
                return ResponseEntity.badRequest().body(response);
            }

            WordEntity wordEntity = wordService.saveOrUpdateWord(word, sourceLanguage, targetLanguage);

            // Update optional fields if provided
            if (request.containsKey("translation")) {
                String translationStr = (String) request.get("translation");
                if (translationStr != null && !translationStr.trim().isEmpty()) {
                    Set<String> translations = new HashSet<>(Arrays.asList(translationStr.split(",")));
                    wordService.updateTranslation(word, sourceLanguage, targetLanguage, translations);
                }
            }

            String transliteration = null;
            if (request.containsKey("sourceTransliteration")) {
                transliteration = (String) request.get("sourceTransliteration");
                if (transliteration != null && !transliteration.trim().isEmpty()) {
                    wordService.updateTransliteration(word, sourceLanguage, targetLanguage, transliteration);
                }
            }

            // If no transliteration provided, get it from OpenAI
            if ((transliteration == null || transliteration.trim().isEmpty()) && !sourceLanguage.equals("en")) {
                try {
                    System.out.println("No transliteration provided, fetching from OpenAI for: " + word);
                    transliteration = translationService.getTransliteration(word, sourceLanguage);
                    if (transliteration != null && !transliteration.trim().isEmpty()) {
                        wordService.updateTransliteration(word, sourceLanguage, targetLanguage, transliteration);
                        System.out.println("Got transliteration from OpenAI: " + transliteration);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to get transliteration from OpenAI: " + e.getMessage());
                    // Continue without transliteration - it's not critical
                }
            }

            // Attach character guide if transliteration is available
            if (transliteration != null && !transliteration.trim().isEmpty()) {
                Optional<CharacterGuideEntity> characterGuide = characterGuideService.findMatchingCharacterForWord(
                        word, sourceLanguage, transliteration);
                if (characterGuide.isPresent()) {
                    wordService.updateCharacterGuide(word, sourceLanguage, targetLanguage,
                            characterGuide.get().getId());
                    System.out.println("Attached character guide: " + characterGuide.get().getCharacterName() +
                            " to word: " + word);
                }
            }

            // Set initial statuses
            wordService.updateImageStatus(word, sourceLanguage, targetLanguage, "PENDING");
            wordService.updateAudioStatus(word, sourceLanguage, targetLanguage, "PENDING");

            response.put("success", true);
            response.put("word", wordEntity);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Update an existing word
     */
    @PutMapping("/{wordId}")
    public ResponseEntity<Map<String, Object>> updateWord(
            @PathVariable Long wordId,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> response = new HashMap<>();

        try {
            Optional<WordEntity> wordOpt = wordService.getAllWords().stream()
                    .filter(w -> w.getId().equals(wordId))
                    .findFirst();

            if (!wordOpt.isPresent()) {
                response.put("success", false);
                response.put("error", "Word not found");
                return ResponseEntity.notFound().build();
            }

            WordEntity word = wordOpt.get();

            // Update translation if provided (with manual override flag since this is a direct user action)
            if (request.containsKey("translation")) {
                String translationStr = (String) request.get("translation");
                if (translationStr != null && !translationStr.trim().isEmpty()) {
                    Set<String> translations = new HashSet<>(Arrays.asList(translationStr.split(",")));
                    wordService.updateTranslationWithManualOverride(word.getWord(), word.getSourceLanguage(),
                            word.getTargetLanguage(), translations);
                }
            }

            // Update transliteration if provided
            if (request.containsKey("sourceTransliteration")) {
                String transliteration = (String) request.get("sourceTransliteration");
                wordService.updateTransliteration(word.getWord(), word.getSourceLanguage(),
                        word.getTargetLanguage(), transliteration);
            }

            // Update mnemonic fields if provided
            if (request.containsKey("mnemonicKeyword") || request.containsKey("mnemonicSentence")
                    || request.containsKey("imagePrompt")) {
                String mnemonicKeyword = (String) request.get("mnemonicKeyword");
                String mnemonicSentence = (String) request.get("mnemonicSentence");
                String imagePrompt = (String) request.get("imagePrompt");
                wordService.updateMnemonic(word.getWord(), word.getSourceLanguage(),
                        word.getTargetLanguage(), mnemonicKeyword, mnemonicSentence, imagePrompt);
            }

            response.put("success", true);
            response.put("message", "Word updated successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Update translation manually and regenerate mnemonic and image prompt
     */
    @PostMapping("/{wordId}/update-translation")
    public ResponseEntity<Map<String, Object>> updateTranslationAndRegenerate(
            @PathVariable Long wordId,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> response = new HashMap<>();

        try {
            Optional<WordEntity> wordOpt = wordService.getAllWords().stream()
                    .filter(w -> w.getId().equals(wordId))
                    .findFirst();

            if (!wordOpt.isPresent()) {
                response.put("success", false);
                response.put("error", "Word not found");
                return ResponseEntity.notFound().build();
            }

            WordEntity word = wordOpt.get();

            // Get new translation
            String translationStr = (String) request.get("translation");
            if (translationStr == null || translationStr.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Translation is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Validate that we're using the correct target language for translation
            // This ensures the translation matches the word's target language
            System.out.println("Updating translation for word: " + word.getWord() +
                    " from " + word.getSourceLanguage() + " to " + word.getTargetLanguage());

            // Update translation with manual override flag
            Set<String> translations = new HashSet<>(Arrays.asList(translationStr.split(",")));
            wordService.updateTranslationWithManualOverride(word.getWord(), word.getSourceLanguage(),
                    word.getTargetLanguage(), translations);

            // Get the first translation for mnemonic generation
            String translation = translations.iterator().next();

            // Get image style if provided
            ImageStyle imageStyle = ImageStyle.REALISTIC_CINEMATIC; // Default style
            if (request.containsKey("imageStyle")) {
                String styleValue = request.get("imageStyle").toString();
                imageStyle = ImageStyle.fromString(styleValue);
            }

            // Generate new mnemonic based on whether keyword was manually set
            String transliteration = word.getSourceTransliteration();
            MnemonicGenerationService.MnemonicData mnemonicData;
            String mnemonicKeywordToUse;

            // Check if mnemonic keyword was manually updated
            if (word.getMnemonicKeywordUpdatedAt() != null) {
                // Keyword was manually set - generate sentence and prompt FROM the manual keyword
                String manualKeyword = word.getMnemonicKeyword();
                System.out.println("Preserving manually updated mnemonic keyword and regenerating from it: " + manualKeyword +
                        " (updated at: " + word.getMnemonicKeywordUpdatedAt() + ")");

                mnemonicData = mnemonicGenerationService.generateMnemonicFromKeyword(
                        manualKeyword,
                        word.getWord(),
                        translation,
                        word.getSourceLanguage(),
                        word.getTargetLanguage(),
                        transliteration,
                        imageStyle
                );
                mnemonicKeywordToUse = manualKeyword;
            } else {
                // No manual keyword - generate everything including a new keyword
                mnemonicData = mnemonicGenerationService.generateMnemonic(
                        word.getWord(),
                        translation,
                        word.getSourceLanguage(),
                        word.getTargetLanguage(),
                        transliteration,
                        imageStyle
                );
                mnemonicKeywordToUse = mnemonicData.getMnemonicKeyword();
            }

            // Update mnemonic, image prompt, and clear old image in a single transaction
            // (keyword will be null if manually set, preserving it)
            wordService.updateMnemonicAndClearImage(
                    word.getWord(),
                    word.getSourceLanguage(),
                    word.getTargetLanguage(),
                    word.getMnemonicKeywordUpdatedAt() != null ? null : mnemonicKeywordToUse,
                    mnemonicData.getMnemonicSentence(),
                    mnemonicData.getImagePrompt()
            );

            response.put("success", true);
            response.put("message", "Translation updated and mnemonic regenerated");
            response.put("mnemonicKeyword", mnemonicKeywordToUse);
            response.put("mnemonicSentence", mnemonicData.getMnemonicSentence());
            response.put("imagePrompt", mnemonicData.getImagePrompt());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Update transliteration manually and re-attach character guide
     */
    @PostMapping("/{wordId}/update-transliteration")
    public ResponseEntity<Map<String, Object>> updateTransliteration(
            @PathVariable Long wordId,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> response = new HashMap<>();

        try {
            Optional<WordEntity> wordOpt = wordService.getAllWords().stream()
                    .filter(w -> w.getId().equals(wordId))
                    .findFirst();

            if (!wordOpt.isPresent()) {
                response.put("success", false);
                response.put("error", "Word not found");
                return ResponseEntity.notFound().build();
            }

            WordEntity word = wordOpt.get();

            // Get new transliteration
            String newTransliteration = (String) request.get("sourceTransliteration");
            if (newTransliteration == null || newTransliteration.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Transliteration is required");
                return ResponseEntity.badRequest().body(response);
            }

            System.out.println("Updating transliteration for word: " + word.getWord() +
                    " from '" + word.getSourceTransliteration() + "' to '" + newTransliteration + "'");

            // Update transliteration
            wordService.updateTransliteration(word.getWord(), word.getSourceLanguage(),
                    word.getTargetLanguage(), newTransliteration);

            // Re-attach character guide based on new transliteration
            Optional<CharacterGuideEntity> characterGuide = characterGuideService.findMatchingCharacterForWord(
                    word.getWord(), word.getSourceLanguage(), newTransliteration);
            if (characterGuide.isPresent()) {
                wordService.updateCharacterGuide(word.getWord(), word.getSourceLanguage(),
                        word.getTargetLanguage(), characterGuide.get().getId());
                System.out.println("Re-attached character guide: " + characterGuide.get().getCharacterName() +
                        " to word: " + word.getWord());
                response.put("characterGuide", characterGuide.get().getCharacterName());
            } else {
                // Clear character guide if no match found
                wordService.updateCharacterGuide(word.getWord(), word.getSourceLanguage(),
                        word.getTargetLanguage(), null);
                System.out.println("No character guide found for new transliteration: " + newTransliteration);
                response.put("characterGuide", null);
            }

            response.put("success", true);
            response.put("message", "Transliteration updated successfully");
            response.put("sourceTransliteration", newTransliteration);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Update mnemonic keyword manually and regenerate image prompt
     */
    @PostMapping("/{wordId}/update-mnemonic-keyword")
    public ResponseEntity<Map<String, Object>> updateMnemonicKeyword(
            @PathVariable Long wordId,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> response = new HashMap<>();

        try {
            Optional<WordEntity> wordOpt = wordService.getAllWords().stream()
                    .filter(w -> w.getId().equals(wordId))
                    .findFirst();

            if (!wordOpt.isPresent()) {
                response.put("success", false);
                response.put("error", "Word not found");
                return ResponseEntity.notFound().build();
            }

            WordEntity word = wordOpt.get();

            // Get new mnemonic keyword
            String newMnemonicKeyword = (String) request.get("mnemonicKeyword");
            if (newMnemonicKeyword == null || newMnemonicKeyword.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Mnemonic keyword is required");
                return ResponseEntity.badRequest().body(response);
            }

            System.out.println("Updating mnemonic keyword for word: " + word.getWord() +
                    " from '" + word.getMnemonicKeyword() + "' to '" + newMnemonicKeyword + "'");

            // Get translation for image prompt generation
            Set<String> translations = wordService.deserializeTranslations(word.getTranslation());
            String translation = translations != null && !translations.isEmpty()
                    ? translations.iterator().next()
                    : null;

            if (translation == null) {
                response.put("success", false);
                response.put("error", "No translation found for this word");
                return ResponseEntity.badRequest().body(response);
            }

            // Get image style if provided
            ImageStyle imageStyle = ImageStyle.REALISTIC_CINEMATIC; // Default style
            if (request.containsKey("imageStyle")) {
                String styleValue = request.get("imageStyle").toString();
                imageStyle = ImageStyle.fromString(styleValue);
            }

            // Generate new mnemonic sentence and image prompt FROM the user's keyword
            // This ensures the sentence and prompt are derived from the keyword and character guide
            String transliteration = word.getSourceTransliteration();
            MnemonicGenerationService.MnemonicData mnemonicData = mnemonicGenerationService.generateMnemonicFromKeyword(
                    newMnemonicKeyword,
                    word.getWord(),
                    translation,
                    word.getSourceLanguage(),
                    word.getTargetLanguage(),
                    transliteration,
                    imageStyle
            );

            // Update mnemonic keyword with manual override flag, new sentence, and new image prompt
            // All three are now derived from the user's keyword
            wordService.updateMnemonicKeywordWithManualOverride(
                    word.getWord(),
                    word.getSourceLanguage(),
                    word.getTargetLanguage(),
                    newMnemonicKeyword,
                    mnemonicData.getImagePrompt()
            );

            // Update mnemonic sentence
            wordService.updateMnemonic(
                    word.getWord(),
                    word.getSourceLanguage(),
                    word.getTargetLanguage(),
                    null, // Don't update keyword again
                    mnemonicData.getMnemonicSentence(),
                    null // Don't update image prompt again
            );

            // Clear the old image since we have a new prompt
            wordService.updateImagePromptAndClearImage(wordId, mnemonicData.getImagePrompt());

            response.put("success", true);
            response.put("message", "Mnemonic keyword updated and sentence/image prompt regenerated from keyword");
            response.put("mnemonicKeyword", newMnemonicKeyword);
            response.put("mnemonicSentence", mnemonicData.getMnemonicSentence());
            response.put("imagePrompt", mnemonicData.getImagePrompt());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Delete a word
     */
    @DeleteMapping("/{wordId}")
    public ResponseEntity<Map<String, Object>> deleteWord(@PathVariable Long wordId) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<WordEntity> wordOpt = wordService.getAllWords().stream()
                    .filter(w -> w.getId().equals(wordId))
                    .findFirst();

            if (!wordOpt.isPresent()) {
                response.put("success", false);
                response.put("error", "Word not found");
                return ResponseEntity.notFound().build();
            }

            WordEntity word = wordOpt.get();
            wordService.deleteWord(word.getWord(), word.getSourceLanguage(), word.getTargetLanguage());

            response.put("success", true);
            response.put("message", "Word deleted successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    private Path downloadImageToLocal(String imageUrl, String fileName) throws IOException {
        Files.createDirectories(Paths.get(imageOutputDirectory));
        Path outputPath = Paths.get(imageOutputDirectory, fileName);

        // Handle file:// URLs (local files from base64 conversion)
        if (imageUrl.startsWith("file://")) {
            Path sourcePath = Paths.get(java.net.URI.create(imageUrl));
            Files.copy(sourcePath, outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Copied local file from: " + sourcePath + " to: " + outputPath);
            return outputPath;
        }

        // Handle remote URLs (http/https)
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            URL url = new URL(imageUrl);
            url.openStream().transferTo(fos);
        }

        return outputPath;
    }
}
