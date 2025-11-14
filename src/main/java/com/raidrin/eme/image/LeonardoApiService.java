package com.raidrin.eme.image;

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

/**
 * Service for generating images using Leonardo AI API
 * Documentation: https://docs.leonardo.ai/docs
 */
@Service
@RequiredArgsConstructor
public class LeonardoApiService {

    @Value("${leonardo.api.key}")
    private String leonardoApiKey;

    @Value("${leonardo.api.base-url:https://cloud.leonardo.ai/api/rest/v1}")
    private String baseUrl;

    @Value("${leonardo.model.id:6bef9f1b-29cb-40c7-b9df-32b51c1f67d3}")
    private String modelId;

    private final RestTemplate restTemplate;

    /**
     * Generate an image using Leonardo AI
     *
     * @param prompt The image generation prompt
     * @param width Image width (default: 1152)
     * @param height Image height (default: 768)
     * @return GeneratedImage with the URL and generation ID
     */
    public GeneratedImage generateImage(String prompt, Integer width, Integer height) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt must be provided");
        }

        // Use default dimensions if not provided
        int imageWidth = width != null ? width : 1152;
        int imageHeight = height != null ? height : 768;

        System.out.println("Generating image with Leonardo AI - Prompt: " + prompt);

        // Step 1: Create a generation request
        String generationId = createGeneration(prompt, imageWidth, imageHeight);

        // Step 2: Poll for completion and get the image URL
        GeneratedImage image = waitForGeneration(generationId);

        System.out.println("Image generation completed - ID: " + generationId);
        return image;
    }

    /**
     * Generate image with default dimensions (1152x768)
     */
    public GeneratedImage generateImage(String prompt) {
        return generateImage(prompt, 1152, 768);
    }

    private String createGeneration(String prompt, int width, int height) {
        LeonardoGenerationRequest request = new LeonardoGenerationRequest();
        request.setPrompt(prompt);
        request.setModelId(modelId); // Configurable via leonardo.model.id property
        request.setWidth(width);
        request.setHeight(height);
        request.setNum_images(1);
        request.setPhotoReal(false);
        // Note: presetStyle is only valid when photoReal is true, so we don't set it here

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + leonardoApiKey);
            headers.set("Content-Type", "application/json");

            HttpEntity<LeonardoGenerationRequest> entity = new HttpEntity<>(request, headers);

            String url = baseUrl + "/generations";
            System.out.println("Making Leonardo API request to: " + url);

            ResponseEntity<LeonardoGenerationResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    LeonardoGenerationResponse.class
            );

            if (response.getBody() != null && response.getBody().getSdGenerationJob() != null) {
                String generationId = response.getBody().getSdGenerationJob().getGenerationId();
                System.out.println("Generation started with ID: " + generationId);
                return generationId;
            } else {
                throw new RuntimeException("Leonardo API returned empty response");
            }
        } catch (Exception e) {
            System.err.println("Leonardo API error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to create image generation: " + e.getMessage(), e);
        }
    }

    private GeneratedImage waitForGeneration(String generationId) {
        int maxAttempts = 60; // 60 attempts with 2 second intervals = 2 minutes max
        int attemptCount = 0;

        while (attemptCount < maxAttempts) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + leonardoApiKey);

                HttpEntity<Void> entity = new HttpEntity<>(headers);

                String url = baseUrl + "/generations/" + generationId;

                ResponseEntity<LeonardoGenerationStatusResponse> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        LeonardoGenerationStatusResponse.class
                );

                if (response.getBody() != null &&
                    response.getBody().getGenerations_by_pk() != null &&
                    response.getBody().getGenerations_by_pk().getStatus().equals("COMPLETE")) {

                    GenerationResult result = response.getBody().getGenerations_by_pk();
                    List<GeneratedImageInfo> images = result.getGenerated_images();
                    if (images != null && !images.isEmpty()) {
                        GeneratedImageInfo imageInfo = images.get(0);
                        Integer creditCost = result.getImageCredit();
                        return new GeneratedImage(generationId, imageInfo.getUrl(), imageInfo.getId(), creditCost);
                    }
                }

                // Wait 2 seconds before next attempt
                Thread.sleep(2000);
                attemptCount++;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Image generation was interrupted", e);
            } catch (Exception e) {
                System.err.println("Error checking generation status: " + e.getMessage());
                attemptCount++;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Image generation was interrupted", ie);
                }
            }
        }

        throw new RuntimeException("Image generation timed out after " + maxAttempts + " attempts");
    }

    @Data
    private static class LeonardoGenerationRequest {
        private String prompt;
        private String modelId;
        private Integer width;
        private Integer height;
        private Integer num_images;
        private Boolean photoReal;
        private String presetStyle;
    }

    @Data
    private static class LeonardoGenerationResponse {
        private SdGenerationJob sdGenerationJob;
    }

    @Data
    private static class SdGenerationJob {
        private String generationId;
    }

    @Data
    private static class LeonardoGenerationStatusResponse {
        private GenerationResult generations_by_pk;
    }

    @Data
    private static class GenerationResult {
        private String status;
        private List<GeneratedImageInfo> generated_images;
        private Integer imageCredit;
    }

    @Data
    private static class GeneratedImageInfo {
        private String id;
        private String url;
    }

    @Data
    public static class GeneratedImage {
        private String generationId;
        private String imageUrl;
        private String imageId;
        private Integer creditCost;

        public GeneratedImage(String generationId, String imageUrl, String imageId, Integer creditCost) {
            this.generationId = generationId;
            this.imageUrl = imageUrl;
            this.imageId = imageId;
            this.creditCost = creditCost;
        }
    }
}
