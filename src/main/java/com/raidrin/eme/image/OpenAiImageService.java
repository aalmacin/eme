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
 * Service for generating images using OpenAI DALL-E API
 * Documentation: https://platform.openai.com/docs/guides/images
 */
@Service
@RequiredArgsConstructor
public class OpenAiImageService {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${openai.api.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${openai.image.model:dall-e-3}")
    private String model;

    private final RestTemplate restTemplate;

    /**
     * Generate an image using OpenAI DALL-E
     *
     * @param prompt The image generation prompt
     * @param size Image size (must be: "1024x1024", "1024x1792", or "1792x1024" for DALL-E 3)
     * @param quality Quality setting ("standard" or "hd")
     * @return GeneratedImage with the URL
     */
    public GeneratedImage generateImage(String prompt, String size, String quality) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt must be provided");
        }

        // Validate size for DALL-E 3
        if (!"1024x1024".equals(size) && !"1024x1792".equals(size) && !"1792x1024".equals(size)) {
            throw new IllegalArgumentException("Size must be one of: 1024x1024, 1024x1792, or 1792x1024");
        }

        System.out.println("Generating image with OpenAI DALL-E - Prompt: " + prompt);

        OpenAiImageRequest request = new OpenAiImageRequest();
        request.setModel(model);
        request.setPrompt(prompt);
        request.setN(1); // Number of images to generate
        request.setSize(size);
        request.setQuality(quality != null ? quality : "standard");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openaiApiKey);
            headers.set("Content-Type", "application/json");

            HttpEntity<OpenAiImageRequest> entity = new HttpEntity<>(request, headers);

            String url = baseUrl + "/images/generations";
            System.out.println("Making OpenAI API request to: " + url);

            ResponseEntity<OpenAiImageResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    OpenAiImageResponse.class
            );

            if (response.getBody() != null && response.getBody().getData() != null && !response.getBody().getData().isEmpty()) {
                OpenAiImageData imageData = response.getBody().getData().get(0);
                System.out.println("Image generation completed");
                return new GeneratedImage(imageData.getUrl(), imageData.getRevised_prompt());
            } else {
                throw new RuntimeException("OpenAI API returned empty response");
            }
        } catch (Exception e) {
            System.err.println("OpenAI API error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to create image generation: " + e.getMessage(), e);
        }
    }

    /**
     * Generate image with default settings (1024x1024, standard quality)
     */
    public GeneratedImage generateImage(String prompt) {
        return generateImage(prompt, "1024x1024", "standard");
    }

    @Data
    private static class OpenAiImageRequest {
        private String model;
        private String prompt;
        private Integer n;
        private String size;
        private String quality;
    }

    @Data
    private static class OpenAiImageResponse {
        private Long created;
        private List<OpenAiImageData> data;
    }

    @Data
    private static class OpenAiImageData {
        private String url;
        private String revised_prompt;
    }

    @Data
    public static class GeneratedImage {
        private String imageUrl;
        private String revisedPrompt;

        public GeneratedImage(String imageUrl, String revisedPrompt) {
            this.imageUrl = imageUrl;
            this.revisedPrompt = revisedPrompt;
        }
    }
}
