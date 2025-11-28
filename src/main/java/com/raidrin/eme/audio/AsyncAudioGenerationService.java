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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Service for async audio generation
 */
@Service
@RequiredArgsConstructor
public class AsyncAudioGenerationService {

    private final TextToAudioGenerator audioGenerator;

    @Value("${audio.output.directory:./generated_audio}")
    private String outputDirectory;

    @Value("${audio.concurrency.level:5}")
    private int audioConcurrencyLevel;

    /**
     * Generate audio files asynchronously for multiple text items (sequential processing)
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
     * Generate audio files in parallel batches for better performance.
     * Uses a dedicated thread pool to process multiple audio files concurrently.
     *
     * @param audioRequests List of audio generation requests
     * @return CompletableFuture with list of generated audio results
     */
    @Async("taskExecutor")
    public CompletableFuture<List<AudioResult>> generateAudioFilesParallel(List<AudioRequest> audioRequests) {
        if (audioRequests.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        long startTime = System.currentTimeMillis();
        System.out.println("[AUDIO] Starting parallel audio generation for " + audioRequests.size() +
            " items with concurrency level " + audioConcurrencyLevel);

        ExecutorService audioExecutor = Executors.newFixedThreadPool(audioConcurrencyLevel);
        List<AudioResult> results = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        try {
            // Create futures for all audio requests
            List<CompletableFuture<Void>> futures = audioRequests.stream()
                .map(request -> CompletableFuture.runAsync(() -> {
                    try {
                        long itemStart = System.currentTimeMillis();

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

                        long itemDuration = System.currentTimeMillis() - itemStart;
                        System.out.println("[AUDIO] Generated: " + request.getFileName() + " (" + itemDuration + "ms)");

                    } catch (Exception e) {
                        String error = "Audio generation failed for '" + request.getText() + "': " + e.getMessage();
                        errors.add(error);
                        System.err.println("[AUDIO] " + error);

                        // Add failed result
                        results.add(new AudioResult(null, request.getFileName(), request.getText(),
                            request.getLanguageCode().getCode()));
                    }
                }, audioExecutor))
                .collect(Collectors.toList());

            // Wait for all to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            long totalDuration = System.currentTimeMillis() - startTime;
            System.out.println("[AUDIO] Parallel generation completed: " + results.size() + " files in " +
                totalDuration + "ms (avg " + (totalDuration / Math.max(1, audioRequests.size())) + "ms/file)");

            if (!errors.isEmpty()) {
                System.err.println("[AUDIO] " + errors.size() + " errors occurred during audio generation");
            }

            return CompletableFuture.completedFuture(new ArrayList<>(results));

        } finally {
            audioExecutor.shutdown();
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

    /**
     * Generate a single audio file synchronously (for use within async contexts)
     */
    public AudioResult generateAudioSync(AudioRequest request) {
        try {
            byte[] audioBytes = audioGenerator.generate(
                request.getText(),
                request.getLanguageCode(),
                request.getVoiceGender(),
                request.getVoiceName()
            );

            Path audioFilePath = saveAudioToFile(audioBytes, request.getFileName());

            return new AudioResult(
                audioFilePath.toString(),
                request.getFileName(),
                request.getText(),
                request.getLanguageCode().getCode()
            );
        } catch (Exception e) {
            System.err.println("Audio generation failed for: " + request.getText() + " - " + e.getMessage());
            return new AudioResult(null, request.getFileName(), request.getText(),
                request.getLanguageCode().getCode());
        }
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
