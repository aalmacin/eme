package com.raidrin.eme.session;

import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.raidrin.eme.audio.AsyncAudioGenerationService;
import com.raidrin.eme.audio.LanguageAudioCodes;
import com.raidrin.eme.codec.Codec;
import com.raidrin.eme.image.LeonardoApiService;
import com.raidrin.eme.mnemonic.MnemonicGenerationService;
import com.raidrin.eme.mnemonic.MnemonicGenerationService.MnemonicData;
import com.raidrin.eme.sentence.SentenceData;
import com.raidrin.eme.sentence.SentenceGenerationService;
import com.raidrin.eme.storage.entity.TranslationSessionEntity;
import com.raidrin.eme.storage.entity.TranslationSessionEntity.SessionStatus;
import com.raidrin.eme.storage.service.GcpStorageService;
import com.raidrin.eme.storage.service.TranslationSessionService;
import com.raidrin.eme.translator.TranslationService;
import com.raidrin.eme.util.FileNameSanitizer;
import com.raidrin.eme.util.ZipFileGenerator;
import lombok.Data;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates the complete async workflow for translation sessions:
 * - Audio generation (source + target + sentences)
 * - Image generation with mnemonics
 * - ZIP creation with all assets
 */
@Service
@RequiredArgsConstructor
public class SessionOrchestrationService {

    private final TranslationService translationService;
    private final SentenceGenerationService sentenceGenerationService;
    private final MnemonicGenerationService mnemonicGenerationService;
    private final AsyncAudioGenerationService audioGenerationService;
    private final LeonardoApiService leonardoService;
    private final GcpStorageService gcpStorageService;
    private final TranslationSessionService sessionService;
    private final ZipFileGenerator zipFileGenerator;

    @Value("${audio.output.directory:./generated_audio}")
    private String audioOutputDirectory;

    @Value("${image.output.directory:./generated_images}")
    private String imageOutputDirectory;

