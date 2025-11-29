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
import com.raidrin.eme.storage.service.SentenceStorageService;
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
import java.util.concurrent.*;
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
    private final SentenceStorageService sentenceStorageService;
    private final TranslationSessionService sessionService;
    private final WordService wordService;
    private final ZipFileGenerator zipFileGenerator;

    @Value("${audio.output.directory:./generated_audio}")
    private String audioOutputDirectory;

    @Value("${image.output.directory:./generated_images}")
    private String imageOutputDirectory;

    @Value("${processing.concurrency.level:3}")
    private int concurrencyLevel;

    @Value("${processing.phase2.concurrency.level:4}")
    private int phase2ConcurrencyLevel;

    // Dedicated executor for CPU-bound parallel operations within word processing
    private final ExecutorService wordProcessingExecutor = Executors.newCachedThreadPool();

    /**
     * Process a batch of words/translations asynchronously
     *
     * @param sessionId Session ID for tracking
     * @param request Configuration for the batch processing
     * @return CompletableFuture that completes when all processing is done
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> processTranslationBatchAsync(Long sessionId, BatchProcessingRequest request) {
        long sessionStartTime = System.currentTimeMillis();

        try {
            System.out.println("[SESSION " + sessionId + "] Starting async batch processing");
            System.out.println("[SESSION " + sessionId + "] Configuration: " +
                "words=" + request.getSourceWords().size() +
                ", translation=" + request.isEnableTranslation() +
                ", audio=" + (request.isEnableSourceAudio() || request.isEnableTargetAudio()) +
                ", sentences=" + request.isEnableSentenceGeneration() +
                ", images=" + request.isEnableImageGeneration());

            sessionService.updateStatus(sessionId, SessionStatus.IN_PROGRESS);

            // Initialize progress data at the start
            Map<String, Object> initialProgressData = new HashMap<>();
            initialProgressData.put("total_words", request.getSourceWords().size());
            initialProgressData.put("processed_words", 0);
            initialProgressData.put("processing", true);
            initialProgressData.put("last_update", java.time.LocalDateTime.now().toString());
            initialProgressData.put("source_language", request.getSourceLanguage());
            initialProgressData.put("target_language", request.getTargetLanguage());
            initialProgressData.put("words", new ArrayList<>());
            sessionService.updateSessionData(sessionId, initialProgressData);

            // Track process statuses - use thread-safe collections for concurrent processing
            List<String> translationErrors = Collections.synchronizedList(new ArrayList<>());
            List<String> audioErrors = Collections.synchronizedList(new ArrayList<>());
            List<String> imageErrors = Collections.synchronizedList(new ArrayList<>());
            List<String> sentenceErrors = Collections.synchronizedList(new ArrayList<>());

            List<Map<String, Object>> wordResults = Collections.synchronizedList(new ArrayList<>());
            List<AsyncAudioGenerationService.AudioRequest> allAudioRequests = Collections.synchronizedList(new ArrayList<>());
            Set<String> processedAudioFiles = ConcurrentHashMap.newKeySet(); // Thread-safe set
            AtomicInteger processedWordCount = new AtomicInteger(0);

            // Timing trackers
            Map<String, Long> phaseTiming = new ConcurrentHashMap<>();
            phaseTiming.put("word_processing_start", System.currentTimeMillis());

            // Process words concurrently in batches with proper dependency handling
            // Dependencies:
            // - Translations: No dependency (can run immediately)
            // - Audio: No dependency (can run immediately, but target audio needs translations)
            // - Mnemonics and Images: Depends on translations
            // - Example Sentences: Depends on translations
            List<CompletableFuture<Map<String, Object>>> wordFutures = new ArrayList<>();

            for (int i = 0; i < request.getSourceWords().size(); i++) {
                final int wordIndex = i;
                final String sourceWord = request.getSourceWords().get(wordIndex);

                CompletableFuture<Map<String, Object>> wordFuture = CompletableFuture.supplyAsync(() -> {
                    long wordStartTime = System.currentTimeMillis();
                    System.out.println("[WORD " + (wordIndex + 1) + "/" + request.getSourceWords().size() + "] Processing: " + sourceWord);

                    // Check if this word has been processed before
                    Map<String, Object> existingWordData = sessionService.findExistingWordData(
                        sourceWord,
                        request.getSourceLanguage(),
                        request.getTargetLanguage()
                    );

                    // If override is enabled, skip reusing existing data and force new translation
                    if (existingWordData != null && !request.isOverrideTranslation()) {
                        System.out.println("[WORD " + (wordIndex + 1) + "] Found existing data, reusing assets");
                        existingWordData.put("reused", true);
                        existingWordData.put("reused_timestamp", java.time.LocalDateTime.now().toString());

                        // Still add audio requests if audio files exist (they might not be in the current directory)
                        if (existingWordData.containsKey("source_audio_file") && request.isEnableSourceAudio()) {
                            String sourceAudioFileName = Codec.encodeForAudioFileName(sourceWord);
                            if (!processedAudioFiles.contains(sourceAudioFileName)) {
                                processedAudioFiles.add(sourceAudioFileName);
                            }
                        }

                        long wordDuration = System.currentTimeMillis() - wordStartTime;
                        System.out.println("[WORD " + (wordIndex + 1) + "] Reused: " + sourceWord + " (" + wordDuration + "ms)");
                        return existingWordData;
                    }

                    if (request.isOverrideTranslation()) {
                        System.out.println("[WORD " + (wordIndex + 1) + "] Override enabled - forcing new translation");
                    }

                    System.out.println("[WORD " + (wordIndex + 1) + "] Generating new assets for: " + sourceWord);
                    Map<String, Object> wordData = new HashMap<>();
                    wordData.put("source_word", sourceWord);
                    wordData.put("reused", false);

                    // ============================================================
                    // PHASE 1: Run independent operations in parallel
                    // - Translations (no dependency)
                    // - Source audio collection (no dependency)
                    // ============================================================
                    long phase1Start = System.currentTimeMillis();

                    // Future for translations - use dedicated executor
                    CompletableFuture<TranslationResult> translationFuture = CompletableFuture.supplyAsync(() -> {
                        TranslationResult result = new TranslationResult();
                        if (!request.isEnableTranslation()) {
                            return result;
                        }

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

                            result.translations = wordService.deserializeTranslations(wordEntity.getTranslation());
                            result.success = true;
                            result.isOverride = true;

                            // Get transliteration from word entity
                            if (wordEntity.getSourceTransliteration() != null && !wordEntity.getSourceTransliteration().trim().isEmpty()) {
                                result.transliteration = wordEntity.getSourceTransliteration();
                                System.out.println("Using stored transliteration: " + result.transliteration);
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
                                result.translations = translationData.getTranslations();
                                result.success = true;

                                // Get transliteration from translation response
                                result.transliteration = translationData.getTransliteration();
                                if (result.transliteration == null || result.transliteration.trim().isEmpty()) {
                                    // Check if existingWordData already has transliteration
                                    if (existingWordData != null && existingWordData.containsKey("source_transliteration")) {
                                        result.transliteration = (String) existingWordData.get("source_transliteration");
                                        System.out.println("Using stored transliteration from cache: " + result.transliteration);
                                    } else {
                                        System.out.println("No transliteration available for: " + sourceWord);
                                    }
                                } else {
                                    System.out.println("Transliteration from translation service: " + result.transliteration);
                                }
                            } catch (Exception e) {
                                result.error = e.getMessage();
                                result.success = false;
                                System.err.println("Translation failed for '" + sourceWord + "': " + e.getMessage());
                            }
                        }
                        return result;
                    }, wordProcessingExecutor);

                    // Collect source audio request (no dependency on translation)
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

                    // Wait for translation to complete before proceeding to Phase 2
                    TranslationResult translationResult = translationFuture.join();
                    long phase1Duration = System.currentTimeMillis() - phase1Start;
                    System.out.println("[WORD " + (wordIndex + 1) + "] Phase 1 (Translation) completed in " + phase1Duration + "ms");

                    // Store translation results in wordData
                    if (request.isEnableTranslation()) {
                        if (translationResult.success) {
                            wordData.put("translations", new ArrayList<>(translationResult.translations));
                            wordData.put("translation_status", "success");
                            if (translationResult.isOverride) {
                                wordData.put("translation_override", true);
                            }
                            if (translationResult.transliteration != null && !translationResult.transliteration.trim().isEmpty()) {
                                wordData.put("source_transliteration", translationResult.transliteration);
                            }
                        } else {
                            String error = "Translation failed for '" + sourceWord + "': " + translationResult.error;
                            translationErrors.add(error);
                            wordData.put("translation_status", "failed");
                            wordData.put("translation_error", translationResult.error);
                        }
                    }

                    // ============================================================
                    // PHASE 2: Run translation-dependent operations in parallel
                    // - Target audio (depends on translations)
                    // - Mnemonics and Images (depends on translations)
                    // - Example Sentences (depends on translations)
                    // ============================================================
                    long phase2Start = System.currentTimeMillis();

                    Set<String> translations = translationResult.translations;
                    String transliteration = translationResult.transliteration;

                    List<CompletableFuture<Void>> phase2Futures = new ArrayList<>();

                    // Target audio collection (depends on translations)
                    if (request.isEnableTargetAudio() && translations != null && !translations.isEmpty()) {
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

                    // Sentence generation (depends on translations for target language context)
                    if (request.isEnableSentenceGeneration()) {
                        final String finalTransliteration = transliteration;
                        CompletableFuture<Void> sentenceFuture = CompletableFuture.runAsync(() -> {
                            long sentenceStart = System.currentTimeMillis();
                            try {
                                SentenceData sentenceData = sentenceGenerationService.generateSentence(
                                    sourceWord,
                                    request.getSourceLanguage(),
                                    request.isEnableTranslation() ? request.getTargetLanguage() : "en"
                                );

                                if (sentenceData != null) {
                                    synchronized (wordData) {
                                        wordData.put("sentence_data", convertSentenceDataToMap(sentenceData));
                                        wordData.put("sentence_status", "success");

                                        // Generate sentence audio
                                        if (sentenceData.getSourceLanguageSentence() != null) {
                                            String sentenceAudioFileName = Codec.encodeForAudioFileName(sentenceData.getSourceLanguageSentence());
                                            String sentenceAudioFileNameWithExt = sentenceAudioFileName + ".mp3";
                                            wordData.put("sentence_audio_file", sentenceAudioFileNameWithExt);

                                            // Update sentence data with audio file and save to database
                                            sentenceData.setAudioFile(sentenceAudioFileNameWithExt);
                                            sentenceStorageService.saveSentence(
                                                sourceWord,
                                                request.getSourceLanguage(),
                                                request.isEnableTranslation() ? request.getTargetLanguage() : "en",
                                                sentenceData
                                            );

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
                                    long sentenceDuration = System.currentTimeMillis() - sentenceStart;
                                    System.out.println("[WORD " + (wordIndex + 1) + "] Sentence generated in " + sentenceDuration + "ms");
                                }
                            } catch (Exception e) {
                                String error = "Sentence generation failed for '" + sourceWord + "': " + e.getMessage();
                                sentenceErrors.add(error);
                                synchronized (wordData) {
                                    wordData.put("sentence_status", "failed");
                                    wordData.put("sentence_error", e.getMessage());
                                }
                                System.err.println(error);
                            }
                        }, wordProcessingExecutor);
                        phase2Futures.add(sentenceFuture);
                    }

                    // Mnemonic and image generation (depends on translations)
                    if (request.isEnableImageGeneration() && translations != null && !translations.isEmpty()) {
                        final String finalTransliteration2 = transliteration;
                        final Set<String> finalTranslations = translations;
                        CompletableFuture<Void> imageFuture = CompletableFuture.runAsync(() -> {
                            long imageStart = System.currentTimeMillis();
                            try {
                                // Check if word already has an image - skip generation if so
                                Optional<WordEntity> existingWordOpt = wordService.findWord(
                                    sourceWord, request.getSourceLanguage(), request.getTargetLanguage()
                                );

                                if (existingWordOpt.isPresent()) {
                                    WordEntity existingWord = existingWordOpt.get();
                                    if (existingWord.getImageFile() != null && !existingWord.getImageFile().isEmpty()) {
                                        // Word already has an image - skip generation and reuse existing
                                        System.out.println("[WORD " + (wordIndex + 1) + "] Image already exists, skipping generation. " +
                                            "Use 'Regenerate Image' button to create a new one.");

                                        synchronized (wordData) {
                                            wordData.put("image_file", existingWord.getImageFile());
                                            if (existingWord.getMnemonicKeyword() != null) {
                                                wordData.put("mnemonic_keyword", existingWord.getMnemonicKeyword());
                                            }
                                            if (existingWord.getMnemonicSentence() != null) {
                                                wordData.put("mnemonic_sentence", existingWord.getMnemonicSentence());
                                            }
                                            if (existingWord.getImagePrompt() != null) {
                                                wordData.put("image_prompt", existingWord.getImagePrompt());
                                            }
                                            wordData.put("image_status", "reused");
                                            wordData.put("image_skipped", true);
                                        }

                                        long skipDuration = System.currentTimeMillis() - imageStart;
                                        System.out.println("[WORD " + (wordIndex + 1) + "] Image reuse completed in " + skipDuration + "ms");
                                        return; // Skip image generation
                                    }
                                }

                                // No existing image - proceed with generation
                                String primaryTranslation = finalTranslations.iterator().next();

                                // Generate mnemonic with transliteration for character matching
                                long mnemonicStart = System.currentTimeMillis();
                                MnemonicData mnemonicData = mnemonicGenerationService.generateMnemonic(
                                    sourceWord, primaryTranslation,
                                    request.getSourceLanguage(), request.getTargetLanguage(),
                                    finalTransliteration2, request.getImageStyle()
                                );

                                long mnemonicDuration = System.currentTimeMillis() - mnemonicStart;
                                System.out.println("[WORD " + (wordIndex + 1) + "] Mnemonic generated in " + mnemonicDuration + "ms");

                                synchronized (wordData) {
                                    wordData.put("mnemonic_keyword", mnemonicData.getMnemonicKeyword());
                                    wordData.put("mnemonic_sentence", mnemonicData.getMnemonicSentence());
                                    wordData.put("image_prompt", mnemonicData.getImagePrompt());
                                }

                                // Sanitize the image prompt before sending to image generation API
                                String sanitizedPrompt = mnemonicGenerationService.sanitizeImagePrompt(mnemonicData.getImagePrompt());

                                // Generate image using OpenAI
                                // Use 1536x1024 for landscape format (closest to 1152x768 ratio)
                                // gpt-image-1-mini supports: 1024x1024, 1024x1536, 1536x1024, auto
                                long imageGenStart = System.currentTimeMillis();
                                String size = "1536x1024";
                                OpenAiImageService.GeneratedImage generatedImage = openAiImageService.generateImage(
                                    sanitizedPrompt, size, "medium", null
                                );
                                long imageGenDuration = System.currentTimeMillis() - imageGenStart;
                                System.out.println("[WORD " + (wordIndex + 1) + "] Image generated in " + imageGenDuration + "ms");

                                // Download image and upload to GCP in parallel
                                String imageFileName = FileNameSanitizer.fromMnemonicSentence(
                                    mnemonicData.getMnemonicSentence(), "jpg"
                                );

                                // Run download and upload concurrently
                                CompletableFuture<Path> downloadFuture = CompletableFuture.supplyAsync(() -> {
                                    try {
                                        return downloadImageToLocal(generatedImage.getImageUrl(), imageFileName);
                                    } catch (IOException e) {
                                        throw new RuntimeException("Failed to download image: " + e.getMessage(), e);
                                    }
                                }, wordProcessingExecutor);

                                CompletableFuture<String> uploadFuture = CompletableFuture.supplyAsync(() ->
                                    gcpStorageService.downloadAndUpload(generatedImage.getImageUrl(), imageFileName),
                                    wordProcessingExecutor
                                );

                                // Wait for both to complete
                                Path localImagePath = downloadFuture.join();
                                String gcsUrl = uploadFuture.join();

                                synchronized (wordData) {
                                    wordData.put("image_file", imageFileName);
                                    wordData.put("image_local_path", localImagePath.toString());
                                    wordData.put("image_gcs_url", gcsUrl);
                                    wordData.put("image_provider", "openai");
                                    wordData.put("image_status", "success");
                                }

                                long totalImageDuration = System.currentTimeMillis() - imageStart;
                                System.out.println("[WORD " + (wordIndex + 1) + "] Total image processing completed in " + totalImageDuration + "ms");

                            } catch (Exception e) {
                                String error = "Image generation failed for '" + sourceWord + "': " + e.getMessage();
                                imageErrors.add(error);
                                synchronized (wordData) {
                                    wordData.put("image_status", "failed");
                                    wordData.put("image_error", e.getMessage());
                                }
                                System.err.println(error);
                            }
                        }, wordProcessingExecutor);
                        phase2Futures.add(imageFuture);
                    }

                    // Wait for all Phase 2 operations to complete
                    if (!phase2Futures.isEmpty()) {
                        CompletableFuture.allOf(phase2Futures.toArray(new CompletableFuture[0])).join();
                        long phase2Duration = System.currentTimeMillis() - phase2Start;
                        System.out.println("[WORD " + (wordIndex + 1) + "] Phase 2 (Sentences/Images) completed in " + phase2Duration + "ms");
                    }

                    // Save word data to WordEntity for future reuse (only for new data, not reused)
                    if (!Boolean.TRUE.equals(wordData.get("reused"))) {
                        try {
                            sessionService.saveWordDataToEntity(wordData, request.getSourceLanguage(), request.getTargetLanguage());
                        } catch (Exception e) {
                            System.err.println("Failed to save word data to entity for: " + sourceWord + " - " + e.getMessage());
                        }
                    }

                    long wordDuration = System.currentTimeMillis() - wordStartTime;
                    System.out.println("[WORD " + (wordIndex + 1) + "] Completed: " + sourceWord + " (" + wordDuration + "ms)");

                    return wordData;
                }, wordProcessingExecutor);

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

            phaseTiming.put("word_processing_end", System.currentTimeMillis());
            long wordProcessingDuration = phaseTiming.get("word_processing_end") - phaseTiming.get("word_processing_start");
            System.out.println("[SESSION " + sessionId + "] Word processing completed in " + wordProcessingDuration + "ms");

            // Step 6: Generate all audio files in parallel
            phaseTiming.put("audio_generation_start", System.currentTimeMillis());
            List<String> audioFilePaths = new ArrayList<>();
            int audioSuccessCount = 0;
            int audioFailureCount = 0;
            if (!allAudioRequests.isEmpty()) {
                try {
                    System.out.println("[SESSION " + sessionId + "] Starting parallel audio generation for " + allAudioRequests.size() + " files...");

                    // Use the new parallel audio generation method
                    CompletableFuture<List<AsyncAudioGenerationService.AudioResult>> audioFuture =
                        audioGenerationService.generateAudioFilesParallel(new ArrayList<>(allAudioRequests));

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
            phaseTiming.put("audio_generation_end", System.currentTimeMillis());
            long audioGenerationDuration = phaseTiming.get("audio_generation_end") - phaseTiming.get("audio_generation_start");
            System.out.println("[SESSION " + sessionId + "] Audio generation completed in " + audioGenerationDuration + "ms (" +
                audioSuccessCount + " success, " + audioFailureCount + " failed)");

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
            phaseTiming.put("zip_creation_start", System.currentTimeMillis());
            TranslationSessionEntity session = sessionService.findById(sessionId).orElseThrow();
            String zipPath = zipFileGenerator.createSessionZip(session, sessionData);
            sessionService.updateZipFilePath(sessionId, zipPath);
            phaseTiming.put("zip_creation_end", System.currentTimeMillis());
            long zipDuration = phaseTiming.get("zip_creation_end") - phaseTiming.get("zip_creation_start");
            System.out.println("[SESSION " + sessionId + "] ZIP creation completed in " + zipDuration + "ms");

            // Step 9: Mark as completed
            sessionService.updateStatus(sessionId, SessionStatus.COMPLETED);

            // Print final timing summary
            long totalDuration = System.currentTimeMillis() - sessionStartTime;
            System.out.println("[SESSION " + sessionId + "] ========== TIMING SUMMARY ==========");
            System.out.println("[SESSION " + sessionId + "] Word processing: " + wordProcessingDuration + "ms");
            System.out.println("[SESSION " + sessionId + "] Audio generation: " + audioGenerationDuration + "ms");
            System.out.println("[SESSION " + sessionId + "] ZIP creation: " + zipDuration + "ms");
            System.out.println("[SESSION " + sessionId + "] TOTAL: " + totalDuration + "ms (" + (totalDuration / 1000) + "s)");
            System.out.println("[SESSION " + sessionId + "] Words processed: " + wordResults.size());
            System.out.println("[SESSION " + sessionId + "] Audio files: " + audioSuccessCount);
            System.out.println("[SESSION " + sessionId + "] =====================================");

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

    /**
     * Helper class to hold translation results for async processing
     */
    private static class TranslationResult {
        Set<String> translations;
        String transliteration;
        boolean success = false;
        boolean isOverride = false;
        String error;
    }
}
