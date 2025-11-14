package com.raidrin.eme.controller;

import com.raidrin.eme.image.ImageProvider;
import com.raidrin.eme.image.LeonardoApiService;
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
    private final LeonardoApiService leonardoService;
    private final OpenAiImageService openAiImageService;
    private final GcpStorageService gcpStorageService;
    private final MnemonicGenerationService mnemonicGenerationService;

    @Value("${image.output.directory:./generated_images}")
    private String imageOutputDirectory;

    /**
     * Update image prompt and regenerate image for a word
     *
     * @param wordId The ID of the word to regenerate image for
     * @param request Optional request body with parameters:
     *                - imagePrompt: Custom prompt (overrides useSamePrompt)
     *                - useSamePrompt: Boolean, if true uses existing prompt instead of generating new one
     *                - provider: "LEONARDO" or "OPENAI" (default: LEONARDO)
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
            ImageProvider provider = ImageProvider.LEONARDO; // Default to Leonardo

            if (request != null) {
                if (request.containsKey("imagePrompt")) {
                    customPrompt = (String) request.get("imagePrompt");
                }
                if (request.containsKey("useSamePrompt")) {
                    useSamePrompt = Boolean.parseBoolean(request.get("useSamePrompt").toString());
                }
                if (request.containsKey("provider")) {
                    try {
                        provider = ImageProvider.valueOf(request.get("provider").toString().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        response.put("success", false);
                        response.put("error", "Invalid provider. Must be LEONARDO or OPENAI");
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

            // Generate new image using selected provider
            String imageUrl;
            Integer creditCost = null;
            String revisedPrompt = null;

            System.out.println("Generating image with provider: " + provider);

            if (provider == ImageProvider.OPENAI) {
                OpenAiImageService.GeneratedImage openAiImage = openAiImageService.generateImage(imagePrompt);
                imageUrl = openAiImage.getImageUrl();
                revisedPrompt = openAiImage.getRevisedPrompt();
            } else {
                // Leonardo (default)
                LeonardoApiService.GeneratedImage leonardoImage = leonardoService.generateImage(
                        imagePrompt, 1152, 768
                );
                imageUrl = leonardoImage.getImageUrl();
                creditCost = leonardoImage.getCreditCost();
            }

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
            response.put("provider", provider.toString());
            response.put("usedSamePrompt", useSamePrompt);

            if (creditCost != null) {
                response.put("creditCost", creditCost);
            }
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

        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            URL url = new URL(imageUrl);
            url.openStream().transferTo(fos);
        }

        return outputPath;
    }
}
