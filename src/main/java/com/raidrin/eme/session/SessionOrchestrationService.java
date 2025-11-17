package com.raidrin.eme.session;

import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.raidrin.eme.audio.AsyncAudioGenerationService;
import com.raidrin.eme.audio.LanguageAudioCodes;
import com.raidrin.eme.codec.Codec;
import com.raidrin.eme.image.ImageStyle;
import com.raidrin.eme.image.OpenAiImageService;
import com.raidrin.eme.mnemonic.MnemonicGenerationService;
import com.raidrin.eme.mnemonic.MnemonicGenerationService.MnemonicData;
import com.raidrin.eme.sentence.SentenceData;
import com.raidrin.eme.sentence.SentenceGenerationService;
import com.raidrin.eme.storage.entity.TranslationSessionEntity;
import com.raidrin.eme.storage.entity.TranslationSessionEntity.SessionStatus;
import com.raidrin.eme.storage.entity.WordEntity;
import com.raidrin.eme.storage.service.GcpStorageService;
import com.raidrin.eme.storage.service.TranslationSessionService;
import com.raidrin.eme.storage.service.WordService;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    private final OpenAiImageService openAiImageService;
    private final GcpStorageService gcpStorageService;
    private final TranslationSessionService sessionService;
    private final WordService wordService;
    private final ZipFileGenerator zipFileGenerator;

    @Value("${audio.output.directory:./generated_audio}")
    private String audioOutputDirectory;

    @Value("${image.output.directory:./generated_images}")
    private String imageOutputDirectory;

    @Value("${processing.concurrency.level:3}")
    private int concurrencyLevel;

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

            // Track process statuses - use thread-safe collections for concurrent processing
            List<String> translationErrors = Collections.synchronizedList(new ArrayList<>());
            List<String> audioErrors = Collections.synchronizedList(new ArrayList<>());
            List<String> imageErrors = Collections.synchronizedList(new ArrayList<>());
            List<String> sentenceErrors = Collections.synchronizedList(new ArrayList<>());

            List<Map<String, Object>> wordResults = Collections.synchronizedList(new ArrayList<>());
            List<AsyncAudioGenerationService.AudioRequest> allAudioRequests = Collections.synchronizedList(new ArrayList<>());
            Set<String> processedAudioFiles = ConcurrentHashMap.newKeySet(); // Thread-safe set
            AtomicInteger processedWordCount = new AtomicInteger(0);

            // Process words concurrently in batches
            List<CompletableFuture<Map<String, Object>>> wordFutures = new ArrayList<>();

            for (int i = 0; i < request.getSourceWords().size(); i++) {
                final int wordIndex = i;
                final String sourceWord = request.getSourceWords().get(wordIndex);

                CompletableFuture<Map<String, Object>> wordFuture = CompletableFuture.supplyAsync(() -> {
                System.out.println("Processing word: " + sourceWord + " (" + (wordIndex + 1) + "/" + request.getSourceWords().size() + ")");

                // Check if this word has been processed before
                Map<String, Object> existingWordData = sessionService.findExistingWordData(
                    sourceWord,
                    request.getSourceLanguage(),
                    request.getTargetLanguage()
                );

                // If override is enabled, skip reusing existing data and force new translation
                if (existingWordData != null && !request.isOverrideTranslation()) {
                    System.out.println("Found existing data for word: " + sourceWord + ", reusing assets");
                    existingWordData.put("reused", true);
                    existingWordData.put("reused_timestamp", java.time.LocalDateTime.now().toString());

                    // Still add audio requests if audio files exist (they might not be in the current directory)
                    if (existingWordData.containsKey("source_audio_file") && request.isEnableSourceAudio()) {
                        String sourceAudioFileName = Codec.encodeForAudioFileName(sourceWord);
                        if (!processedAudioFiles.contains(sourceAudioFileName)) {
                            processedAudioFiles.add(sourceAudioFileName);
                        }
                    }

                    // Return existing data early (skip processing)
                    return existingWordData;
                }

                if (request.isOverrideTranslation()) {
                    System.out.println("Override translation enabled - forcing new translation for: " + sourceWord);
                }

                System.out.println("No existing data found for word: " + sourceWord + ", generating new assets");
                Map<String, Object> wordData = new HashMap<>();
                wordData.put("source_word", sourceWord);
                wordData.put("reused", false);

                // Step 1: Get translations
                Set<String> translations = null;
                String transliteration = null;
                boolean useManuallyOverriddenTranslation = false;

                if (request.isEnableTranslation()) {
                    // First check if word has a manually overridden translation
                    Optional<WordEntity> wordEntityOpt = wordService.findWord(
                        sourceWord,
                        request.getSourceLanguage(),
                        request.getTargetLanguage()
                    );

                    if (wordEntityOpt.isPresent() && wordEntityOpt.get().getTranslationOverrideAt() != null) {
                        // Use existing manually overridden translation
                        WordEntity wordEntity = wordEntityOpt.get();
                        System.out.println("Found manually overridden translation for: " + sourceWord +
                                " (override at: " + wordEntity.getTranslationOverrideAt() + ")");

                        translations = wordService.deserializeTranslations(wordEntity.getTranslation());
                        wordData.put("translations", new ArrayList<>(translations));
                        wordData.put("translation_status", "success");
                        wordData.put("translation_override", true);
                        useManuallyOverriddenTranslation = true;

                        // Get transliteration from word entity
                        if (wordEntity.getSourceTransliteration() != null && !wordEntity.getSourceTransliteration().trim().isEmpty()) {
                            transliteration = wordEntity.getSourceTransliteration();
                            wordData.put("source_transliteration", transliteration);
                            System.out.println("Using stored transliteration: " + transliteration);
                        }
                    } else {
                        // Fetch new translation
                        try {
                            com.raidrin.eme.translator.TranslationData translationData = translationService.translateText(
                                sourceWord,
                                request.getSourceLanguageCode(),
                                request.getTargetLanguageCode(),
                                request.isOverrideTranslation()
                            );
                            translations = translationData.getTranslations();
                            wordData.put("translations", new ArrayList<>(translations));
                            wordData.put("translation_status", "success");

                            // Get transliteration from translation response
                            transliteration = translationData.getTransliteration();
                            if (transliteration == null || transliteration.trim().isEmpty()) {
                                // Check if existingWordData already has transliteration
                                if (existingWordData != null && existingWordData.containsKey("source_transliteration")) {
                                    transliteration = (String) existingWordData.get("source_transliteration");
                                    System.out.println("Using stored transliteration from cache: " + transliteration);
                                } else {
                                    System.out.println("No transliteration available for: " + sourceWord);
                                }
                            } else {
                                System.out.println("Transliteration from translation service: " + transliteration);
                            }
                            if (transliteration != null && !transliteration.trim().isEmpty()) {
                                wordData.put("source_transliteration", transliteration);
                            }
                        } catch (Exception e) {
                            String error = "Translation failed for '" + sourceWord + "': " + e.getMessage();
                            translationErrors.add(error);
                            wordData.put("translation_status", "failed");
                            wordData.put("translation_error", e.getMessage());
                            System.err.println(error);
                        }
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

                        // Generate mnemonic with transliteration for character matching
                        MnemonicData mnemonicData = mnemonicGenerationService.generateMnemonic(
                            sourceWord, primaryTranslation,
                            request.getSourceLanguage(), request.getTargetLanguage(),
                            transliteration, request.getImageStyle()
                        );

                        wordData.put("mnemonic_keyword", mnemonicData.getMnemonicKeyword());
                        wordData.put("mnemonic_sentence", mnemonicData.getMnemonicSentence());
                        wordData.put("image_prompt", mnemonicData.getImagePrompt());

                        // Sanitize the image prompt before sending to image generation API
                        String sanitizedPrompt = mnemonicGenerationService.sanitizeImagePrompt(mnemonicData.getImagePrompt());

                        // Generate image using OpenAI
                        // Use 1536x1024 for landscape format (closest to 1152x768 ratio)
                        // gpt-image-1-mini supports: 1024x1024, 1024x1536, 1536x1024, auto
                        String size = "1536x1024";
                        OpenAiImageService.GeneratedImage generatedImage = openAiImageService.generateImage(
                            sanitizedPrompt, size, "medium", null
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
                        wordData.put("image_provider", "openai");
                        wordData.put("image_status", "success");
                    } catch (Exception e) {
                        String error = "Image generation failed for '" + sourceWord + "': " + e.getMessage();
                        imageErrors.add(error);
                        wordData.put("image_status", "failed");
                        wordData.put("image_error", e.getMessage());
                        System.err.println(error);
                    }
                }

                    // Save word data to WordEntity for future reuse (only for new data, not reused)
                    if (!Boolean.TRUE.equals(wordData.get("reused"))) {
                        try {
                            sessionService.saveWordDataToEntity(wordData, request.getSourceLanguage(), request.getTargetLanguage());
                        } catch (Exception e) {
                            System.err.println("Failed to save word data to entity for: " + sourceWord + " - " + e.getMessage());
                        }
                    }

                    return wordData;
                });

                wordFutures.add(wordFuture);

                // Limit concurrent word processing to avoid overwhelming external APIs
                if (wordFutures.size() >= concurrencyLevel || i == request.getSourceWords().size() - 1) {
                    // Wait for current batch to complete before starting next batch
                    CompletableFuture.allOf(wordFutures.toArray(new CompletableFuture[0])).join();

                    // Collect results from completed futures
                    for (CompletableFuture<Map<String, Object>> future : wordFutures) {
                        try {
                            Map<String, Object> wordData = future.get();
                            wordResults.add(wordData);

                            // Update progress incrementally after each word
                            int currentCount = processedWordCount.incrementAndGet();
                            updateProgressData(sessionId, request, wordResults, currentCount);
                        } catch (Exception e) {
                            System.err.println("Failed to get word processing result: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    // Clear futures for next batch
                    wordFutures.clear();
                }
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
            originalRequest.put("image_style", request.getImageStyle() != null ? request.getImageStyle().name() : null);
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

        // Default to false if not present (for backward compatibility)
        Boolean overrideTranslation = (Boolean) originalRequest.get("override_translation");
        request.setOverrideTranslation(overrideTranslation != null ? overrideTranslation : false);

        // Reconstruct image style (default to REALISTIC_CINEMATIC for backward compatibility)
        String imageStyleValue = (String) originalRequest.get("image_style");
        if (imageStyleValue != null) {
            request.setImageStyle(ImageStyle.fromString(imageStyleValue));
        } else {
            request.setImageStyle(ImageStyle.REALISTIC_CINEMATIC);
        }

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
        private boolean overrideTranslation; // Force new translation, skip cache

        // Image configuration
        private ImageStyle imageStyle; // Style for image generation (defaults to REALISTIC_CINEMATIC if null)
    }
}
