package com.raidrin.eme.audio;

import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for async audio generation
 */
@Service
@RequiredArgsConstructor
public class AsyncAudioGenerationService {

    private final TextToAudioGenerator audioGenerator;

    @Value("${audio.output.directory:./generated_audio}")
    private String outputDirectory;

    /**
     * Generate audio files asynchronously for multiple text items
     *
     * @param audioRequests List of audio generation requests
     * @return CompletableFuture with list of generated audio file paths
     */
    @Async("taskExecutor")
    public CompletableFuture<List<AudioResult>> generateAudioFilesAsync(List<AudioRequest> audioRequests) {
        try {
            System.out.println("Starting async audio generation for " + audioRequests.size() + " items");

            List<AudioResult> results = new ArrayList<>();

            for (AudioRequest request : audioRequests) {
                System.out.println("Generating audio for: " + request.getText() + " (" + request.getLanguageCode().getCode() + ")");

                // Generate audio bytes
                byte[] audioBytes = audioGenerator.generate(
                    request.getText(),
                    request.getLanguageCode(),
                    request.getVoiceGender(),
                    request.getVoiceName()
                );

                // Save to file
                Path audioFilePath = saveAudioToFile(audioBytes, request.getFileName());

                // Create result
                AudioResult result = new AudioResult(
                    audioFilePath.toString(),
                    request.getFileName(),
                    request.getText(),
                    request.getLanguageCode().getCode()
                );
                results.add(result);

                System.out.println("Generated audio file: " + audioFilePath);
            }

            System.out.println("All audio files generated successfully");
            return CompletableFuture.completedFuture(results);

        } catch (Exception e) {
            System.err.println("Audio generation failed: " + e.getMessage());
            e.printStackTrace();
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Generate a single audio file asynchronously
     */
    @Async("taskExecutor")
    public CompletableFuture<AudioResult> generateAudioFileAsync(AudioRequest request) {
        return generateAudioFilesAsync(List.of(request))
            .thenApply(results -> results.isEmpty() ? null : results.get(0));
    }

    private Path saveAudioToFile(byte[] audioBytes, String fileName) throws IOException {
        // Create output directory if it doesn't exist
        Path outputDir = Paths.get(outputDirectory);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        // Ensure fileName has .mp3 extension
        String fullFileName = fileName.endsWith(".mp3") ? fileName : fileName + ".mp3";
        Path filePath = outputDir.resolve(fullFileName);

        // Write audio bytes to file
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(audioBytes);
        }

        return filePath;
    }

    /**
     * Request for audio generation
     */
    @Data
    public static class AudioRequest {
        private final String text;
        private final LanguageAudioCodes languageCode;
        private final SsmlVoiceGender voiceGender;
        private final String voiceName;
        private final String fileName;
    }

    /**
     * Result of audio generation
     */
    @Data
    public static class AudioResult {
        private final String localFilePath;
        private final String fileName;
        private final String text;
        private final String languageCode;
    }
}
