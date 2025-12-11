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
import com.raidrin.eme.storage.entity.WordEntity;
import com.raidrin.eme.storage.service.GcpStorageService;
import com.raidrin.eme.storage.service.SentenceStorageService;
import com.raidrin.eme.storage.service.TranslationSessionService;
import com.raidrin.eme.storage.service.WordService;
import com.raidrin.eme.translator.TranslationService;
import com.raidrin.eme.util.FileNameSanitizer;
import com.raidrin.eme.util.ZipFileGenerator;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final ExecutorService wordProcessingExecutor;

    public SessionOrchestrationService(
            TranslationService translationService,
            SentenceGenerationService sentenceGenerationService,
            MnemonicGenerationService mnemonicGenerationService,
            AsyncAudioGenerationService audioGenerationService,
            OpenAiImageService openAiImageService,
            GcpStorageService gcpStorageService,
            SentenceStorageService sentenceStorageService,
            TranslationSessionService sessionService,
            WordService wordService,
            ZipFileGenerator zipFileGenerator,
            @Qualifier("wordProcessingExecutor") ExecutorService wordProcessingExecutor) {
        this.translationService = translationService;
        this.sentenceGenerationService = sentenceGenerationService;
        this.mnemonicGenerationService = mnemonicGenerationService;
        this.audioGenerationService = audioGenerationService;
        this.openAiImageService = openAiImageService;
        this.gcpStorageService = gcpStorageService;
        this.sentenceStorageService = sentenceStorageService;
        this.sessionService = sessionService;
        this.wordService = wordService;
        this.zipFileGenerator = zipFileGenerator;
        this.wordProcessingExecutor = wordProcessingExecutor;
    }

    @Value("${audio.output.directory:./generated_audio}")
    private String audioOutputDirectory;

    @Value("${image.output.directory:./generated_images}")
    private String imageOutputDirectory;

    @Value("${processing.concurrency.level:3}")
    private int concurrencyLevel;

    @Value("${processing.phase2.concurrency.level:4}")
    private int phase2ConcurrencyLevel;

    @Value("${processing.progress.update.interval:5}")
    private int progressUpdateInterval;

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
                ", audio=" + request.isEnableSourceAudio() +
                ", sentences=" + request.isEnableSentenceGeneration() +
                ", images=" + request.isEnableImageGeneration());

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
                    // - Mnemonics and Images (depends on translations)
                    // - Example Sentences (depends on translations)
                    // ============================================================
                    long phase2Start = System.currentTimeMillis();

                    Set<String> translations = translationResult.translations;
                    String transliteration = translationResult.transliteration;

                    List<CompletableFuture<Void>> phase2Futures = new ArrayList<>();

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

                            // Update progress in batches to reduce database writes
                            int currentCount = processedWordCount.incrementAndGet();
                            int totalWords = request.getSourceWords().size();
                            boolean isLastWord = currentCount == totalWords;
                            boolean shouldUpdateProgress = currentCount % progressUpdateInterval == 0 || isLastWord;

                            if (shouldUpdateProgress) {
                                updateProgressData(sessionId, request, wordResults, currentCount);
                            }
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
            originalRequest.put("source_audio_language_code", request.getSourceAudioLanguageCode() != null ? request.getSourceAudioLanguageCode().name() : null);
            originalRequest.put("source_voice_gender", request.getSourceVoiceGender() != null ? request.getSourceVoiceGender().name() : null);
            originalRequest.put("source_voice_name", request.getSourceVoiceName());
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
            // Store error in session data
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error", e.getMessage());
            errorData.put("errorTime", java.time.LocalDateTime.now().toString());
            sessionService.updateSessionData(sessionId, errorData);
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

        String sourceAudioLangCode = (String) originalRequest.get("source_audio_language_code");
        if (sourceAudioLangCode != null) {
            request.setSourceAudioLanguageCode(LanguageAudioCodes.valueOf(sourceAudioLangCode));
        }

        String sourceVoiceGender = (String) originalRequest.get("source_voice_gender");
        if (sourceVoiceGender != null) {
            request.setSourceVoiceGender(SsmlVoiceGender.valueOf(sourceVoiceGender));
        }

        request.setSourceVoiceName((String) originalRequest.get("source_voice_name"));
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

    // ========================================================================
    // SELECTIVE GENERATION METHODS (Phase 2.3)
    // These methods selectively regenerate specific fields, respecting overrides
    // ========================================================================

    /**
     * Selectively generate translations for words in a session, skipping overridden words
     *
     * @param sessionId The session ID
     * @return Result containing success/failure counts and skipped words
     */
    @Async("taskExecutor")
    public CompletableFuture<SelectiveGenerationResult> generateTranslationsSelectively(Long sessionId) {
        System.out.println("[SESSION " + sessionId + "] Starting selective translation generation");
        SelectiveGenerationResult result = new SelectiveGenerationResult("translations");

        try {
            TranslationSessionEntity session = sessionService.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            Map<String, Object> sessionData = sessionService.getSessionData(sessionId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

            if (words == null || words.isEmpty()) {
                result.setMessage("No words found in session");
                return CompletableFuture.completedFuture(result);
            }

            // Get language codes from original request or use defaults
            @SuppressWarnings("unchecked")
            Map<String, Object> originalRequest = (Map<String, Object>) sessionData.get("original_request");
            String sourceLanguageCode = originalRequest != null && originalRequest.containsKey("source_language_code")
                ? (String) originalRequest.get("source_language_code")
                : session.getSourceLanguage();
            String targetLanguageCode = originalRequest != null && originalRequest.containsKey("target_language_code")
                ? (String) originalRequest.get("target_language_code")
                : session.getTargetLanguage();

            for (Map<String, Object> wordData : words) {
                String sourceWord = (String) wordData.get("source_word");
                result.incrementTotal();

                // Find the word entity
                Optional<WordEntity> wordEntityOpt = wordService.findWord(
                    sourceWord,
                    session.getSourceLanguage(),
                    session.getTargetLanguage()
                );

                if (wordEntityOpt.isPresent() && wordService.isTranslationOverridden(wordEntityOpt.get())) {
                    System.out.println("[WORD] Skipping translation for '" + sourceWord + "' (manually overridden)");
                    result.addSkipped(sourceWord, "Translation manually overridden");
                    continue;
                }

                // Generate new translation
                try {
                    com.raidrin.eme.translator.TranslationData translationData = translationService.translateText(
                        sourceWord,
                        sourceLanguageCode,
                        targetLanguageCode,
                        true // Force new translation
                    );

                    // Update word entity with new translation
                    wordService.updateTranslation(
                        sourceWord,
                        session.getSourceLanguage(),
                        session.getTargetLanguage(),
                        translationData.getTranslations()
                    );

                    result.incrementSuccess();
                    System.out.println("[WORD] Generated translation for: " + sourceWord);
                } catch (Exception e) {
                    result.incrementFailed();
                    result.addError(sourceWord, e.getMessage());
                    System.err.println("[WORD] Translation generation failed for '" + sourceWord + "': " + e.getMessage());
                }
            }

            result.setMessage("Translation generation completed: " + result.getSuccessCount() + " success, " +
                            result.getFailedCount() + " failed, " + result.getSkippedCount() + " skipped");

        } catch (Exception e) {
            result.setMessage("Translation generation failed: " + e.getMessage());
            System.err.println("[SESSION " + sessionId + "] Translation generation error: " + e.getMessage());
        }

        return CompletableFuture.completedFuture(result);
    }

    /**
     * Selectively generate transliterations for words in a session, skipping overridden words
     *
     * @param sessionId The session ID
     * @return Result containing success/failure counts and skipped words
     */
    @Async("taskExecutor")
    public CompletableFuture<SelectiveGenerationResult> generateTransliterationsSelectively(Long sessionId) {
        System.out.println("[SESSION " + sessionId + "] Starting selective transliteration generation");
        SelectiveGenerationResult result = new SelectiveGenerationResult("transliterations");

        try {
            TranslationSessionEntity session = sessionService.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            Map<String, Object> sessionData = sessionService.getSessionData(sessionId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

            if (words == null || words.isEmpty()) {
                result.setMessage("No words found in session");
                return CompletableFuture.completedFuture(result);
            }

            // Get language codes from original request or use defaults
            @SuppressWarnings("unchecked")
            Map<String, Object> originalRequest = (Map<String, Object>) sessionData.get("original_request");
            String sourceLanguageCode = originalRequest != null && originalRequest.containsKey("source_language_code")
                ? (String) originalRequest.get("source_language_code")
                : session.getSourceLanguage();
            String targetLanguageCode = originalRequest != null && originalRequest.containsKey("target_language_code")
                ? (String) originalRequest.get("target_language_code")
                : session.getTargetLanguage();

            for (Map<String, Object> wordData : words) {
                String sourceWord = (String) wordData.get("source_word");
                result.incrementTotal();

                // Find the word entity
                Optional<WordEntity> wordEntityOpt = wordService.findWord(
                    sourceWord,
                    session.getSourceLanguage(),
                    session.getTargetLanguage()
                );

                if (wordEntityOpt.isPresent() && wordService.isTransliterationOverridden(wordEntityOpt.get())) {
                    System.out.println("[WORD] Skipping transliteration for '" + sourceWord + "' (manually overridden)");
                    result.addSkipped(sourceWord, "Transliteration manually overridden");
                    continue;
                }

                // Generate new transliteration via translation service
                try {
                    com.raidrin.eme.translator.TranslationData translationData = translationService.translateText(
                        sourceWord,
                        sourceLanguageCode,
                        targetLanguageCode,
                        true
                    );

                    if (translationData.getTransliteration() != null && !translationData.getTransliteration().isEmpty()) {
                        wordService.updateTransliteration(
                            sourceWord,
                            session.getSourceLanguage(),
                            session.getTargetLanguage(),
                            translationData.getTransliteration()
                        );
                        result.incrementSuccess();
                        System.out.println("[WORD] Generated transliteration for: " + sourceWord);
                    } else {
                        result.addSkipped(sourceWord, "No transliteration available for this language");
                        System.out.println("[WORD] No transliteration available for: " + sourceWord);
                    }
                } catch (Exception e) {
                    result.incrementFailed();
                    result.addError(sourceWord, e.getMessage());
                    System.err.println("[WORD] Transliteration generation failed for '" + sourceWord + "': " + e.getMessage());
                }
            }

            result.setMessage("Transliteration generation completed: " + result.getSuccessCount() + " success, " +
                            result.getFailedCount() + " failed, " + result.getSkippedCount() + " skipped");

        } catch (Exception e) {
            result.setMessage("Transliteration generation failed: " + e.getMessage());
            System.err.println("[SESSION " + sessionId + "] Transliteration generation error: " + e.getMessage());
        }

        return CompletableFuture.completedFuture(result);
    }

    /**
     * Selectively generate mnemonic keywords for words in a session, skipping overridden words
     *
     * @param sessionId The session ID
     * @param imageStyle Optional image style for mnemonic generation
     * @return Result containing success/failure counts and skipped words
     */
    @Async("taskExecutor")
    public CompletableFuture<SelectiveGenerationResult> generateMnemonicKeywordsSelectively(
            Long sessionId, ImageStyle imageStyle) {
        System.out.println("[SESSION " + sessionId + "] Starting selective mnemonic keyword generation");
        SelectiveGenerationResult result = new SelectiveGenerationResult("mnemonic_keywords");

        try {
            TranslationSessionEntity session = sessionService.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            Map<String, Object> sessionData = sessionService.getSessionData(sessionId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

            if (words == null || words.isEmpty()) {
                result.setMessage("No words found in session");
                return CompletableFuture.completedFuture(result);
            }

            for (Map<String, Object> wordData : words) {
                String sourceWord = (String) wordData.get("source_word");
                result.incrementTotal();

                // Find the word entity
                Optional<WordEntity> wordEntityOpt = wordService.findWord(
                    sourceWord,
                    session.getSourceLanguage(),
                    session.getTargetLanguage()
                );

                if (wordEntityOpt.isEmpty()) {
                    result.addSkipped(sourceWord, "Word entity not found");
                    continue;
                }

                WordEntity wordEntity = wordEntityOpt.get();

                if (wordService.isMnemonicKeywordOverridden(wordEntity)) {
                    System.out.println("[WORD] Skipping mnemonic keyword for '" + sourceWord + "' (manually overridden)");
                    result.addSkipped(sourceWord, "Mnemonic keyword manually overridden");
                    continue;
                }

                if (!wordService.hasTranslation(wordEntity)) {
                    result.addSkipped(sourceWord, "Missing translation (prerequisite)");
                    continue;
                }

                // Generate new mnemonic keyword
                try {
                    Set<String> translations = wordService.deserializeTranslations(wordEntity.getTranslation());
                    String primaryTranslation = translations.iterator().next();

                    MnemonicData mnemonicData = mnemonicGenerationService.generateMnemonic(
                        sourceWord,
                        primaryTranslation,
                        session.getSourceLanguage(),
                        session.getTargetLanguage(),
                        wordEntity.getSourceTransliteration(),
                        imageStyle != null ? imageStyle : ImageStyle.REALISTIC_CINEMATIC
                    );

                    // Only update mnemonic keyword (don't update sentence or clear image)
                    wordService.updateMnemonic(
                        sourceWord,
                        session.getSourceLanguage(),
                        session.getTargetLanguage(),
                        mnemonicData.getMnemonicKeyword(),
                        null, // Don't update sentence
                        null  // Don't update image prompt
                    );

                    result.incrementSuccess();
                    System.out.println("[WORD] Generated mnemonic keyword for: " + sourceWord);
                } catch (Exception e) {
                    result.incrementFailed();
                    result.addError(sourceWord, e.getMessage());
                    System.err.println("[WORD] Mnemonic keyword generation failed for '" + sourceWord + "': " + e.getMessage());
                }
            }

            result.setMessage("Mnemonic keyword generation completed: " + result.getSuccessCount() + " success, " +
                            result.getFailedCount() + " failed, " + result.getSkippedCount() + " skipped");

        } catch (Exception e) {
            result.setMessage("Mnemonic keyword generation failed: " + e.getMessage());
            System.err.println("[SESSION " + sessionId + "] Mnemonic keyword generation error: " + e.getMessage());
        }

        return CompletableFuture.completedFuture(result);
    }

    /**
     * Selectively generate mnemonic sentences for words in a session
     * Checks prerequisites (translation, mnemonic keyword)
     *
     * @param sessionId The session ID
     * @param imageStyle Optional image style for mnemonic generation
     * @return Result containing success/failure counts and skipped words
     */
    @Async("taskExecutor")
    public CompletableFuture<SelectiveGenerationResult> generateMnemonicSentencesSelectively(
            Long sessionId, ImageStyle imageStyle) {
        System.out.println("[SESSION " + sessionId + "] Starting selective mnemonic sentence generation");
        SelectiveGenerationResult result = new SelectiveGenerationResult("mnemonic_sentences");

        try {
            TranslationSessionEntity session = sessionService.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            Map<String, Object> sessionData = sessionService.getSessionData(sessionId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

            if (words == null || words.isEmpty()) {
                result.setMessage("No words found in session");
                return CompletableFuture.completedFuture(result);
            }

            for (Map<String, Object> wordData : words) {
                String sourceWord = (String) wordData.get("source_word");
                result.incrementTotal();

                // Find the word entity
                Optional<WordEntity> wordEntityOpt = wordService.findWord(
                    sourceWord,
                    session.getSourceLanguage(),
                    session.getTargetLanguage()
                );

                if (wordEntityOpt.isEmpty()) {
                    result.addSkipped(sourceWord, "Word entity not found");
                    continue;
                }

                WordEntity wordEntity = wordEntityOpt.get();

                // Check prerequisites
                if (!wordService.hasTranslation(wordEntity)) {
                    result.addSkipped(sourceWord, "Missing translation (prerequisite)");
                    continue;
                }

                if (!wordService.hasMnemonicKeyword(wordEntity)) {
                    result.addSkipped(sourceWord, "Missing mnemonic keyword (prerequisite)");
                    continue;
                }

                // Generate new mnemonic sentence
                try {
                    Set<String> translations = wordService.deserializeTranslations(wordEntity.getTranslation());
                    String primaryTranslation = translations.iterator().next();

                    MnemonicData mnemonicData = mnemonicGenerationService.generateMnemonic(
                        sourceWord,
                        primaryTranslation,
                        session.getSourceLanguage(),
                        session.getTargetLanguage(),
                        wordEntity.getSourceTransliteration(),
                        imageStyle != null ? imageStyle : ImageStyle.REALISTIC_CINEMATIC
                    );

                    // Update mnemonic sentence and image prompt, clear image file
                    wordService.updateMnemonicAndClearImage(
                        sourceWord,
                        session.getSourceLanguage(),
                        session.getTargetLanguage(),
                        null, // Keep existing keyword
                        mnemonicData.getMnemonicSentence(),
                        mnemonicData.getImagePrompt()
                    );

                    result.incrementSuccess();
                    System.out.println("[WORD] Generated mnemonic sentence for: " + sourceWord);
                } catch (Exception e) {
                    result.incrementFailed();
                    result.addError(sourceWord, e.getMessage());
                    System.err.println("[WORD] Mnemonic sentence generation failed for '" + sourceWord + "': " + e.getMessage());
                }
            }

            result.setMessage("Mnemonic sentence generation completed: " + result.getSuccessCount() + " success, " +
                            result.getFailedCount() + " failed, " + result.getSkippedCount() + " skipped");

        } catch (Exception e) {
            result.setMessage("Mnemonic sentence generation failed: " + e.getMessage());
            System.err.println("[SESSION " + sessionId + "] Mnemonic sentence generation error: " + e.getMessage());
        }

        return CompletableFuture.completedFuture(result);
    }

    /**
     * Selectively generate image prompts ONLY for words in a session (without generating images).
     * This allows users to review and edit prompts before image generation.
     * Sets imagePromptStatus to GENERATED so users can review before approving.
     *
     * @param sessionId The session ID
     * @param imageStyle The image style to use
     * @return Result containing success/failure counts and skipped words
     */
    @Async("taskExecutor")
    public CompletableFuture<SelectiveGenerationResult> generateImagePromptsSelectively(
            Long sessionId, ImageStyle imageStyle) {
        System.out.println("[SESSION " + sessionId + "] Starting selective image PROMPT generation (no images)");
        SelectiveGenerationResult result = new SelectiveGenerationResult("image_prompts");

        try {
            TranslationSessionEntity session = sessionService.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            Map<String, Object> sessionData = sessionService.getSessionData(sessionId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

            if (words == null || words.isEmpty()) {
                result.setMessage("No words found in session");
                return CompletableFuture.completedFuture(result);
            }

            for (Map<String, Object> wordData : words) {
                String sourceWord = (String) wordData.get("source_word");
                result.incrementTotal();

                // Find the word entity
                Optional<WordEntity> wordEntityOpt = wordService.findWord(
                    sourceWord,
                    session.getSourceLanguage(),
                    session.getTargetLanguage()
                );

                if (wordEntityOpt.isEmpty()) {
                    result.addSkipped(sourceWord, "Word entity not found");
                    continue;
                }

                WordEntity wordEntity = wordEntityOpt.get();

                // Check prerequisites - need translation and mnemonic keyword
                if (!wordService.hasTranslation(wordEntity)) {
                    result.addSkipped(sourceWord, "Missing translation (prerequisite)");
                    continue;
                }

                if (!wordService.hasMnemonicKeyword(wordEntity)) {
                    result.addSkipped(sourceWord, "Missing mnemonic keyword (prerequisite)");
                    continue;
                }

                // Skip if already has an APPROVED prompt (don't regenerate approved prompts)
                String currentStatus = wordEntity.getImagePromptStatus();
                if ("APPROVED".equals(currentStatus) && wordEntity.getImagePrompt() != null) {
                    result.addSkipped(sourceWord, "Prompt already approved");
                    continue;
                }

                // Generate mnemonic sentence and image prompt
                try {
                    Set<String> translations = wordService.deserializeTranslations(wordEntity.getTranslation());
                    String primaryTranslation = translations.iterator().next();

                    MnemonicData mnemonicData = mnemonicGenerationService.generateMnemonicFromKeyword(
                        wordEntity.getMnemonicKeyword(),
                        sourceWord,
                        primaryTranslation,
                        session.getSourceLanguage(),
                        session.getTargetLanguage(),
                        wordEntity.getSourceTransliteration(),
                        imageStyle != null ? imageStyle : ImageStyle.REALISTIC_CINEMATIC
                    );

                    // Update mnemonic sentence and image prompt, set status to GENERATED
                    wordService.updateMnemonic(
                        sourceWord,
                        session.getSourceLanguage(),
                        session.getTargetLanguage(),
                        null, // Keep existing keyword
                        mnemonicData.getMnemonicSentence(),
                        mnemonicData.getImagePrompt()
                    );

                    // Set prompt status to GENERATED (ready for review)
                    wordService.updateImagePromptStatus(
                        sourceWord,
                        session.getSourceLanguage(),
                        session.getTargetLanguage(),
                        "GENERATED"
                    );

                    result.incrementSuccess();
                    System.out.println("[WORD] Generated image prompt for: " + sourceWord);
                } catch (Exception e) {
                    result.incrementFailed();
                    result.addError(sourceWord, e.getMessage());
                    System.err.println("[WORD] Image prompt generation failed for '" + sourceWord + "': " + e.getMessage());
                }
            }

            result.setMessage("Image prompt generation completed: " + result.getSuccessCount() + " success, " +
                            result.getFailedCount() + " failed, " + result.getSkippedCount() + " skipped");

        } catch (Exception e) {
            result.setMessage("Image prompt generation failed: " + e.getMessage());
            System.err.println("[SESSION " + sessionId + "] Image prompt generation error: " + e.getMessage());
        }

        return CompletableFuture.completedFuture(result);
    }

    /**
     * Generate images ONLY for words that have APPROVED image prompts.
     * This is the second step after reviewing/editing prompts.
     *
     * @param sessionId The session ID
     * @return Result containing success/failure counts and skipped words
     */
    @Async("taskExecutor")
    public CompletableFuture<SelectiveGenerationResult> generateImagesFromApprovedPrompts(Long sessionId) {
        System.out.println("[SESSION " + sessionId + "] Starting image generation from APPROVED prompts only");
        SelectiveGenerationResult result = new SelectiveGenerationResult("images_from_prompts");

        try {
            TranslationSessionEntity session = sessionService.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            Map<String, Object> sessionData = sessionService.getSessionData(sessionId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

            if (words == null || words.isEmpty()) {
                result.setMessage("No words found in session");
                return CompletableFuture.completedFuture(result);
            }

            for (Map<String, Object> wordData : words) {
                String sourceWord = (String) wordData.get("source_word");
                result.incrementTotal();

                // Find the word entity
                Optional<WordEntity> wordEntityOpt = wordService.findWord(
                    sourceWord,
                    session.getSourceLanguage(),
                    session.getTargetLanguage()
                );

                if (wordEntityOpt.isEmpty()) {
                    result.addSkipped(sourceWord, "Word entity not found");
                    continue;
                }

                WordEntity wordEntity = wordEntityOpt.get();

                // Only process words with APPROVED prompts
                String promptStatus = wordEntity.getImagePromptStatus();
                if (!"APPROVED".equals(promptStatus)) {
                    result.addSkipped(sourceWord, "Prompt not approved (status: " + promptStatus + ")");
                    continue;
                }

                // Check we have a prompt
                if (wordEntity.getImagePrompt() == null || wordEntity.getImagePrompt().trim().isEmpty()) {
                    result.addSkipped(sourceWord, "No image prompt available");
                    continue;
                }

                // Skip if already has an image
                if (wordEntity.getImageFile() != null && !wordEntity.getImageFile().trim().isEmpty()) {
                    result.addSkipped(sourceWord, "Image already exists");
                    continue;
                }

                // Generate image from approved prompt
                try {
                    String sanitizedPrompt = mnemonicGenerationService.sanitizeImagePrompt(wordEntity.getImagePrompt());
                    String imageFileName = FileNameSanitizer.fromMnemonicSentence(
                        wordEntity.getMnemonicSentence() != null ? wordEntity.getMnemonicSentence() : sourceWord,
                        "jpg"
                    );

                    OpenAiImageService.GeneratedImage openAiImage = openAiImageService.generateImage(
                        sanitizedPrompt, "1536x1024", "medium", null);

                    // Download image to local file
                    downloadImageToLocal(openAiImage.getImageUrl(), imageFileName);

                    // Upload to GCP
                    gcpStorageService.downloadAndUpload(openAiImage.getImageUrl(), imageFileName);

                    // Update word with image file
                    wordService.updateImage(
                        sourceWord,
                        session.getSourceLanguage(),
                        session.getTargetLanguage(),
                        imageFileName,
                        wordEntity.getImagePrompt()
                    );

                    // Update image status to COMPLETED
                    wordService.updateImageStatus(
                        sourceWord,
                        session.getSourceLanguage(),
                        session.getTargetLanguage(),
                        "COMPLETED"
                    );

                    result.incrementSuccess();
                    System.out.println("[WORD] Generated image for: " + sourceWord);
                } catch (Exception e) {
                    result.incrementFailed();
                    result.addError(sourceWord, e.getMessage());
                    System.err.println("[WORD] Image generation failed for '" + sourceWord + "': " + e.getMessage());

                    // Update image status to FAILED
                    wordService.updateImageStatus(
                        sourceWord,
                        session.getSourceLanguage(),
                        session.getTargetLanguage(),
                        "FAILED"
                    );
                }
            }

            result.setMessage("Image generation completed: " + result.getSuccessCount() + " success, " +
                            result.getFailedCount() + " failed, " + result.getSkippedCount() + " skipped");

        } catch (Exception e) {
            result.setMessage("Image generation failed: " + e.getMessage());
            System.err.println("[SESSION " + sessionId + "] Image generation error: " + e.getMessage());
        }

        return CompletableFuture.completedFuture(result);
    }

    /**
     * Selectively generate images for words in a session
     * Validates prerequisites (translation, mnemonic keyword, mnemonic sentence)
     *
     * @param sessionId The session ID
     * @return Result containing success/failure counts and skipped words
     */
    @Async("taskExecutor")
    public CompletableFuture<SelectiveGenerationResult> generateImagesSelectively(Long sessionId) {
        System.out.println("[SESSION " + sessionId + "] Starting selective image generation");
        SelectiveGenerationResult result = new SelectiveGenerationResult("images");

        try {
            TranslationSessionEntity session = sessionService.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            Map<String, Object> sessionData = sessionService.getSessionData(sessionId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

            if (words == null || words.isEmpty()) {
                result.setMessage("No words found in session");
                return CompletableFuture.completedFuture(result);
            }

            for (Map<String, Object> wordData : words) {
                String sourceWord = (String) wordData.get("source_word");
                result.incrementTotal();

                // Find the word entity
                Optional<WordEntity> wordEntityOpt = wordService.findWord(
                    sourceWord,
                    session.getSourceLanguage(),
                    session.getTargetLanguage()
                );

                if (wordEntityOpt.isEmpty()) {
                    result.addSkipped(sourceWord, "Word entity not found");
                    continue;
                }

                WordEntity wordEntity = wordEntityOpt.get();

                // Check prerequisites using WordService helper
                if (!wordService.hasImagePrerequisites(wordEntity)) {
                    List<String> missing = new ArrayList<>();
                    if (!wordService.hasTranslation(wordEntity)) missing.add("translation");
                    if (!wordService.hasMnemonicKeyword(wordEntity)) missing.add("mnemonic keyword");
                    if (!wordService.hasMnemonicSentence(wordEntity)) missing.add("mnemonic sentence");

                    result.addSkipped(sourceWord, "Missing prerequisites: " + String.join(", ", missing));
                    continue;
                }

                // Generate image
                try {
                    // Sanitize the image prompt before sending to image generation API
                    String sanitizedPrompt = mnemonicGenerationService.sanitizeImagePrompt(wordEntity.getImagePrompt());

                    // Generate image using OpenAI
                    String size = "1536x1024"; // Landscape format
                    OpenAiImageService.GeneratedImage generatedImage = openAiImageService.generateImage(
                        sanitizedPrompt, size, "medium", null
                    );

                    // Download and save image
                    String imageFileName = FileNameSanitizer.fromMnemonicSentence(
                        wordEntity.getMnemonicSentence(), "jpg"
                    );

                    Path localImagePath = downloadImageToLocal(generatedImage.getImageUrl(), imageFileName);
                    String gcsUrl = gcpStorageService.downloadAndUpload(generatedImage.getImageUrl(), imageFileName);

                    // Update word entity with image
                    wordService.updateImage(
                        sourceWord,
                        session.getSourceLanguage(),
                        session.getTargetLanguage(),
                        imageFileName,
                        wordEntity.getImagePrompt()
                    );

                    result.incrementSuccess();
                    System.out.println("[WORD] Generated image for: " + sourceWord);
                } catch (Exception e) {
                    result.incrementFailed();
                    result.addError(sourceWord, e.getMessage());
                    System.err.println("[WORD] Image generation failed for '" + sourceWord + "': " + e.getMessage());
                }
            }

            result.setMessage("Image generation completed: " + result.getSuccessCount() + " success, " +
                            result.getFailedCount() + " failed, " + result.getSkippedCount() + " skipped");

        } catch (Exception e) {
            result.setMessage("Image generation failed: " + e.getMessage());
            System.err.println("[SESSION " + sessionId + "] Image generation error: " + e.getMessage());
        }

        return CompletableFuture.completedFuture(result);
    }

    /**
     * Selectively generate source audio for words in a session
     *
     * @param sessionId The session ID
     * @return Result containing success/failure counts and skipped words
     */
    @Async("taskExecutor")
    public CompletableFuture<SelectiveGenerationResult> generateAudioSelectively(Long sessionId) {
        System.out.println("[SESSION " + sessionId + "] Starting selective audio generation (source only)");
        SelectiveGenerationResult result = new SelectiveGenerationResult("audio");

        try {
            TranslationSessionEntity session = sessionService.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            Map<String, Object> sessionData = sessionService.getSessionData(sessionId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

            if (words == null || words.isEmpty()) {
                result.setMessage("No words found in session");
                return CompletableFuture.completedFuture(result);
            }

            List<AsyncAudioGenerationService.AudioRequest> audioRequests = new ArrayList<>();
            Set<String> processedAudioFiles = new HashSet<>();

            // Get audio language code from original request or use defaults
            @SuppressWarnings("unchecked")
            Map<String, Object> originalRequest = (Map<String, Object>) sessionData.get("original_request");
            String audioLangCodeStr = originalRequest != null && originalRequest.containsKey("source_audio_language_code")
                ? (String) originalRequest.get("source_audio_language_code")
                : session.getSourceLanguage();
            LanguageAudioCodes audioLangCode;
            try {
                audioLangCode = LanguageAudioCodes.valueOf(audioLangCodeStr);
            } catch (IllegalArgumentException e) {
                // Fallback if enum value is invalid
                audioLangCode = LanguageAudioCodes.English;
            }

            for (Map<String, Object> wordData : words) {
                String sourceWord = (String) wordData.get("source_word");
                result.incrementTotal();

                String sourceAudioFileName = Codec.encodeForAudioFileName(sourceWord);

                if (processedAudioFiles.contains(sourceAudioFileName)) {
                    result.addSkipped(sourceWord, "Audio file already queued");
                    continue;
                }

                audioRequests.add(new AsyncAudioGenerationService.AudioRequest(
                    sourceWord,
                    audioLangCode,
                    SsmlVoiceGender.NEUTRAL,
                    null,
                    sourceAudioFileName
                ));
                processedAudioFiles.add(sourceAudioFileName);
            }

            // Generate all audio files in parallel
            if (!audioRequests.isEmpty()) {
                CompletableFuture<List<AsyncAudioGenerationService.AudioResult>> audioFuture =
                    audioGenerationService.generateAudioFilesParallel(audioRequests);

                List<AsyncAudioGenerationService.AudioResult> audioResults = audioFuture.get();
                for (AsyncAudioGenerationService.AudioResult audioResult : audioResults) {
                    if (audioResult.getLocalFilePath() != null) {
                        result.incrementSuccess();
                    } else {
                        result.incrementFailed();
                        result.addError(audioResult.getFileName(), "Audio generation failed");
                    }
                }
            }

            result.setMessage("Audio generation completed: " + result.getSuccessCount() + " success, " +
                            result.getFailedCount() + " failed, " + result.getSkippedCount() + " skipped");

        } catch (Exception e) {
            result.setMessage("Audio generation failed: " + e.getMessage());
            System.err.println("[SESSION " + sessionId + "] Audio generation error: " + e.getMessage());
        }

        return CompletableFuture.completedFuture(result);
    }

    /**
     * Selectively generate example sentences for words in a session
     * Checks prerequisites (translation)
     *
     * @param sessionId The session ID
     * @return Result containing success/failure counts and skipped words
     */
    @Async("taskExecutor")
    public CompletableFuture<SelectiveGenerationResult> generateExampleSentencesSelectively(Long sessionId) {
        System.out.println("[SESSION " + sessionId + "] Starting selective example sentence generation");
        SelectiveGenerationResult result = new SelectiveGenerationResult("example_sentences");

        try {
            TranslationSessionEntity session = sessionService.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            Map<String, Object> sessionData = sessionService.getSessionData(sessionId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

            if (words == null || words.isEmpty()) {
                result.setMessage("No words found in session");
                return CompletableFuture.completedFuture(result);
            }

            for (Map<String, Object> wordData : words) {
                String sourceWord = (String) wordData.get("source_word");
                result.incrementTotal();

                // Find the word entity
                Optional<WordEntity> wordEntityOpt = wordService.findWord(
                    sourceWord,
                    session.getSourceLanguage(),
                    session.getTargetLanguage()
                );

                if (wordEntityOpt.isEmpty()) {
                    result.addSkipped(sourceWord, "Word entity not found");
                    continue;
                }

                WordEntity wordEntity = wordEntityOpt.get();

                // Check prerequisites
                if (!wordService.hasTranslation(wordEntity)) {
                    result.addSkipped(sourceWord, "Missing translation (prerequisite)");
                    continue;
                }

                // Generate example sentence
                try {
                    SentenceData sentenceData = sentenceGenerationService.generateSentence(
                        sourceWord,
                        session.getSourceLanguage(),
                        session.getTargetLanguage()
                    );

                    if (sentenceData != null) {
                        // Generate sentence audio
                        if (sentenceData.getSourceLanguageSentence() != null) {
                            String sentenceAudioFileName = Codec.encodeForAudioFileName(
                                sentenceData.getSourceLanguageSentence()
                            ) + ".mp3";
                            sentenceData.setAudioFile(sentenceAudioFileName);

                            // Save sentence to database
                            sentenceStorageService.saveSentence(
                                sourceWord,
                                session.getSourceLanguage(),
                                session.getTargetLanguage(),
                                sentenceData
                            );

                            result.incrementSuccess();
                            System.out.println("[WORD] Generated example sentence for: " + sourceWord);
                        }
                    } else {
                        result.addSkipped(sourceWord, "Sentence generation returned null");
                    }
                } catch (Exception e) {
                    result.incrementFailed();
                    result.addError(sourceWord, e.getMessage());
                    System.err.println("[WORD] Example sentence generation failed for '" + sourceWord + "': " + e.getMessage());
                }
            }

            result.setMessage("Example sentence generation completed: " + result.getSuccessCount() + " success, " +
                            result.getFailedCount() + " failed, " + result.getSkippedCount() + " skipped");

        } catch (Exception e) {
            result.setMessage("Example sentence generation failed: " + e.getMessage());
            System.err.println("[SESSION " + sessionId + "] Example sentence generation error: " + e.getMessage());
        }

        return CompletableFuture.completedFuture(result);
    }

    /**
     * Result object for selective generation operations
     */
    @Data
    public static class SelectiveGenerationResult {
        private final String operationType;
        private int totalCount = 0;
        private int successCount = 0;
        private int failedCount = 0;
        private int skippedCount = 0;
        private List<SkippedWord> skippedWords = new ArrayList<>();
        private List<FailedWord> failedWords = new ArrayList<>();
        private String message;

        public SelectiveGenerationResult(String operationType) {
            this.operationType = operationType;
        }

        public void incrementTotal() { totalCount++; }
        public void incrementSuccess() { successCount++; }
        public void incrementFailed() { failedCount++; }

        public void addSkipped(String word, String reason) {
            skippedCount++;
            skippedWords.add(new SkippedWord(word, reason));
        }

        public void addError(String word, String error) {
            failedWords.add(new FailedWord(word, error));
        }

        @Data
        public static class SkippedWord {
            private final String word;
            private final String reason;
        }

        @Data
        public static class FailedWord {
            private final String word;
            private final String error;
        }
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

        // Audio configuration (source only)
        private boolean enableSourceAudio;
        private LanguageAudioCodes sourceAudioLanguageCode;
        private SsmlVoiceGender sourceVoiceGender;
        private String sourceVoiceName;

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
