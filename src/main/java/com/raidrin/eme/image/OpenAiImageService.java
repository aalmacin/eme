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

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Service for generating images using OpenAI Image Generation API
 * Documentation: https://platform.openai.com/docs/guides/images
 */
@Service
@RequiredArgsConstructor
public class OpenAiImageService {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${openai.api.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${openai.image.model:gpt-image-1}")
    private String model;

    private final RestTemplate restTemplate;

    /**
     * Generate an image using OpenAI Image Generation
     *
     * @param prompt The image generation prompt
     * @param size Image size (must be: "1024x1024", "1024x1792", or "1792x1024")
     * @param quality Quality setting ("low", "medium", "high", or "auto")
     * @return GeneratedImage with the URL
     */
    public GeneratedImage generateImage(String prompt, String size, String quality) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt must be provided");
        }

        // Validate size
        if (!"1024x1024".equals(size) && !"1024x1792".equals(size) && !"1792x1024".equals(size)) {
            throw new IllegalArgumentException("Size must be one of: 1024x1024, 1024x1792, or 1792x1024");
        }

        System.out.println("Generating image with OpenAI - Prompt: " + prompt);

        OpenAiImageRequest request = new OpenAiImageRequest();
        request.setModel(model);
        request.setPrompt(prompt);
        request.setN(1); // Number of images to generate
        request.setSize(size);
        request.setQuality(quality != null ? quality : "medium");

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
                System.out.println("Response data - URL: " + imageData.getUrl() + ", B64: " + (imageData.getB64_json() != null ? "present" : "null") + ", Revised prompt: " + imageData.getRevised_prompt());

                // Check if we have either URL or base64 data
                if (imageData.getUrl() != null && !imageData.getUrl().trim().isEmpty()) {
                    return new GeneratedImage(imageData.getUrl(), imageData.getRevised_prompt());
                } else if (imageData.getB64_json() != null && !imageData.getB64_json().trim().isEmpty()) {
                    System.out.println("OpenAI returned base64 image data - converting to file");
                    try {
                        String fileUrl = saveBase64ToTempFile(imageData.getB64_json());
                        return new GeneratedImage(fileUrl, imageData.getRevised_prompt());
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to save base64 image data to file: " + e.getMessage(), e);
                    }
                } else {
                    System.err.println("OpenAI API returned null or empty URL and base64");
                    System.err.println("Full response body: " + response.getBody());
                    throw new RuntimeException("OpenAI API returned neither URL nor base64 image data");
                }
            } else {
                System.err.println("OpenAI API returned empty response body");
                if (response.getBody() != null) {
                    System.err.println("Response body: " + response.getBody());
                }
                throw new RuntimeException("OpenAI API returned empty response");
            }
        } catch (Exception e) {
            System.err.println("OpenAI API error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to create image generation: " + e.getMessage(), e);
        }
    }

    /**
     * Generate image with default settings (1024x1024, medium quality)
     */
    public GeneratedImage generateImage(String prompt) {
        return generateImage(prompt, "1024x1024", "medium");
    }

    /**
     * Save base64 encoded image data to a temporary file and return file:// URL
     */
    private String saveBase64ToTempFile(String base64Data) throws IOException {
        // Decode base64 to bytes
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);

        // Create temp directory if it doesn't exist
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "openai-images");
        Files.createDirectories(tempDir);

        // Generate unique filename
        String fileName = "openai-" + UUID.randomUUID().toString() + ".png";
        Path tempFile = tempDir.resolve(fileName);

        // Write bytes to file
        try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
            fos.write(imageBytes);
        }

        System.out.println("Saved base64 image to: " + tempFile.toAbsolutePath());

        // Return file:// URL
        return tempFile.toUri().toString();
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
        private String b64_json;
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