    /**
     * Process a batch of words/translations asynchronously
     *
     * @param sessionId Session ID for tracking
     * @param request Configuration for the batch processing
     * @return CompletableFuture that completes when all processing is done
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> processTranslationBatchAsync(Long sessionId, BatchProcessingRequest request) {
        try {
            System.out.println("Starting async batch processing for session: " + sessionId);
            sessionService.updateStatus(sessionId, SessionStatus.IN_PROGRESS);

            // Track process statuses
            Map<String, Object> processStatuses = new HashMap<>();
            List<String> translationErrors = new ArrayList<>();
            List<String> audioErrors = new ArrayList<>();
            List<String> imageErrors = new ArrayList<>();
            List<String> sentenceErrors = new ArrayList<>();

            List<Map<String, Object>> wordResults = new ArrayList<>();
            List<AsyncAudioGenerationService.AudioRequest> allAudioRequests = new ArrayList<>();
            Set<String> processedAudioFiles = new HashSet<>(); // Avoid duplicate audio files

            // Process each source word
            int currentWordIndex = 0;
            for (String sourceWord : request.getSourceWords()) {
                System.out.println("Processing word: " + sourceWord + " (" + (currentWordIndex + 1) + "/" + request.getSourceWords().size() + ")");

                // Check if this word has been processed before
                Map<String, Object> existingWordData = sessionService.findExistingWordData(
                    sourceWord,
                    request.getSourceLanguage(),
                    request.getTargetLanguage()
                );

                if (existingWordData != null) {
                    System.out.println("Found existing data for word: " + sourceWord + ", reusing assets");
                    existingWordData.put("reused", true);
                    existingWordData.put("reused_timestamp", java.time.LocalDateTime.now().toString());
                    wordResults.add(existingWordData);

                    // Still add audio requests if audio files exist (they might not be in the current directory)
                    if (existingWordData.containsKey("source_audio_file") && request.isEnableSourceAudio()) {
                        String sourceAudioFileName = Codec.encodeForAudioFileName(sourceWord);
                        if (!processedAudioFiles.contains(sourceAudioFileName)) {
                            processedAudioFiles.add(sourceAudioFileName);
                        }
                    }

                    // Update progress incrementally
                    currentWordIndex++;
                    updateProgressData(sessionId, request, wordResults, currentWordIndex);

                    continue; // Skip to next word
                }

                System.out.println("No existing data found for word: " + sourceWord + ", generating new assets");
                Map<String, Object> wordData = new HashMap<>();
                wordData.put("source_word", sourceWord);
                wordData.put("reused", false);

                // Step 1: Get translations
                Set<String> translations = null;
                if (request.isEnableTranslation()) {
                    try {
                        translations = translationService.translateText(
                            sourceWord,
                            request.getSourceLanguageCode(),
                            request.getTargetLanguageCode()
                        );
                        wordData.put("translations", new ArrayList<>(translations));
                        wordData.put("translation_status", "success");
                    } catch (Exception e) {
                        String error = "Translation failed for '" + sourceWord + "': " + e.getMessage();
                        translationErrors.add(error);
                        wordData.put("translation_status", "failed");
                        wordData.put("translation_error", e.getMessage());
                        System.err.println(error);
                    }
                }

                // Step 2: Generate source audio
                if (request.isEnableSourceAudio()) {
                    String sourceAudioFileName = Codec.encodeForAudioFileName(sourceWord);
                    wordData.put("source_audio_file", sourceAudioFileName + ".mp3");

                    if (!processedAudioFiles.contains(sourceAudioFileName)) {
                        allAudioRequests.add(new AsyncAudioGenerationService.AudioRequest(
                            sourceWord,
                            request.getSourceAudioLanguageCode(),
                            request.getSourceVoiceGender(),
                            request.getSourceVoiceName(),
                            sourceAudioFileName
                        ));
                        processedAudioFiles.add(sourceAudioFileName);
                    }
                }

                // Step 3: Generate target audio for all translations
                if (request.isEnableTargetAudio() && translations != null) {
                    List<String> targetAudioFiles = new ArrayList<>();
                    for (String translation : translations) {
                        String targetAudioFileName = Codec.encodeForAudioFileName(translation);
                        targetAudioFiles.add(targetAudioFileName + ".mp3");

                        if (!processedAudioFiles.contains(targetAudioFileName)) {
                            allAudioRequests.add(new AsyncAudioGenerationService.AudioRequest(
                                translation,
                                request.getTargetAudioLanguageCode(),
                                request.getTargetVoiceGender(),
                                request.getTargetVoiceName(),
                                targetAudioFileName
                            ));
                            processedAudioFiles.add(targetAudioFileName);
                        }
                    }
                    wordData.put("target_audio_files", targetAudioFiles);
                }

                // Step 4: Generate sentences if enabled
                if (request.isEnableSentenceGeneration()) {
                    try {
                        SentenceData sentenceData = sentenceGenerationService.generateSentence(
                            sourceWord,
                            request.getSourceLanguage(),
                            request.isEnableTranslation() ? request.getTargetLanguage() : "en"
                        );

                        if (sentenceData != null) {
                            wordData.put("sentence_data", convertSentenceDataToMap(sentenceData));
                            wordData.put("sentence_status", "success");

                            // Generate sentence audio
                            if (sentenceData.getSourceLanguageSentence() != null) {
                                String sentenceAudioFileName = Codec.encodeForAudioFileName(sentenceData.getSourceLanguageSentence());
                                wordData.put("sentence_audio_file", sentenceAudioFileName + ".mp3");

                                if (!processedAudioFiles.contains(sentenceAudioFileName)) {
                                    allAudioRequests.add(new AsyncAudioGenerationService.AudioRequest(
                                        sentenceData.getSourceLanguageSentence(),
                                        request.getSourceAudioLanguageCode(),
                                        request.getSourceVoiceGender(),
                                        request.getSourceVoiceName(),
                                        sentenceAudioFileName
                                    ));
                                    processedAudioFiles.add(sentenceAudioFileName);
                                }
                            }
                        }
                    } catch (Exception e) {
                        String error = "Sentence generation failed for '" + sourceWord + "': " + e.getMessage();
                        sentenceErrors.add(error);
                        wordData.put("sentence_status", "failed");
                        wordData.put("sentence_error", e.getMessage());
                        System.err.println(error);
                    }
                }

                // Step 5: Generate mnemonic and image if enabled
                if (request.isEnableImageGeneration() && translations != null && !translations.isEmpty()) {
                    try {
                        String primaryTranslation = translations.iterator().next();

                        // Generate mnemonic
                        MnemonicData mnemonicData = mnemonicGenerationService.generateMnemonic(
                            sourceWord, primaryTranslation,
                            request.getSourceLanguage(), request.getTargetLanguage()
                        );

                        wordData.put("mnemonic_keyword", mnemonicData.getMnemonicKeyword());
                        wordData.put("mnemonic_sentence", mnemonicData.getMnemonicSentence());
                        wordData.put("image_prompt", mnemonicData.getImagePrompt());

                        // Generate image
                        LeonardoApiService.GeneratedImage generatedImage = leonardoService.generateImage(
                            mnemonicData.getImagePrompt(), 1152, 768
                        );

                        // Download image
                        String imageFileName = FileNameSanitizer.fromMnemonicSentence(
                            mnemonicData.getMnemonicSentence(), "jpg"
                        );
                        Path localImagePath = downloadImageToLocal(generatedImage.getImageUrl(), imageFileName);
                        wordData.put("image_file", imageFileName);
                        wordData.put("image_local_path", localImagePath.toString());

                        // Upload to GCP
                        String gcsUrl = gcpStorageService.downloadAndUpload(generatedImage.getImageUrl(), imageFileName);
                        wordData.put("image_gcs_url", gcsUrl);
                        wordData.put("leonardo_generation_id", generatedImage.getGenerationId());
                        wordData.put("credit_cost", generatedImage.getCreditCost());
                        wordData.put("image_status", "success");
                    } catch (Exception e) {
                        String error = "Image generation failed for '" + sourceWord + "': " + e.getMessage();
                        imageErrors.add(error);
                        wordData.put("image_status", "failed");
                        wordData.put("image_error", e.getMessage());
                        System.err.println(error);
                    }
                }

                wordResults.add(wordData);

                // Save word data to WordEntity for future reuse (only for new data, not reused)
                if (!Boolean.TRUE.equals(wordData.get("reused"))) {
                    try {
                        sessionService.saveWordDataToEntity(wordData, request.getSourceLanguage(), request.getTargetLanguage());
                    } catch (Exception e) {
                        System.err.println("Failed to save word data to entity for: " + sourceWord + " - " + e.getMessage());
                    }
                }

                // Update progress incrementally after each word
                currentWordIndex++;
                updateProgressData(sessionId, request, wordResults, currentWordIndex);
            }

            // Step 6: Generate all audio files
            List<String> audioFilePaths = new ArrayList<>();
            int audioSuccessCount = 0;
            int audioFailureCount = 0;
            if (!allAudioRequests.isEmpty()) {
                try {
                    System.out.println("Generating " + allAudioRequests.size() + " audio files...");
                    CompletableFuture<List<AsyncAudioGenerationService.AudioResult>> audioFuture =
                        audioGenerationService.generateAudioFilesAsync(allAudioRequests);

                    List<AsyncAudioGenerationService.AudioResult> audioResults = audioFuture.get();
                    for (AsyncAudioGenerationService.AudioResult audioResult : audioResults) {
                        if (audioResult.getLocalFilePath() != null) {
                            audioFilePaths.add(audioResult.getLocalFilePath());
                            audioSuccessCount++;
                        } else {
                            audioFailureCount++;
                            String error = "Audio generation failed for: " + audioResult.getFileName();
                            audioErrors.add(error);
                        }
                    }
                } catch (Exception e) {
                    String error = "Audio generation batch failed: " + e.getMessage();
                    audioErrors.add(error);
                    audioFailureCount = allAudioRequests.size();
                    System.err.println(error);
                }
            }

            // Step 7: Update session data
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("words", wordResults);
            sessionData.put("total_words", request.getSourceWords().size());
            sessionData.put("processed_words", request.getSourceWords().size());
            sessionData.put("processing", false);
            sessionData.put("audio_files", audioFilePaths);
            sessionData.put("source_language", request.getSourceLanguage());
            sessionData.put("target_language", request.getTargetLanguage());

            // Calculate deduplication statistics
            long reusedCount = wordResults.stream()
                .filter(wd -> Boolean.TRUE.equals(wd.get("reused")))
                .count();
            long newCount = wordResults.stream()
                .filter(wd -> Boolean.FALSE.equals(wd.get("reused")))
                .count();

            Map<String, Object> dedupStats = new HashMap<>();
            dedupStats.put("reused_count", reusedCount);
            dedupStats.put("new_count", newCount);
            dedupStats.put("total_count", wordResults.size());
            sessionData.put("dedup_stats", dedupStats);

            // Store original request for retry
            Map<String, Object> originalRequest = new HashMap<>();
            originalRequest.put("source_words", request.getSourceWords());
            originalRequest.put("source_language", request.getSourceLanguage());
            originalRequest.put("target_language", request.getTargetLanguage());
            originalRequest.put("source_language_code", request.getSourceLanguageCode());
            originalRequest.put("target_language_code", request.getTargetLanguageCode());
            originalRequest.put("enable_source_audio", request.isEnableSourceAudio());
            originalRequest.put("enable_target_audio", request.isEnableTargetAudio());
            originalRequest.put("source_audio_language_code", request.getSourceAudioLanguageCode() != null ? request.getSourceAudioLanguageCode().name() : null);
            originalRequest.put("target_audio_language_code", request.getTargetAudioLanguageCode() != null ? request.getTargetAudioLanguageCode().name() : null);
            originalRequest.put("source_voice_gender", request.getSourceVoiceGender() != null ? request.getSourceVoiceGender().name() : null);
            originalRequest.put("target_voice_gender", request.getTargetVoiceGender() != null ? request.getTargetVoiceGender().name() : null);
            originalRequest.put("source_voice_name", request.getSourceVoiceName());
            originalRequest.put("target_voice_name", request.getTargetVoiceName());
            originalRequest.put("enable_translation", request.isEnableTranslation());
            originalRequest.put("enable_sentence_generation", request.isEnableSentenceGeneration());
            originalRequest.put("enable_image_generation", request.isEnableImageGeneration());
            sessionData.put("original_request", originalRequest);

            // Add process statuses
            Map<String, Object> processSummary = new HashMap<>();
            processSummary.put("translation_errors", translationErrors);
            processSummary.put("audio_errors", audioErrors);
            processSummary.put("audio_success_count", audioSuccessCount);
            processSummary.put("audio_failure_count", audioFailureCount);
            processSummary.put("image_errors", imageErrors);
            processSummary.put("sentence_errors", sentenceErrors);
            processSummary.put("has_errors", !translationErrors.isEmpty() || !audioErrors.isEmpty() ||
                                              !imageErrors.isEmpty() || !sentenceErrors.isEmpty());
            sessionData.put("process_summary", processSummary);

            sessionService.updateSessionData(sessionId, sessionData);

            // Step 8: Create ZIP file with all assets
            TranslationSessionEntity session = sessionService.findById(sessionId).orElseThrow();
            String zipPath = zipFileGenerator.createSessionZip(session, sessionData);
            sessionService.updateZipFilePath(sessionId, zipPath);

            // Step 9: Mark as completed
            sessionService.updateStatus(sessionId, SessionStatus.COMPLETED);

            System.out.println("Batch processing completed for session: " + sessionId);
            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            System.err.println("Batch processing failed for session " + sessionId + ": " + e.getMessage());
            e.printStackTrace();
            sessionService.markAsFailed(sessionId, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    private Path downloadImageToLocal(String imageUrl, String fileName) throws IOException {
        Path outputDir = Paths.get(imageOutputDirectory);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        Path filePath = outputDir.resolve(fileName);

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

    private Map<String, Object> convertSentenceDataToMap(SentenceData sentenceData) {
        Map<String, Object> map = new HashMap<>();
        map.put("source_language_sentence", sentenceData.getSourceLanguageSentence());
        map.put("source_language_structure", sentenceData.getSourceLanguageStructure());
        map.put("target_language_sentence", sentenceData.getTargetLanguageSentence());
        map.put("target_language_latin", sentenceData.getTargetLanguageLatinCharacters());
        map.put("target_language_transliteration", sentenceData.getTargetLanguageTransliteration());
        return map;
    }

    /**
     * Update session data with current progress (called incrementally during processing)
     */
    private void updateProgressData(Long sessionId, BatchProcessingRequest request,
                                     List<Map<String, Object>> wordResults, int currentWordIndex) {
        try {
            Map<String, Object> progressData = new HashMap<>();
            progressData.put("words", new ArrayList<>(wordResults)); // Create a copy
            progressData.put("total_words", request.getSourceWords().size());
            progressData.put("processed_words", currentWordIndex);
            progressData.put("processing", true);
            progressData.put("last_update", java.time.LocalDateTime.now().toString());
            progressData.put("source_language", request.getSourceLanguage());
            progressData.put("target_language", request.getTargetLanguage());

            sessionService.updateSessionData(sessionId, progressData);
            System.out.println("Progress updated: " + currentWordIndex + "/" + request.getSourceWords().size() + " words processed");
        } catch (Exception e) {
            System.err.println("Failed to update progress: " + e.getMessage());
            // Don't throw exception - progress updates are non-critical
        }
    }

