package com.raidrin.eme.controller;

import com.raidrin.eme.image.ImageProvider;
import com.raidrin.eme.image.ImageStyle;
import com.raidrin.eme.image.OpenAiImageService;
import com.raidrin.eme.mnemonic.MnemonicGenerationService;
import com.raidrin.eme.storage.entity.*;
import com.raidrin.eme.storage.service.CharacterGuideService;
import com.raidrin.eme.storage.service.GcpStorageService;
import com.raidrin.eme.storage.service.WordService;
import com.raidrin.eme.storage.service.WordVariantService;
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
    private final WordVariantService wordVariantService;
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

            // Get image style from the word entity, falling back to default if not set
            ImageStyle imageStyle = word.getImageStyle() != null
                    ? ImageStyle.fromString(word.getImageStyle())
                    : ImageStyle.REALISTIC_CINEMATIC;

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

            // Get image style from the word entity, falling back to default if not set
            ImageStyle imageStyle = word.getImageStyle() != null
                    ? ImageStyle.fromString(word.getImageStyle())
                    : ImageStyle.REALISTIC_CINEMATIC;

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

            // Update transliteration with manual override flag
            wordService.updateTransliterationWithManualOverride(word.getWord(), word.getSourceLanguage(),
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

            // Get image style from the word entity, falling back to default if not set
            ImageStyle imageStyle = word.getImageStyle() != null
                    ? ImageStyle.fromString(word.getImageStyle())
                    : ImageStyle.REALISTIC_CINEMATIC;

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

    // ==================== Image Prompt Management Endpoints ====================

    /**
     * Update image prompt for a word (for editing prompts before image generation)
     */
    @PostMapping("/{wordId}/update-image-prompt")
    public ResponseEntity<Map<String, Object>> updateImagePrompt(
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

            // Get new image prompt
            String newImagePrompt = (String) request.get("imagePrompt");
            if (newImagePrompt == null || newImagePrompt.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Image prompt is required");
                return ResponseEntity.badRequest().body(response);
            }

            System.out.println("Updating image prompt for word: " + word.getWord());

            // Update image prompt and set status to GENERATED (edited prompts need re-approval)
            wordService.updateImagePromptWithStatus(
                word.getWord(),
                word.getSourceLanguage(),
                word.getTargetLanguage(),
                newImagePrompt,
                "GENERATED"
            );

            response.put("success", true);
            response.put("message", "Image prompt updated successfully");
            response.put("imagePrompt", newImagePrompt);
            response.put("imagePromptStatus", "GENERATED");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Approve image prompt for a word (makes it ready for image generation)
     */
    @PostMapping("/{wordId}/approve-image-prompt")
    public ResponseEntity<Map<String, Object>> approveImagePrompt(@PathVariable Long wordId) {

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

            // Check if word has an image prompt
            if (word.getImagePrompt() == null || word.getImagePrompt().trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "No image prompt to approve");
                return ResponseEntity.badRequest().body(response);
            }

            System.out.println("Approving image prompt for word: " + word.getWord());

            // Set status to APPROVED
            wordService.updateImagePromptStatus(
                word.getWord(),
                word.getSourceLanguage(),
                word.getTargetLanguage(),
                "APPROVED"
            );

            response.put("success", true);
            response.put("message", "Image prompt approved");
            response.put("imagePromptStatus", "APPROVED");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Reject image prompt for a word (requires regeneration)
     */
    @PostMapping("/{wordId}/reject-image-prompt")
    public ResponseEntity<Map<String, Object>> rejectImagePrompt(@PathVariable Long wordId) {

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

            System.out.println("Rejecting image prompt for word: " + word.getWord());

            // Set status to REJECTED
            wordService.updateImagePromptStatus(
                word.getWord(),
                word.getSourceLanguage(),
                word.getTargetLanguage(),
                "REJECTED"
            );

            response.put("success", true);
            response.put("message", "Image prompt rejected");
            response.put("imagePromptStatus", "REJECTED");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Approve all image prompts in a batch (by word IDs)
     */
    @PostMapping("/batch/approve-image-prompts")
    public ResponseEntity<Map<String, Object>> batchApproveImagePrompts(
            @RequestBody Map<String, Object> request) {

        Map<String, Object> response = new HashMap<>();

        try {
            @SuppressWarnings("unchecked")
            List<Number> wordIds = (List<Number>) request.get("wordIds");

            if (wordIds == null || wordIds.isEmpty()) {
                response.put("success", false);
                response.put("error", "No word IDs provided");
                return ResponseEntity.badRequest().body(response);
            }

            int approvedCount = 0;
            int skippedCount = 0;
            List<String> errors = new ArrayList<>();

            for (Number wordIdNum : wordIds) {
                Long wordId = wordIdNum.longValue();
                try {
                    Optional<WordEntity> wordOpt = wordService.getAllWords().stream()
                            .filter(w -> w.getId().equals(wordId))
                            .findFirst();

                    if (wordOpt.isEmpty()) {
                        skippedCount++;
                        continue;
                    }

                    WordEntity word = wordOpt.get();
                    if (word.getImagePrompt() == null || word.getImagePrompt().trim().isEmpty()) {
                        skippedCount++;
                        continue;
                    }

                    wordService.updateImagePromptStatus(
                        word.getWord(),
                        word.getSourceLanguage(),
                        word.getTargetLanguage(),
                        "APPROVED"
                    );
                    approvedCount++;

                } catch (Exception e) {
                    errors.add("Word " + wordId + ": " + e.getMessage());
                }
            }

            response.put("success", true);
            response.put("approvedCount", approvedCount);
            response.put("skippedCount", skippedCount);
            if (!errors.isEmpty()) {
                response.put("errors", errors);
            }
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== Variant Management Endpoints ====================

    /**
     * Get all image variants for a word
     */
    @GetMapping("/{wordId}/variants/images")
    public ResponseEntity<List<Map<String, Object>>> getImageVariants(@PathVariable Long wordId) {
        List<WordImageEntity> images = wordVariantService.getImageHistory(wordId);
        List<Map<String, Object>> result = images.stream().map(img -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", img.getId());
            map.put("imageFile", img.getImageFile());
            map.put("imageGcsUrl", img.getImageGcsUrl());
            map.put("imagePrompt", img.getImagePrompt());
            map.put("imageStyle", img.getImageStyle());
            map.put("isCurrent", img.getIsCurrent());
            map.put("createdAt", img.getCreatedAt() != null ? img.getCreatedAt().toString() : null);
            return map;
        }).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Set current image variant
     */
    @PostMapping("/{wordId}/variants/images/{imageId}/set-current")
    public ResponseEntity<Map<String, Object>> setCurrentImage(
            @PathVariable Long wordId,
            @PathVariable Long imageId) {
        Map<String, Object> response = new HashMap<>();
        try {
            wordVariantService.setCurrentImage(wordId, imageId);

            // Also update the legacy field on WordEntity
            WordImageEntity image = wordVariantService.getCurrentImage(wordId).orElse(null);
            if (image != null) {
                Optional<WordEntity> wordOpt = wordService.getAllWords().stream()
                        .filter(w -> w.getId().equals(wordId))
                        .findFirst();
                if (wordOpt.isPresent()) {
                    WordEntity word = wordOpt.get();
                    wordService.updateImage(word.getWord(), word.getSourceLanguage(),
                            word.getTargetLanguage(), image.getImageFile(), image.getImagePrompt());
                }
            }

            response.put("success", true);
            response.put("message", "Current image updated");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get all mnemonic variants for a word
     */
    @GetMapping("/{wordId}/variants/mnemonics")
    public ResponseEntity<List<Map<String, Object>>> getMnemonicVariants(@PathVariable Long wordId) {
        List<WordMnemonicEntity> mnemonics = wordVariantService.getMnemonicHistory(wordId);
        List<Map<String, Object>> result = mnemonics.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("mnemonicKeyword", m.getMnemonicKeyword());
            map.put("mnemonicSentence", m.getMnemonicSentence());
            map.put("isCurrent", m.getIsCurrent());
            map.put("isUserCreated", m.getIsUserCreated());
            map.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
            if (m.getCharacterGuide() != null) {
                map.put("characterGuideName", m.getCharacterGuide().getCharacterName());
            }
            return map;
        }).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Set current mnemonic variant
     */
    @PostMapping("/{wordId}/variants/mnemonics/{mnemonicId}/set-current")
    public ResponseEntity<Map<String, Object>> setCurrentMnemonic(
            @PathVariable Long wordId,
            @PathVariable Long mnemonicId) {
        Map<String, Object> response = new HashMap<>();
        try {
            wordVariantService.setCurrentMnemonic(wordId, mnemonicId);

            // Also update the legacy field on WordEntity
            WordMnemonicEntity mnemonic = wordVariantService.getCurrentMnemonic(wordId).orElse(null);
            if (mnemonic != null) {
                Optional<WordEntity> wordOpt = wordService.getAllWords().stream()
                        .filter(w -> w.getId().equals(wordId))
                        .findFirst();
                if (wordOpt.isPresent()) {
                    WordEntity word = wordOpt.get();
                    wordService.updateMnemonic(word.getWord(), word.getSourceLanguage(),
                            word.getTargetLanguage(), mnemonic.getMnemonicKeyword(),
                            mnemonic.getMnemonicSentence(), null);
                }
            }

            response.put("success", true);
            response.put("message", "Current mnemonic updated");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get all translation variants for a word
     */
    @GetMapping("/{wordId}/variants/translations")
    public ResponseEntity<List<Map<String, Object>>> getTranslationVariants(@PathVariable Long wordId) {
        List<WordTranslationEntity> translations = wordVariantService.getTranslationHistory(wordId);
        List<Map<String, Object>> result = translations.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", t.getId());
            map.put("translation", t.getTranslation());
            map.put("transliteration", t.getTransliteration());
            map.put("source", t.getSource());
            map.put("isCurrent", t.getIsCurrent());
            map.put("isUserCreated", t.getIsUserCreated());
            map.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
            return map;
        }).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Set current translation variant
     */
    @PostMapping("/{wordId}/variants/translations/{translationId}/set-current")
    public ResponseEntity<Map<String, Object>> setCurrentTranslation(
            @PathVariable Long wordId,
            @PathVariable Long translationId) {
        Map<String, Object> response = new HashMap<>();
        try {
            wordVariantService.setCurrentTranslation(wordId, translationId);

            // Also update the legacy field on WordEntity
            WordTranslationEntity translation = wordVariantService.getCurrentTranslation(wordId).orElse(null);
            if (translation != null) {
                Optional<WordEntity> wordOpt = wordService.getAllWords().stream()
                        .filter(w -> w.getId().equals(wordId))
                        .findFirst();
                if (wordOpt.isPresent()) {
                    WordEntity word = wordOpt.get();
                    Set<String> translations = new HashSet<>(Arrays.asList(translation.getTranslation().split(", ")));
                    wordService.updateTranslation(word.getWord(), word.getSourceLanguage(),
                            word.getTargetLanguage(), translations);
                    if (translation.getTransliteration() != null) {
                        wordService.updateTransliteration(word.getWord(), word.getSourceLanguage(),
                                word.getTargetLanguage(), translation.getTransliteration());
                    }
                }
            }

            response.put("success", true);
            response.put("message", "Current translation updated");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get all sentence variants for a word
     */
    @GetMapping("/{wordId}/variants/sentences")
    public ResponseEntity<List<Map<String, Object>>> getSentenceVariants(@PathVariable Long wordId) {
        List<WordSentenceEntity> sentences = wordVariantService.getSentenceHistory(wordId);
        List<Map<String, Object>> result = sentences.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("sentenceSource", s.getSentenceSource());
            map.put("sentenceTransliteration", s.getSentenceTransliteration());
            map.put("sentenceTarget", s.getSentenceTarget());
            map.put("wordStructure", s.getWordStructure());
            map.put("audioFile", s.getAudioFile());
            map.put("isCurrent", s.getIsCurrent());
            map.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
            return map;
        }).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Set current sentence variant
     */
    @PostMapping("/{wordId}/variants/sentences/{sentenceId}/set-current")
    public ResponseEntity<Map<String, Object>> setCurrentSentence(
            @PathVariable Long wordId,
            @PathVariable Long sentenceId) {
        Map<String, Object> response = new HashMap<>();
        try {
            wordVariantService.setCurrentSentence(wordId, sentenceId);
            response.put("success", true);
            response.put("message", "Current sentence updated");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get variant counts for a word
     */
    @GetMapping("/{wordId}/variants/counts")
    public ResponseEntity<Map<String, Object>> getVariantCounts(@PathVariable Long wordId) {
        WordVariantService.VariantCounts counts = wordVariantService.getVariantCounts(wordId);
        Map<String, Object> response = new HashMap<>();
        response.put("translations", counts.translations());
        response.put("mnemonics", counts.mnemonics());
        response.put("images", counts.images());
        response.put("sentences", counts.sentences());
        return ResponseEntity.ok(response);
    }

    /**
     * Clear translation override for a word, allowing regeneration
     */
    @DeleteMapping("/{wordId}/clear-override/translation")
    public ResponseEntity<Map<String, Object>> clearTranslationOverride(@PathVariable Long wordId) {
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
            wordService.clearTranslationOverride(word.getWord(), word.getSourceLanguage(), word.getTargetLanguage());

            response.put("success", true);
            response.put("message", "Translation override cleared");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Clear transliteration override for a word, allowing regeneration
     */
    @DeleteMapping("/{wordId}/clear-override/transliteration")
    public ResponseEntity<Map<String, Object>> clearTransliterationOverride(@PathVariable Long wordId) {
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
            wordService.clearTransliterationOverride(word.getWord(), word.getSourceLanguage(), word.getTargetLanguage());

            response.put("success", true);
            response.put("message", "Transliteration override cleared");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Update image style for a word
     */
    @PostMapping("/{wordId}/update-image-style")
    public ResponseEntity<Map<String, Object>> updateImageStyle(
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

            String imageStyleStr = (String) request.get("imageStyle");
            if (imageStyleStr == null || imageStyleStr.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Image style is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Validate the image style
            ImageStyle imageStyle = ImageStyle.fromString(imageStyleStr);

            wordService.updateImageStyle(wordId, imageStyle.name());

            response.put("success", true);
            response.put("message", "Image style updated successfully");
            response.put("imageStyle", imageStyle.name());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Clear mnemonic keyword override for a word, allowing regeneration
     */
    @DeleteMapping("/{wordId}/clear-override/mnemonic-keyword")
    public ResponseEntity<Map<String, Object>> clearMnemonicKeywordOverride(@PathVariable Long wordId) {
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
            wordService.clearMnemonicKeywordOverride(word.getWord(), word.getSourceLanguage(), word.getTargetLanguage());

            response.put("success", true);
            response.put("message", "Mnemonic keyword override cleared");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
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
