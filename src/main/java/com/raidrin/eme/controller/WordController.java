package com.raidrin.eme.controller;

import com.raidrin.eme.image.ImageProvider;
import com.raidrin.eme.image.OpenAiImageService;
import com.raidrin.eme.mnemonic.MnemonicGenerationService;
import com.raidrin.eme.storage.entity.WordEntity;
import com.raidrin.eme.storage.service.GcpStorageService;
import com.raidrin.eme.storage.service.WordService;
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

    @Value("${image.output.directory:./generated_images}")
    private String imageOutputDirectory;

    /**
     * Create a new word and trigger async processing pipeline
     *
     * @param request Request body with word details:
     *                - word: The source word
     *                - sourceLanguage: Source language code (e.g., "en", "hi", "tl")
     *                - targetLanguage: Target language code (e.g., "en", "hi", "tl")
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createWord(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String word = request.get("word");
            String sourceLanguage = request.get("sourceLanguage");
            String targetLanguage = request.get("targetLanguage");

            if (word == null || word.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Word must be provided");
                return ResponseEntity.badRequest().body(response);
            }
            if (sourceLanguage == null || sourceLanguage.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Source language must be provided");
                return ResponseEntity.badRequest().body(response);
            }
            if (targetLanguage == null || targetLanguage.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Target language must be provided");
                return ResponseEntity.badRequest().body(response);
            }

            // Check if word already exists
            if (wordService.hasWord(word, sourceLanguage, targetLanguage)) {
                response.put("success", false);
                response.put("error", "Word already exists");
                return ResponseEntity.badRequest().body(response);
            }

            // Create word and trigger processing pipeline
            var wordEntity = wordService.createWordAndTriggerProcessing(word, sourceLanguage, targetLanguage);

            response.put("success", true);
            response.put("wordId", wordEntity.getId());
            response.put("word", wordEntity.getWord());
            response.put("sourceLanguage", wordEntity.getSourceLanguage());
            response.put("targetLanguage", wordEntity.getTargetLanguage());
            response.put("translationStatus", wordEntity.getTranslationStatus().toString());
            response.put("audioGenerationStatus", wordEntity.getAudioGenerationStatus().toString());
            response.put("imageGenerationStatus", wordEntity.getImageGenerationStatus().toString());
            response.put("message", "Word created and processing started. Check status via GET /api/words/{wordId}");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

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

                // Generate mnemonic and prompt
                MnemonicGenerationService.MnemonicData mnemonicData = mnemonicGenerationService.generateMnemonic(
                        word.getWord(),
                        translation,
                        word.getSourceLanguage(),
                        word.getTargetLanguage()
                );

                imagePrompt = mnemonicData.getImagePrompt();

                // Update mnemonic data
                wordService.updateMnemonic(
                        word.getWord(),
                        word.getSourceLanguage(),
                        word.getTargetLanguage(),
                        mnemonicData.getMnemonicKeyword(),
                        mnemonicData.getMnemonicSentence(),
                        imagePrompt
                );
            }

            // Update the prompt and clear old image (only if not using same prompt)
            if (!useSamePrompt) {
                wordService.updateImagePromptAndClearImage(wordId, imagePrompt);
            }

            // Generate new image using OpenAI with selected model
            String imageUrl;
            String revisedPrompt = null;

            System.out.println("Generating image with OpenAI model: " + model);

            OpenAiImageService.GeneratedImage openAiImage = openAiImageService.generateImage(imagePrompt, model);
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
