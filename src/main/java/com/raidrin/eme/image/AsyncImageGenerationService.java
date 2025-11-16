package com.raidrin.eme.image;

import com.raidrin.eme.mnemonic.MnemonicGenerationService;
import com.raidrin.eme.mnemonic.MnemonicGenerationService.MnemonicData;
import com.raidrin.eme.storage.service.GcpStorageService;
import com.raidrin.eme.storage.service.TranslationSessionService;
import com.raidrin.eme.storage.entity.TranslationSessionEntity.SessionStatus;
import com.raidrin.eme.util.FileNameSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for async image generation workflow
 */
@Service
@RequiredArgsConstructor
public class AsyncImageGenerationService {

    private final MnemonicGenerationService mnemonicService;
    private final OpenAiImageService openAiImageService;
    private final GcpStorageService gcpStorageService;
    private final TranslationSessionService sessionService;

    @Value("${image.output.directory:./generated_images}")
    private String outputDirectory;

    /**
     * Generate images for a single word translation asynchronously with pre-generated mnemonic data
     *
     * @param sessionId ID of the translation session
     * @param mnemonicData Pre-generated mnemonic data
     * @param fileName Pre-calculated filename
     * @return CompletableFuture with list of generated image file paths
     */
    @Async("taskExecutor")
    public CompletableFuture<List<ImageResult>> generateImagesAsync(
            Long sessionId, MnemonicData mnemonicData, String fileName) {

        try {
            System.out.println("Starting async image generation for session: " + sessionId + " with pre-generated mnemonic");
            sessionService.updateStatus(sessionId, SessionStatus.IN_PROGRESS);

            // Step 1: Generate image using configured provider
            System.out.println("Generating image with prompt: " + mnemonicData.getImagePrompt());
            GeneratedImageInfo generatedImage = generateImage(
                    mnemonicData.getImagePrompt(), 1152, 768);

            // Step 2: Download image to local file
            Path localFilePath = downloadImageToLocal(generatedImage.getImageUrl(), fileName);

            // Step 3: Upload to GCP Storage as backup
            System.out.println("Uploading image to GCP Storage: " + fileName);
            String gcsUrl = gcpStorageService.downloadAndUpload(
                    generatedImage.getImageUrl(), fileName);

            // Step 4: Create result
            ImageResult result = new ImageResult(
                    localFilePath.toString(),
                    gcsUrl,
                    fileName,
                    mnemonicData.getMnemonicKeyword(),
                    mnemonicData.getMnemonicSentence(),
                    mnemonicData.getImagePrompt()
            );

            // Step 5: Update session data
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("mnemonic_keyword", mnemonicData.getMnemonicKeyword());
            sessionData.put("mnemonic_sentence", mnemonicData.getMnemonicSentence());
            sessionData.put("image_prompt", mnemonicData.getImagePrompt());
            sessionData.put("image_file", fileName);
            sessionData.put("local_path", localFilePath.toString());
            sessionData.put("gcs_url", gcsUrl);
            sessionData.put("image_provider", generatedImage.getProvider());
            if (generatedImage.getGenerationId() != null) {
                sessionData.put("generation_id", generatedImage.getGenerationId());
            }
            if (generatedImage.getCreditCost() != null) {
                sessionData.put("credit_cost", generatedImage.getCreditCost());
            }

            sessionService.updateSessionData(sessionId, sessionData);
            sessionService.updateStatus(sessionId, SessionStatus.COMPLETED);

            System.out.println("Image generation completed for session: " + sessionId);

            return CompletableFuture.completedFuture(List.of(result));

        } catch (Exception e) {
            System.err.println("Image generation failed for session " + sessionId + ": " + e.getMessage());
            e.printStackTrace();
            sessionService.markAsFailed(sessionId, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Generate images for a single word translation asynchronously
     *
     * @param sessionId ID of the translation session
     * @param sourceWord Word in source language
     * @param targetWord Word in target language (translation)
     * @param sourceLanguage Source language code
     * @param targetLanguage Target language code
     * @param imageStyle Style for image generation (defaults to REALISTIC_CINEMATIC if null)
     * @return CompletableFuture with list of generated image file paths
     */
    @Async("taskExecutor")
    public CompletableFuture<List<ImageResult>> generateImagesAsync(
            Long sessionId, String sourceWord, String targetWord,
            String sourceLanguage, String targetLanguage, ImageStyle imageStyle) {

        try {
            System.out.println("Starting async image generation for session: " + sessionId);
            sessionService.updateStatus(sessionId, SessionStatus.IN_PROGRESS);

            // Step 1: Generate mnemonic data
            System.out.println("Generating mnemonic for: " + sourceWord + " -> " + targetWord);
            MnemonicData mnemonicData = mnemonicService.generateMnemonic(
                    sourceWord, targetWord, sourceLanguage, targetLanguage, imageStyle);

            // Step 2: Generate image using configured provider
            System.out.println("Generating image with prompt: " + mnemonicData.getImagePrompt());
            GeneratedImageInfo generatedImage = generateImage(
                    mnemonicData.getImagePrompt(), 1152, 768);

            // Step 3: Download image to local file
            String fileName = FileNameSanitizer.fromMnemonicSentence(
                    mnemonicData.getMnemonicSentence(), "jpg");

            Path localFilePath = downloadImageToLocal(generatedImage.getImageUrl(), fileName);

            // Step 4: Upload to GCP Storage as backup
            System.out.println("Uploading image to GCP Storage: " + fileName);
            String gcsUrl = gcpStorageService.downloadAndUpload(
                    generatedImage.getImageUrl(), fileName);

            // Step 5: Create result
            ImageResult result = new ImageResult(
                    localFilePath.toString(),
                    gcsUrl,
                    fileName,
                    mnemonicData.getMnemonicKeyword(),
                    mnemonicData.getMnemonicSentence(),
                    mnemonicData.getImagePrompt()
            );

            // Step 6: Update session data
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("mnemonic_keyword", mnemonicData.getMnemonicKeyword());
            sessionData.put("mnemonic_sentence", mnemonicData.getMnemonicSentence());
            sessionData.put("image_prompt", mnemonicData.getImagePrompt());
            sessionData.put("image_file", fileName);
            sessionData.put("local_path", localFilePath.toString());
            sessionData.put("gcs_url", gcsUrl);
            sessionData.put("image_provider", generatedImage.getProvider());
            if (generatedImage.getGenerationId() != null) {
                sessionData.put("generation_id", generatedImage.getGenerationId());
            }
            if (generatedImage.getCreditCost() != null) {
                sessionData.put("credit_cost", generatedImage.getCreditCost());
            }

            sessionService.updateSessionData(sessionId, sessionData);
            sessionService.updateStatus(sessionId, SessionStatus.COMPLETED);

            System.out.println("Image generation completed for session: " + sessionId);

            return CompletableFuture.completedFuture(List.of(result));

        } catch (Exception e) {
            System.err.println("Image generation failed for session " + sessionId + ": " + e.getMessage());
            e.printStackTrace();
            sessionService.markAsFailed(sessionId, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Generate images for multiple word translations
     *
     * @param sessionId ID of the translation session
     * @param translations List of translation pairs
     * @param sourceLanguage Source language code
     * @param targetLanguage Target language code
     * @param imageStyle Style for image generation (defaults to REALISTIC_CINEMATIC if null)
     * @return CompletableFuture with list of generated images
     */
    @Async("taskExecutor")
    public CompletableFuture<List<ImageResult>> generateMultipleImagesAsync(
            Long sessionId, List<TranslationPair> translations,
            String sourceLanguage, String targetLanguage, ImageStyle imageStyle) {

        try {
            System.out.println("Starting async image generation for " + translations.size() + " words");
            sessionService.updateStatus(sessionId, SessionStatus.IN_PROGRESS);

            List<ImageResult> results = new ArrayList<>();
            Map<String, Object> allSessionData = new HashMap<>();
            List<Map<String, Object>> imagesData = new ArrayList<>();

            for (int i = 0; i < translations.size(); i++) {
                TranslationPair pair = translations.get(i);
                System.out.println("Processing " + (i + 1) + "/" + translations.size() + ": " +
                        pair.getSourceWord() + " -> " + pair.getTargetWord());

                // Generate mnemonic
                MnemonicData mnemonicData = mnemonicService.generateMnemonic(
                        pair.getSourceWord(), pair.getTargetWord(),
                        sourceLanguage, targetLanguage, imageStyle);

                // Generate image using configured provider
                GeneratedImageInfo generatedImage = generateImage(
                        mnemonicData.getImagePrompt(), 1152, 768);

                // Download and save
                String fileName = FileNameSanitizer.fromMnemonicSentence(
                        mnemonicData.getMnemonicSentence(), "jpg");
                Path localFilePath = downloadImageToLocal(generatedImage.getImageUrl(), fileName);

                // Upload to GCP
                String gcsUrl = gcpStorageService.downloadAndUpload(
                        generatedImage.getImageUrl(), fileName);

                // Create result
                ImageResult result = new ImageResult(
                        localFilePath.toString(), gcsUrl, fileName,
                        mnemonicData.getMnemonicKeyword(),
                        mnemonicData.getMnemonicSentence(),
                        mnemonicData.getImagePrompt()
                );
                results.add(result);

                // Track in session data
                Map<String, Object> imageData = new HashMap<>();
                imageData.put("word", pair.getTargetWord());
                imageData.put("mnemonic_keyword", mnemonicData.getMnemonicKeyword());
                imageData.put("mnemonic_sentence", mnemonicData.getMnemonicSentence());
                imageData.put("image_file", fileName);
                imageData.put("local_path", localFilePath.toString());
                imageData.put("gcs_url", gcsUrl);
                imagesData.add(imageData);
            }

            allSessionData.put("images", imagesData);
            allSessionData.put("total_count", translations.size());
            sessionService.updateSessionData(sessionId, allSessionData);
            sessionService.updateStatus(sessionId, SessionStatus.COMPLETED);

            System.out.println("All images generated successfully for session: " + sessionId);

            return CompletableFuture.completedFuture(results);

        } catch (Exception e) {
            System.err.println("Multiple image generation failed: " + e.getMessage());
            e.printStackTrace();
            sessionService.markAsFailed(sessionId, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Generate image using OpenAI
     *
     * @param prompt The image generation prompt
     * @param width Desired width (used to determine aspect ratio)
     * @param height Desired height (used to determine aspect ratio)
     * @return Generated image info with URL
     */
    private GeneratedImageInfo generateImage(String prompt, int width, int height) {
        // OpenAI gpt-image-1-mini supports: 1024x1024, 1024x1536, 1536x1024, auto
        // Choose closest match based on aspect ratio
        String size = (width > height) ? "1536x1024" : (width < height) ? "1024x1536" : "1024x1024";
        OpenAiImageService.GeneratedImage openAiImage = openAiImageService.generateImage(
                prompt, size, "medium", null);
        return new GeneratedImageInfo(
                openAiImage.getImageUrl(),
                null,  // OpenAI doesn't have generation ID
                null,  // OpenAI doesn't provide credit cost in response
                "openai"
        );
    }

    /**
     * Unified image info structure
     */
    private static class GeneratedImageInfo {
        private final String imageUrl;
        private final String generationId;
        private final Integer creditCost;
        private final String provider;

        public GeneratedImageInfo(String imageUrl, String generationId, Integer creditCost, String provider) {
            this.imageUrl = imageUrl;
            this.generationId = generationId;
            this.creditCost = creditCost;
            this.provider = provider;
        }

        public String getImageUrl() { return imageUrl; }
        public String getGenerationId() { return generationId; }
        public Integer getCreditCost() { return creditCost; }
        public String getProvider() { return provider; }
    }

    private Path downloadImageToLocal(String imageUrl, String fileName) throws IOException {
        // Create output directory if it doesn't exist
        Path outputDir = Paths.get(outputDirectory);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        Path filePath = outputDir.resolve(fileName);

        // Download the image
        URL url = new URL(imageUrl);
        try (var in = url.openStream();
             var out = new FileOutputStream(filePath.toFile())) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        System.out.println("Downloaded image to: " + filePath);
        return filePath;
    }

    /**
     * Result of image generation
     */
    public static class ImageResult {
        private final String localFilePath;
        private final String gcsUrl;
        private final String fileName;
        private final String mnemonicKeyword;
        private final String mnemonicSentence;
        private final String imagePrompt;

        public ImageResult(String localFilePath, String gcsUrl, String fileName,
                           String mnemonicKeyword, String mnemonicSentence, String imagePrompt) {
            this.localFilePath = localFilePath;
            this.gcsUrl = gcsUrl;
            this.fileName = fileName;
            this.mnemonicKeyword = mnemonicKeyword;
            this.mnemonicSentence = mnemonicSentence;
            this.imagePrompt = imagePrompt;
        }

        public String getLocalFilePath() { return localFilePath; }
        public String getGcsUrl() { return gcsUrl; }
        public String getFileName() { return fileName; }
        public String getMnemonicKeyword() { return mnemonicKeyword; }
        public String getMnemonicSentence() { return mnemonicSentence; }
        public String getImagePrompt() { return imagePrompt; }
    }

    /**
     * Translation pair for batch processing
     */
    public static class TranslationPair {
        private final String sourceWord;
        private final String targetWord;

        public TranslationPair(String sourceWord, String targetWord) {
            this.sourceWord = sourceWord;
            this.targetWord = targetWord;
        }

        public String getSourceWord() { return sourceWord; }
        public String getTargetWord() { return targetWord; }
    }
}