    /**
     * Reconstruct BatchProcessingRequest from stored session data
     */
    @SuppressWarnings("unchecked")
    public BatchProcessingRequest reconstructRequestFromSessionData(Map<String, Object> sessionData) {
        if (!sessionData.containsKey("original_request")) {
            throw new IllegalStateException("Session data does not contain original request information");
        }

        Map<?, ?> originalRequest = (Map<?, ?>) sessionData.get("original_request");
        BatchProcessingRequest request = new BatchProcessingRequest();

        request.setSourceWords((List<String>) originalRequest.get("source_words"));
        request.setSourceLanguage((String) originalRequest.get("source_language"));
        request.setTargetLanguage((String) originalRequest.get("target_language"));
        request.setSourceLanguageCode((String) originalRequest.get("source_language_code"));
        request.setTargetLanguageCode((String) originalRequest.get("target_language_code"));
        request.setEnableSourceAudio((Boolean) originalRequest.get("enable_source_audio"));
        request.setEnableTargetAudio((Boolean) originalRequest.get("enable_target_audio"));

        String sourceAudioLangCode = (String) originalRequest.get("source_audio_language_code");
        if (sourceAudioLangCode != null) {
            request.setSourceAudioLanguageCode(LanguageAudioCodes.valueOf(sourceAudioLangCode));
        }

        String targetAudioLangCode = (String) originalRequest.get("target_audio_language_code");
        if (targetAudioLangCode != null) {
            request.setTargetAudioLanguageCode(LanguageAudioCodes.valueOf(targetAudioLangCode));
        }

        String sourceVoiceGender = (String) originalRequest.get("source_voice_gender");
        if (sourceVoiceGender != null) {
            request.setSourceVoiceGender(SsmlVoiceGender.valueOf(sourceVoiceGender));
        }

        String targetVoiceGender = (String) originalRequest.get("target_voice_gender");
        if (targetVoiceGender != null) {
            request.setTargetVoiceGender(SsmlVoiceGender.valueOf(targetVoiceGender));
        }

        request.setSourceVoiceName((String) originalRequest.get("source_voice_name"));
        request.setTargetVoiceName((String) originalRequest.get("target_voice_name"));
        request.setEnableTranslation((Boolean) originalRequest.get("enable_translation"));
        request.setEnableSentenceGeneration((Boolean) originalRequest.get("enable_sentence_generation"));
        request.setEnableImageGeneration((Boolean) originalRequest.get("enable_image_generation"));

        return request;
    }

    /**
     * Configuration for batch processing
     */
    @Data
    public static class BatchProcessingRequest {
        private List<String> sourceWords;
        private String sourceLanguage;
        private String targetLanguage;
        private String sourceLanguageCode;  // For translation API
        private String targetLanguageCode;  // For translation API

        // Audio configuration
        private boolean enableSourceAudio;
        private boolean enableTargetAudio;
        private LanguageAudioCodes sourceAudioLanguageCode;
        private LanguageAudioCodes targetAudioLanguageCode;
        private SsmlVoiceGender sourceVoiceGender;
        private SsmlVoiceGender targetVoiceGender;
        private String sourceVoiceName;
        private String targetVoiceName;

        // Feature flags
        private boolean enableTranslation;
        private boolean enableSentenceGeneration;
        private boolean enableImageGeneration;
    }
}
