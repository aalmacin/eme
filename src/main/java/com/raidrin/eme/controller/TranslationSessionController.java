package com.raidrin.eme.controller;

import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.raidrin.eme.anki.AnkiCardBuilderService;
import com.raidrin.eme.anki.AnkiNoteCreatorService;
import com.raidrin.eme.anki.CardItem;
import com.raidrin.eme.audio.AsyncAudioGenerationService;
import com.raidrin.eme.audio.LanguageAudioCodes;
import com.raidrin.eme.codec.Codec;
import com.raidrin.eme.session.SessionOrchestrationService;
import com.raidrin.eme.storage.entity.AnkiFormatEntity;
import com.raidrin.eme.storage.entity.CharacterGuideEntity;
import com.raidrin.eme.storage.entity.TranslationSessionEntity;
import com.raidrin.eme.storage.entity.WordEntity;
import com.raidrin.eme.storage.service.CharacterGuideService;
import com.raidrin.eme.storage.service.SentenceStorageService;
import com.raidrin.eme.storage.service.TranslationSessionService;
import com.raidrin.eme.storage.service.WordService;
import com.raidrin.eme.sentence.SentenceData;
import com.raidrin.eme.sentence.SentenceGenerationService;
import com.raidrin.eme.translator.TranslationService;
import com.raidrin.eme.translator.TranslationData;
import com.raidrin.eme.util.ZipFileGenerator;
import com.raidrin.eme.util.FileNameSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class TranslationSessionController {

    private final TranslationSessionService translationSessionService;
    private final WordService wordService;
    private final ZipFileGenerator zipFileGenerator;
    private final AnkiNoteCreatorService ankiNoteCreatorService;
    private final SessionOrchestrationService sessionOrchestrationService;
    private final CharacterGuideService characterGuideService;
    private final TranslationService translationService;
    private final AsyncAudioGenerationService audioGenerationService;
    private final SentenceStorageService sentenceStorageService;
    private final SentenceGenerationService sentenceGenerationService;
    private final com.raidrin.eme.anki.AnkiCardBuilderService ankiCardBuilderService;
    private final com.raidrin.eme.storage.service.AnkiFormatService ankiFormatService;
    private final com.raidrin.eme.mnemonic.MnemonicGenerationService mnemonicGenerationService;
    private final com.raidrin.eme.image.OpenAiImageService openAiImageService;
    private final com.raidrin.eme.storage.service.GcpStorageService gcpStorageService;

    @Value("${image.output.directory:./generated_images}")
    private String imageOutputDirectory;

    @GetMapping
    public String listSessions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<TranslationSessionEntity> sessionPage = translationSessionService.findAll(pageable);
        List<TranslationSessionEntity> sessions = sessionPage.getContent();

        // Enrich sessions with transliteration data
        Map<Long, String> transliterations = new HashMap<>();
        for (TranslationSessionEntity session : sessions) {
            Optional<WordEntity> wordEntity = wordService.findWord(
                    session.getWord(),
                    session.getSourceLanguage(),
                    session.getTargetLanguage()
            );
            if (wordEntity.isPresent() && wordEntity.get().getSourceTransliteration() != null) {
                transliterations.put(session.getId(), wordEntity.get().getSourceTransliteration());
            }
        }

        model.addAttribute("sessions", sessions);
        model.addAttribute("transliterations", transliterations);
        model.addAttribute("page", sessionPage);
        return "sessions/list";
    }

    @GetMapping("/{id}")
    public String viewSession(@PathVariable Long id,
                             @RequestParam(defaultValue = "0") int wordPage,
                             @RequestParam(defaultValue = "20") int wordSize,
                             Model model) {
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);
        if (sessionOpt.isPresent()) {
            TranslationSessionEntity session = sessionOpt.get();
            Map<String, Object> sessionData = translationSessionService.getSessionData(id);

            // Enrich word data with word IDs and latest data from WordEntity
            List<Map<String, Object>> allWords = new ArrayList<>();
            int totalWords = 0;

            if (sessionData.containsKey("words")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");
                totalWords = words.size();

                for (Map<String, Object> wordData : words) {
                    String sourceWord = (String) wordData.get("source_word");
                    if (sourceWord != null) {
                        Optional<WordEntity> wordEntity = wordService.findWord(
                                sourceWord,
                                session.getSourceLanguage(),
                                session.getTargetLanguage()
                        );
                        if (wordEntity.isPresent()) {
                            // Add word ID
                            wordData.put("word_id", wordEntity.get().getId());

                            // Merge with latest data to show updated images and other regenerated content
                            Map<String, Object> mergedData = mergeWithLatestWordData(
                                    wordData,
                                    session.getSourceLanguage(),
                                    session.getTargetLanguage()
                            );
                            wordData.putAll(mergedData);
                        }

                        // Add character guide information
                        enrichWithCharacterGuideInfo(wordData, session.getSourceLanguage(), session.getTargetLanguage());
                    }
                }

                allWords = words;

                // Update audio count in process_summary to reflect current state
                updateAudioCountInProcessSummary(sessionData, words);
            }

            // Apply pagination to words list
            int startIndex = wordPage * wordSize;
            int endIndex = Math.min(startIndex + wordSize, totalWords);
            List<Map<String, Object>> paginatedWords = new ArrayList<>();

            if (startIndex < totalWords) {
                paginatedWords = allWords.subList(startIndex, endIndex);
            }

            // Replace words in sessionData with paginated words
            sessionData.put("words", paginatedWords);

            // Calculate pagination info
            int totalPages = (int) Math.ceil((double) totalWords / wordSize);

            // Extract source words from session data for display
            String sourceWordsText = "";
            if (sessionData != null && sessionData.containsKey("original_request")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> originalRequest = (Map<String, Object>) sessionData.get("original_request");
                if (originalRequest != null && originalRequest.containsKey("source_words")) {
                    @SuppressWarnings("unchecked")
                    List<String> sourceWords = (List<String>) originalRequest.get("source_words");
                    if (sourceWords != null && !sourceWords.isEmpty()) {
                        sourceWordsText = String.join("\n", sourceWords);
                    }
                }
            }

            model.addAttribute("translationSession", session);
            model.addAttribute("sessionData", sessionData);
            model.addAttribute("sourceWordsText", sourceWordsText);

            // Add pagination attributes
            model.addAttribute("wordPage", wordPage);
            model.addAttribute("wordSize", wordSize);
            model.addAttribute("totalWords", totalWords);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("startIndex", startIndex + 1); // 1-based for display
            model.addAttribute("endIndex", endIndex);

            return "sessions/view";
        } else {
            return "redirect:/sessions?error=not-found";
        }
    }

    @PostMapping("/{id}/retry-word-translation")
    public String retryWordTranslation(@PathVariable Long id, @RequestParam int wordIndex) {
        // TODO: Implement individual word translation retry
        return "redirect:/sessions/" + id + "?message=word-translation-retry-not-implemented";
    }

    @PostMapping("/{id}/retry-word-sentence")
    @SuppressWarnings("unchecked")
    public String retryWordSentence(@PathVariable Long id, @RequestParam int wordIndex) {
        return regenerateSentenceForWord(id, wordIndex);
    }

    @PostMapping("/{id}/regenerate-sentence")
    @SuppressWarnings("unchecked")
    public String regenerateSentence(@PathVariable Long id, @RequestParam int wordIndex) {
        return regenerateSentenceForWord(id, wordIndex);
    }

    private String regenerateSentenceForWord(Long id, int wordIndex) {
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);

        if (sessionOpt.isEmpty()) {
            return "redirect:/sessions?error=session-not-found";
        }

        TranslationSessionEntity session = sessionOpt.get();
        Map<String, Object> sessionData = translationSessionService.getSessionData(id);

        // Get words from session data
        if (!sessionData.containsKey("words")) {
            return "redirect:/sessions/" + id + "?error=no-words-in-session";
        }

        List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

        if (wordIndex < 0 || wordIndex >= words.size()) {
            return "redirect:/sessions/" + id + "?error=invalid-word-index";
        }

        Map<String, Object> wordData = words.get(wordIndex);
        String sourceWord = (String) wordData.get("source_word");

        if (sourceWord == null || sourceWord.trim().isEmpty()) {
            return "redirect:/sessions/" + id + "?error=invalid-source-word";
        }

        try {
            // Regenerate sentence using the service
            SentenceData sentenceData = sentenceGenerationService.regenerateSentence(
                    sourceWord,
                    session.getSourceLanguage(),
                    session.getTargetLanguage()
            );

            // Generate audio for the sentence
            if (sentenceData.getSourceLanguageSentence() != null) {
                String sentenceAudioFileName = Codec.encodeForAudioFileName(sentenceData.getSourceLanguageSentence());
                String sentenceAudioFileNameWithExt = sentenceAudioFileName + ".mp3";

                // Get audio settings from session's original request
                Map<String, Object> originalRequest = sessionData.containsKey("original_request")
                    ? (Map<String, Object>) sessionData.get("original_request")
                    : new HashMap<>();

                String audioLangCodeStr = (String) originalRequest.get("source_audio_language_code");
                String voiceGenderStr = (String) originalRequest.get("source_voice_gender");
                String voiceName = (String) originalRequest.get("source_voice_name");

                LanguageAudioCodes audioLangCode = audioLangCodeStr != null
                    ? LanguageAudioCodes.valueOf(audioLangCodeStr)
                    : getDefaultAudioCode(session.getSourceLanguage());

                SsmlVoiceGender voiceGender = voiceGenderStr != null
                    ? SsmlVoiceGender.valueOf(voiceGenderStr)
                    : SsmlVoiceGender.FEMALE;

                // Generate audio file
                AsyncAudioGenerationService.AudioRequest audioRequest =
                    new AsyncAudioGenerationService.AudioRequest(
                        sentenceData.getSourceLanguageSentence(),
                        audioLangCode,
                        voiceGender,
                        voiceName,
                        sentenceAudioFileName
                    );

                try {
                    audioGenerationService.generateAudioFileAsync(audioRequest).get();
                    sentenceData.setAudioFile(sentenceAudioFileNameWithExt);

                    // Save updated sentence data with audio file to database
                    sentenceStorageService.saveSentence(
                        sourceWord,
                        session.getSourceLanguage(),
                        session.getTargetLanguage(),
                        sentenceData
                    );
                } catch (Exception audioEx) {
                    System.err.println("Failed to generate audio for sentence: " + audioEx.getMessage());
                    // Continue even if audio generation fails
                }
            }

            // Convert sentence data to map format
            Map<String, String> sentenceMap = new HashMap<>();
            sentenceMap.put("source_language_sentence", sentenceData.getSourceLanguageSentence());
            sentenceMap.put("source_language_structure", sentenceData.getSourceLanguageStructure());
            sentenceMap.put("target_language_sentence", sentenceData.getTargetLanguageSentence());
            sentenceMap.put("target_language_latin", sentenceData.getTargetLanguageLatinCharacters());
            sentenceMap.put("target_language_transliteration", sentenceData.getTargetLanguageTransliteration());

            // Update word data with new sentence
            wordData.put("sentence_data", sentenceMap);
            wordData.put("sentence_status", "success");
            if (sentenceData.getAudioFile() != null) {
                wordData.put("sentence_audio_file", sentenceData.getAudioFile());
            }
            wordData.remove("sentence_error"); // Clear any previous error

            // Update session data
            words.set(wordIndex, wordData);
            sessionData.put("words", words);
            translationSessionService.updateSessionData(id, sessionData);

            System.out.println("Regenerated sentence for word: " + sourceWord + " in session " + id);
            return "redirect:/sessions/" + id + "?message=sentence-regenerated";

        } catch (Exception e) {
            System.err.println("Failed to regenerate sentence: " + e.getMessage());
            e.printStackTrace();

            // Update word data with error
            wordData.put("sentence_status", "failed");
            wordData.put("sentence_error", e.getMessage());

            // Update session data
            words.set(wordIndex, wordData);
            sessionData.put("words", words);
            translationSessionService.updateSessionData(id, sessionData);

            return "redirect:/sessions/" + id + "?error=sentence-regeneration-failed";
        }
    }

    @PostMapping("/{id}/regenerate-all-sentences")
    @SuppressWarnings("unchecked")
    public String regenerateAllSentences(@PathVariable Long id) {
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);

        if (sessionOpt.isEmpty()) {
            return "redirect:/sessions?error=session-not-found";
        }

        TranslationSessionEntity session = sessionOpt.get();
        Map<String, Object> sessionData = translationSessionService.getSessionData(id);

        // Get words from session data
        if (!sessionData.containsKey("words")) {
            return "redirect:/sessions/" + id + "?error=no-words-in-session";
        }

        List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

        if (words.isEmpty()) {
            return "redirect:/sessions/" + id + "?error=no-words-to-regenerate";
        }

        // Get audio settings from session's original request
        Map<String, Object> originalRequest = sessionData.containsKey("original_request")
            ? (Map<String, Object>) sessionData.get("original_request")
            : new HashMap<>();

        String audioLangCodeStr = (String) originalRequest.get("source_audio_language_code");
        String voiceGenderStr = (String) originalRequest.get("source_voice_gender");
        String voiceName = (String) originalRequest.get("source_voice_name");

        LanguageAudioCodes audioLangCode = audioLangCodeStr != null
            ? LanguageAudioCodes.valueOf(audioLangCodeStr)
            : getDefaultAudioCode(session.getSourceLanguage());

        SsmlVoiceGender voiceGender = voiceGenderStr != null
            ? SsmlVoiceGender.valueOf(voiceGenderStr)
            : SsmlVoiceGender.FEMALE;

        int successCount = 0;
        int failureCount = 0;
        List<AsyncAudioGenerationService.AudioRequest> audioRequests = new ArrayList<>();
        Set<String> processedAudioFiles = new HashSet<>();

        // Process all words
        for (Map<String, Object> wordData : words) {
            String sourceWord = (String) wordData.get("source_word");

            if (sourceWord == null || sourceWord.trim().isEmpty()) {
                continue;
            }

            try {
                // Regenerate sentence using the service
                SentenceData sentenceData = sentenceGenerationService.regenerateSentence(
                        sourceWord,
                        session.getSourceLanguage(),
                        session.getTargetLanguage()
                );

                // Prepare audio generation for the sentence
                if (sentenceData.getSourceLanguageSentence() != null) {
                    String sentenceAudioFileName = Codec.encodeForAudioFileName(sentenceData.getSourceLanguageSentence());
                    String sentenceAudioFileNameWithExt = sentenceAudioFileName + ".mp3";

                    sentenceData.setAudioFile(sentenceAudioFileNameWithExt);

                    // Save updated sentence data with audio file to database
                    sentenceStorageService.saveSentence(
                        sourceWord,
                        session.getSourceLanguage(),
                        session.getTargetLanguage(),
                        sentenceData
                    );

                    // Add to batch audio generation if not already processed
                    if (!processedAudioFiles.contains(sentenceAudioFileName)) {
                        audioRequests.add(new AsyncAudioGenerationService.AudioRequest(
                            sentenceData.getSourceLanguageSentence(),
                            audioLangCode,
                            voiceGender,
                            voiceName,
                            sentenceAudioFileName
                        ));
                        processedAudioFiles.add(sentenceAudioFileName);
                    }

                    wordData.put("sentence_audio_file", sentenceAudioFileNameWithExt);
                }

                // Convert sentence data to map format
                Map<String, String> sentenceMap = new HashMap<>();
                sentenceMap.put("source_language_sentence", sentenceData.getSourceLanguageSentence());
                sentenceMap.put("source_language_structure", sentenceData.getSourceLanguageStructure());
                sentenceMap.put("target_language_sentence", sentenceData.getTargetLanguageSentence());
                sentenceMap.put("target_language_latin", sentenceData.getTargetLanguageLatinCharacters());
                sentenceMap.put("target_language_transliteration", sentenceData.getTargetLanguageTransliteration());

                // Update word data with new sentence
                wordData.put("sentence_data", sentenceMap);
                wordData.put("sentence_status", "success");
                wordData.remove("sentence_error");

                successCount++;
                System.out.println("Regenerated sentence for word: " + sourceWord);

            } catch (Exception e) {
                System.err.println("Failed to regenerate sentence for word '" + sourceWord + "': " + e.getMessage());
                wordData.put("sentence_status", "failed");
                wordData.put("sentence_error", e.getMessage());
                failureCount++;
            }
        }

        // Generate all audio files in batch
        if (!audioRequests.isEmpty()) {
            try {
                System.out.println("Generating audio for " + audioRequests.size() + " sentences...");
                audioGenerationService.generateAudioFilesAsync(audioRequests).get();
                System.out.println("Audio generation completed for all sentences");
            } catch (Exception audioEx) {
                System.err.println("Failed to generate audio files: " + audioEx.getMessage());
                // Continue even if audio generation fails
            }
        }

        // Update session data
        sessionData.put("words", words);
        translationSessionService.updateSessionData(id, sessionData);

        System.out.println("Regenerated sentences for session " + id +
                " - Success: " + successCount + ", Failed: " + failureCount);

        if (failureCount > 0) {
            return "redirect:/sessions/" + id + "?message=sentences-regenerated-with-errors&success=" + successCount + "&failed=" + failureCount;
        } else {
            return "redirect:/sessions/" + id + "?message=all-sentences-regenerated&count=" + successCount;
        }
    }

    @PostMapping("/{id}/retry-word-image")
    public String retryWordImage(@PathVariable Long id,
                                 @RequestParam int wordIndex,
                                 @RequestParam(required = false) String imagePromptOverride) {
        // TODO: Implement individual word image retry with optional prompt override
        return "redirect:/sessions/" + id + "?message=word-image-retry-not-implemented";
    }

    @PostMapping("/{id}/cancel")
    public String cancelSession(@PathVariable Long id,
                                @RequestParam(required = false) String reason) {
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);

        if (sessionOpt.isEmpty()) {
            return "redirect:/sessions?error=session-not-found";
        }

        // Session cancellation is no longer supported (status tracking removed)
        return "redirect:/sessions/" + id + "?message=cancellation-not-supported";
    }

    @PostMapping("/{id}/retry")
    @SuppressWarnings("unchecked")
    public String retrySession(@PathVariable Long id) {
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);

        if (sessionOpt.isEmpty()) {
            return "redirect:/sessions?error=session-not-found";
        }

        // Get session data
        Map<String, Object> sessionData = translationSessionService.getSessionData(id);

        // Check if we have the original request data
        if (!sessionData.containsKey("original_request")) {
            return "redirect:/sessions/" + id + "?error=cannot-retry-old-session";
        }

        try {
            // Reconstruct the request
            SessionOrchestrationService.BatchProcessingRequest request =
                    sessionOrchestrationService.reconstructRequestFromSessionData(sessionData);

            // Clear error/cancellation data from previous attempt
            Map<String, Object> updatedSessionData = new HashMap<>(sessionData);

            // Clear error data
            updatedSessionData.remove("error");
            updatedSessionData.remove("errorTime");

            // Clear process summary errors
            if (updatedSessionData.containsKey("process_summary")) {
                Map<String, Object> processSummary = (Map<String, Object>) updatedSessionData.get("process_summary");
                processSummary.put("translation_errors", new ArrayList<>());
                processSummary.put("audio_errors", new ArrayList<>());
                processSummary.put("image_errors", new ArrayList<>());
                processSummary.put("sentence_errors", new ArrayList<>());
                processSummary.put("has_errors", false);
            }

            // Add retry metadata
            updatedSessionData.put("retry_count",
                ((Integer) updatedSessionData.getOrDefault("retry_count", 0)) + 1);
            updatedSessionData.put("last_retry_time", LocalDateTime.now().toString());

            translationSessionService.updateSessionData(id, updatedSessionData);

            // Start async processing
            sessionOrchestrationService.processTranslationBatchAsync(id, request);

            System.out.println("Started retry processing for session " + id);
            return "redirect:/sessions/" + id + "?message=retry-started";

        } catch (Exception e) {
            System.err.println("Failed to retry session: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/sessions/" + id + "?error=retry-failed";
        }
    }

    @PostMapping("/{id}/regenerate-audio")
    @SuppressWarnings("unchecked")
    public String regenerateAudio(@PathVariable Long id, @RequestParam int wordIndex) {
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);

        if (sessionOpt.isEmpty()) {
            return "redirect:/sessions?error=session-not-found";
        }

        TranslationSessionEntity session = sessionOpt.get();
        Map<String, Object> sessionData = translationSessionService.getSessionData(id);

        // Get words from session data
        if (!sessionData.containsKey("words")) {
            return "redirect:/sessions/" + id + "?error=no-words-in-session";
        }

        List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

        if (wordIndex < 0 || wordIndex >= words.size()) {
            return "redirect:/sessions/" + id + "?error=invalid-word-index";
        }

        Map<String, Object> wordData = words.get(wordIndex);
        String sourceWord = (String) wordData.get("source_word");

        if (sourceWord == null || sourceWord.trim().isEmpty()) {
            return "redirect:/sessions/" + id + "?error=invalid-source-word";
        }

        try {
            // Get audio configuration from original request or use defaults
            Map<String, Object> originalRequest = sessionData.containsKey("original_request")
                    ? (Map<String, Object>) sessionData.get("original_request")
                    : new HashMap<>();

            boolean enableSourceAudio = (Boolean) originalRequest.getOrDefault("enable_source_audio", true);

            List<AsyncAudioGenerationService.AudioRequest> audioRequests = new ArrayList<>();

            // Get language audio configuration
            LangAudioOption sourceLangAudio = getLangAudioOption(session.getSourceLanguage());

            // Generate source audio only
            if (enableSourceAudio) {
                String sourceAudioFileName = Codec.encodeForAudioFileName(sourceWord);
                AsyncAudioGenerationService.AudioRequest sourceAudioRequest =
                        new AsyncAudioGenerationService.AudioRequest(
                                sourceWord,
                                sourceLangAudio.languageCode,
                                sourceLangAudio.voiceGender,
                                sourceLangAudio.voiceName,
                                sourceAudioFileName
                        );
                audioRequests.add(sourceAudioRequest);
                wordData.put("source_audio_file", sourceAudioFileName + ".mp3");
            }

            // Generate audio files
            if (!audioRequests.isEmpty()) {
                audioGenerationService.generateAudioFilesAsync(audioRequests).get();
            }

            // Update session data
            words.set(wordIndex, wordData);
            sessionData.put("words", words);

            // Update audio count in process_summary
            updateAudioCountInProcessSummary(sessionData, words);

            translationSessionService.updateSessionData(id, sessionData);

            System.out.println("Regenerated audio for word: " + sourceWord + " in session " + id);
            return "redirect:/sessions/" + id + "?message=audio-regenerated";

        } catch (Exception e) {
            System.err.println("Failed to regenerate audio: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/sessions/" + id + "?error=audio-regeneration-failed";
        }
    }

    @PostMapping("/{id}/regenerate-all-audio")
    @SuppressWarnings("unchecked")
    public String regenerateAllAudio(@PathVariable Long id) {
        System.out.println("Regenerating audio for session " + id);
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);

        if (sessionOpt.isEmpty()) {
            return "redirect:/sessions?error=session-not-found";
        }

        TranslationSessionEntity session = sessionOpt.get();
        Map<String, Object> sessionData = translationSessionService.getSessionData(id);

        // Get words from session data
        if (!sessionData.containsKey("words")) {
            return "redirect:/sessions/" + id + "?error=no-words-in-session";
        }

        List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

        if (words.isEmpty()) {
            return "redirect:/sessions/" + id + "?error=no-words-to-regenerate";
        }

        try {
            // Force enable source audio when explicitly regenerating audio
            // (User clicked the "Generate Audio" button, so they want audio regardless of original settings)
            boolean enableSourceAudio = true;

            System.out.println("Forcing audio generation: enableSourceAudio=" + enableSourceAudio);

            List<AsyncAudioGenerationService.AudioRequest> audioRequests = new ArrayList<>();

            // Get language audio configuration
            LangAudioOption sourceLangAudio = getLangAudioOption(session.getSourceLanguage());

            // Process all words
            int audioFilesAdded = 0;
            for (Map<String, Object> wordData : words) {
                String sourceWord = (String) wordData.get("source_word");

                if (sourceWord == null || sourceWord.trim().isEmpty()) {
                    continue;
                }

                // Generate source audio
                if (enableSourceAudio) {
                    String sourceAudioFileName = Codec.encodeForAudioFileName(sourceWord);
                    AsyncAudioGenerationService.AudioRequest sourceAudioRequest =
                            new AsyncAudioGenerationService.AudioRequest(
                                    sourceWord,
                                    sourceLangAudio.languageCode,
                                    sourceLangAudio.voiceGender,
                                    sourceLangAudio.voiceName,
                                    sourceAudioFileName
                            );
                    audioRequests.add(sourceAudioRequest);
                    wordData.put("source_audio_file", sourceAudioFileName + ".mp3");
                    audioFilesAdded++;
                }
            }

            // Generate all audio files
            System.out.println("Added audio files to " + audioFilesAdded + " words, generating " + audioRequests.size() + " audio files");
            if (!audioRequests.isEmpty()) {
                audioGenerationService.generateAudioFilesAsync(audioRequests).get();
            }

            // Update session data
            sessionData.put("words", words);

            // Update audio count in process_summary
            updateAudioCountInProcessSummary(sessionData, words);

            translationSessionService.updateSessionData(id, sessionData);

            System.out.println("Regenerated audio for " + words.size() + " words in session " + id);
            return "redirect:/sessions/" + id + "?message=all-audio-regenerated";

        } catch (Exception e) {
            System.err.println("Failed to regenerate all audio: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/sessions/" + id + "?error=audio-regeneration-failed";
        }
    }

    /**
     * Update audio count in process_summary based on word-level audio data
     */
    @SuppressWarnings("unchecked")
    private void updateAudioCountInProcessSummary(Map<String, Object> sessionData, List<Map<String, Object>> words) {
        int audioCount = 0;

        // Count audio files from word data (source only)
        for (Map<String, Object> wordData : words) {
            // Count source audio
            if (wordData.containsKey("source_audio_file") && wordData.get("source_audio_file") != null) {
                audioCount++;
            }
        }

        // Update process_summary
        Map<String, Object> processSummary;
        if (sessionData.containsKey("process_summary") && sessionData.get("process_summary") instanceof Map) {
            processSummary = (Map<String, Object>) sessionData.get("process_summary");
        } else {
            processSummary = new HashMap<>();
            sessionData.put("process_summary", processSummary);
        }

        processSummary.put("audio_success_count", audioCount);
        processSummary.put("audio_failure_count", 0); // Reset failures after successful regeneration

        System.out.println("Audio count updated: " + audioCount + " files");
    }

    private static class LangAudioOption {
        public LanguageAudioCodes languageCode;
        public SsmlVoiceGender voiceGender;
        public String voiceName;
    }

    private LangAudioOption getLangAudioOption(String lang) {
        LangAudioOption langAudioOption = new LangAudioOption();
        switch (lang) {
            case "en" -> {
                langAudioOption.languageCode = LanguageAudioCodes.English;
                langAudioOption.voiceGender = SsmlVoiceGender.MALE;
                langAudioOption.voiceName = "en-US-Neural2-A";
                return langAudioOption;
            }
            case "es" -> {
                langAudioOption.languageCode = LanguageAudioCodes.Spanish;
                langAudioOption.voiceGender = SsmlVoiceGender.MALE;
                langAudioOption.voiceName = "es-US-Neural2-B";
                return langAudioOption;
            }
            case "fr" -> {
                langAudioOption.languageCode = LanguageAudioCodes.French;
                langAudioOption.voiceGender = SsmlVoiceGender.MALE;
                langAudioOption.voiceName = "fr-FR-Neural2-B";
                return langAudioOption;
            }
            case "cafr" -> {
                langAudioOption.languageCode = LanguageAudioCodes.CanadianFrench;
                langAudioOption.voiceGender = SsmlVoiceGender.FEMALE;
                langAudioOption.voiceName = "fr-CA-Neural2-A";
                return langAudioOption;
            }
            case "kr" -> {
                langAudioOption.languageCode = LanguageAudioCodes.Korean;
                langAudioOption.voiceGender = SsmlVoiceGender.FEMALE;
                langAudioOption.voiceName = "ko-KR-Standard-A";
                return langAudioOption;
            }
            case "jp" -> {
                langAudioOption.languageCode = LanguageAudioCodes.Japanese;
                langAudioOption.voiceGender = SsmlVoiceGender.MALE;
                langAudioOption.voiceName = "ja-JP-Neural2-C";
                return langAudioOption;
            }
            case "hi" -> {
                langAudioOption.languageCode = LanguageAudioCodes.Hindi;
                langAudioOption.voiceGender = SsmlVoiceGender.FEMALE;
                langAudioOption.voiceName = "hi-IN-Neural2-A";
                return langAudioOption;
            }
            case "pa" -> {
                langAudioOption.languageCode = LanguageAudioCodes.Punjabi;
                langAudioOption.voiceGender = SsmlVoiceGender.FEMALE;
                langAudioOption.voiceName = "pa-IN-Standard-A";
                return langAudioOption;
            }
            case "tl" -> {
                langAudioOption.languageCode = LanguageAudioCodes.Tagalog;
                langAudioOption.voiceGender = SsmlVoiceGender.FEMALE;
                langAudioOption.voiceName = "fil-PH-Standard-A";
                return langAudioOption;
            }
            default -> throw new RuntimeException("Invalid language code");
        }
    }

    @GetMapping("/{id}/anki-cards-preview")
    @ResponseBody
    public ResponseEntity<?> getAnkiCardsPreview(@PathVariable Long id) {
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TranslationSessionEntity session = sessionOpt.get();

        // Check if Anki is enabled
        if (!Boolean.TRUE.equals(session.getAnkiEnabled())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Anki not enabled"));
        }

        // Get session data
        Map<String, Object> sessionData = translationSessionService.getSessionData(id);
        List<Map<String, Object>> cardPreviews = new ArrayList<>();

        // Get words from session data
        if (sessionData.containsKey("words") && sessionData.get("words") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

            for (Map<String, Object> wordData : words) {
                // Merge with latest data from WordEntity
                Map<String, Object> latestWordData = mergeWithLatestWordData(
                        wordData,
                        session.getSourceLanguage(),
                        session.getTargetLanguage()
                );

                // Build front and back content using latest data
                String frontPreview = buildAnkiFront(session, latestWordData);
                String backPreview = buildAnkiBack(session, latestWordData);

                Map<String, Object> cardPreview = new HashMap<>();
                cardPreview.put("sourceWord", latestWordData.get("source_word"));
                cardPreview.put("frontPreview", frontPreview);
                cardPreview.put("backPreview", backPreview);
                cardPreview.put("wordData", latestWordData);

                // Add format structure if available
                if (session.getAnkiFormat() != null && session.getAnkiFormat().getFormat() != null) {
                    cardPreview.put("formatName", session.getAnkiFormat().getName());
                    cardPreview.put("frontStructure", ankiCardBuilderService.buildPreviewHtml(
                            session.getAnkiFormat().getFormat().getFrontCardItems()));
                    cardPreview.put("backStructure", ankiCardBuilderService.buildPreviewHtml(
                            session.getAnkiFormat().getFormat().getBackCardItems()));
                }

                cardPreviews.add(cardPreview);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("deck", session.getAnkiDeck());
        response.put("cards", cardPreviews);
        response.put("sessionId", session.getId());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/enable-anki")
    @ResponseBody
    public ResponseEntity<?> enableAnki(@PathVariable Long id) {
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        translationSessionService.updateAnkiEnabled(id, true);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/create-anki-cards-with-edits")
    @ResponseBody
    public ResponseEntity<?> createAnkiCardsWithEdits(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {

        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TranslationSessionEntity session = sessionOpt.get();

        // Check if Anki is enabled
        if (!Boolean.TRUE.equals(session.getAnkiEnabled())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Anki not enabled"));
        }

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> cards = (List<Map<String, String>>) request.get("cards");

            // Get deck name from request, fall back to session's deck name
            String deckName = request.containsKey("deckName") ? (String) request.get("deckName") : session.getAnkiDeck();

            int cardsCreated = 0;
            for (Map<String, String> card : cards) {
                String front = card.get("front");
                String back = card.get("back");

                // Create Anki card with edited content
                ankiNoteCreatorService.addNote(deckName, front, back);
                cardsCreated++;
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "cardsCreated", cardsCreated
            ));

        } catch (Exception e) {
            System.err.println("Failed to create Anki cards: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/{id}/create-anki-cards")
    public String createAnkiCards(@PathVariable Long id) {
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);

        if (sessionOpt.isEmpty()) {
            return "redirect:/sessions?error=session-not-found";
        }

        TranslationSessionEntity session = sessionOpt.get();

        // Check if Anki is enabled
        if (!Boolean.TRUE.equals(session.getAnkiEnabled())) {
            return "redirect:/sessions/" + id + "?error=anki-not-enabled";
        }

        // Check if Anki format is configured
        if (session.getAnkiFormat() == null || session.getAnkiFormat().getFormat() == null) {
            return "redirect:/sessions/" + id + "?error=anki-format-not-configured";
        }

        // Get session data
        Map<String, Object> sessionData = translationSessionService.getSessionData(id);

        try {
            // Get card format
            AnkiFormatEntity ankiFormat = session.getAnkiFormat();
            List<CardItem> frontCardItems = ankiFormat.getFormat().getFrontCardItems();
            List<CardItem> backCardItems = ankiFormat.getFormat().getBackCardItems();

            // Ensure custom note type exists in Anki
            String modelName = "EME - " + ankiFormat.getName();
            ankiCardBuilderService.ensureCustomNoteTypeExists(modelName, frontCardItems, backCardItems);

            int cardsCreated = 0;
            int validationErrors = 0;

            // Get words from session data
            if (sessionData.containsKey("words") && sessionData.get("words") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

                for (Map<String, Object> wordData : words) {
                    // Merge with latest data from WordEntity
                    Map<String, Object> latestWordData = mergeWithLatestWordData(
                            wordData,
                            session.getSourceLanguage(),
                            session.getTargetLanguage()
                    );

                    // Validate word data has all required fields
                    AnkiCardBuilderService.ValidationResult frontValidation =
                        ankiCardBuilderService.validateWordData(latestWordData, frontCardItems);

                    if (!frontValidation.isValid()) {
                        System.err.println("Skipping word due to missing front fields: " +
                            latestWordData.get("source_word") + " - " + frontValidation.getMissingFieldsMessage());
                        validationErrors++;
                        continue;
                    }

                    // Build field values for custom note type
                    Map<String, String> fields = ankiCardBuilderService.buildFieldValues(
                        latestWordData, frontCardItems, backCardItems);

                    // Create Anki card with custom note type
                    ankiNoteCreatorService.addNoteWithCustomModel(
                        session.getAnkiDeck(), modelName, fields, latestWordData);
                    cardsCreated++;
                }
            }

            if (validationErrors > 0) {
                return "redirect:/sessions/" + id + "?message=created-" + cardsCreated +
                    "-cards&warning=skipped-" + validationErrors + "-cards";
            }

            return "redirect:/sessions/" + id + "?message=created-" + cardsCreated + "-cards";

        } catch (Exception e) {
            System.err.println("Failed to create Anki cards: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/sessions/" + id + "?error=anki-creation-failed";
        }
    }

    private String buildAnkiFront(TranslationSessionEntity session, Map<?, ?> wordData) {
        if (session.getAnkiFormat() == null || session.getAnkiFormat().getFormat() == null) {
            // Fallback to simple source word
            Object sourceWord = wordData.get("source_word");
            return sourceWord != null ? sourceWord.toString() : "";
        }

        return ankiCardBuilderService.buildCardSide(
                session.getAnkiFormat().getFormat().getFrontCardItems(),
                wordData
        );
    }

    private String buildAnkiBack(TranslationSessionEntity session, Map<?, ?> wordData) {
        if (session.getAnkiFormat() == null || session.getAnkiFormat().getFormat() == null) {
            // Fallback to simple translation
            if (wordData.containsKey("translations")) {
                List<?> translations = (List<?>) wordData.get("translations");
                StringBuilder sb = new StringBuilder();
                if (!translations.isEmpty()) {
                    for (Object trans : translations) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(trans.toString());
                    }
                }
                return sb.toString();
            }
            return "";
        }

        return ankiCardBuilderService.buildCardSide(
                session.getAnkiFormat().getFormat().getBackCardItems(),
                wordData
        );
    }

    /**
     * Merge session word data with latest data from WordEntity
     * This ensures Anki cards use the most up-to-date information
     */
    private Map<String, Object> mergeWithLatestWordData(
            Map<String, Object> sessionWordData,
            String sourceLanguage,
            String targetLanguage) {

        String sourceWord = (String) sessionWordData.get("source_word");
        if (sourceWord == null) {
            return sessionWordData;
        }

        // Try to get latest data from WordEntity
        Optional<WordEntity> wordEntityOpt = wordService.findWord(sourceWord, sourceLanguage, targetLanguage);

        if (wordEntityOpt.isEmpty()) {
            return sessionWordData; // No Word entity yet, use session data
        }

        WordEntity wordEntity = wordEntityOpt.get();
        Map<String, Object> mergedData = new HashMap<>(sessionWordData);

        // Update with latest translations if available
        if (wordEntity.getTranslation() != null && !wordEntity.getTranslation().isEmpty()) {
            try {
                Set<String> translations = wordService.deserializeTranslations(wordEntity.getTranslation());
                mergedData.put("translations", new ArrayList<>(translations));
            } catch (Exception e) {
                // Keep session data
            }
        }

        // Update with latest audio files
        if (wordEntity.getAudioSourceFile() != null) {
            mergedData.put("source_audio_file", wordEntity.getAudioSourceFile());
        }

        // Update with latest mnemonic data
        if (wordEntity.getMnemonicKeyword() != null) {
            mergedData.put("mnemonic_keyword", wordEntity.getMnemonicKeyword());
        }
        if (wordEntity.getMnemonicSentence() != null) {
            mergedData.put("mnemonic_sentence", wordEntity.getMnemonicSentence());
        }
        if (wordEntity.getImagePrompt() != null) {
            mergedData.put("image_prompt", wordEntity.getImagePrompt());
        }

        // Update with latest transliteration
        if (wordEntity.getSourceTransliteration() != null) {
            mergedData.put("source_transliteration", wordEntity.getSourceTransliteration());
        }

        // Add override timestamps to indicate manually overridden fields
        if (wordEntity.getTranslationOverrideAt() != null) {
            mergedData.put("translation_override_at", wordEntity.getTranslationOverrideAt().toString());
        }
        if (wordEntity.getTransliterationOverrideAt() != null) {
            mergedData.put("transliteration_override_at", wordEntity.getTransliterationOverrideAt().toString());
        }
        if (wordEntity.getMnemonicKeywordUpdatedAt() != null) {
            mergedData.put("mnemonic_keyword_override_at", wordEntity.getMnemonicKeywordUpdatedAt().toString());
        }

        // Update with latest image file (this is the key update for regenerated images)
        if (wordEntity.getImageFile() != null) {
            mergedData.put("image_file", wordEntity.getImageFile());
            mergedData.put("image_status", "success");
        }

        // Add image prompt status for the new two-step workflow
        if (wordEntity.getImagePromptStatus() != null) {
            mergedData.put("image_prompt_status", wordEntity.getImagePromptStatus());
        } else {
            // Default to NONE if no status set
            mergedData.put("image_prompt_status", "NONE");
        }

        // Update with latest sentence data from SentenceEntity
        Optional<SentenceData> sentenceDataOpt = sentenceStorageService.findSentence(
                sourceWord, sourceLanguage, targetLanguage);
        if (sentenceDataOpt.isPresent()) {
            SentenceData sentenceData = sentenceDataOpt.get();
            Map<String, String> sentenceMap = new HashMap<>();
            sentenceMap.put("source_language_sentence", sentenceData.getSourceLanguageSentence());
            sentenceMap.put("source_language_structure", sentenceData.getSourceLanguageStructure());
            sentenceMap.put("target_language_sentence", sentenceData.getTargetLanguageSentence());
            sentenceMap.put("target_language_latin", sentenceData.getTargetLanguageLatinCharacters());
            sentenceMap.put("target_language_transliteration", sentenceData.getTargetLanguageTransliteration());
            mergedData.put("sentence_data", sentenceMap);
            mergedData.put("sentence_status", "success");
        }

        return mergedData;
    }

    /**
     * Enrich word data with character guide information
     * Adds information about whether the character is in the character guide DB
     */
    private void enrichWithCharacterGuideInfo(Map<String, Object> wordData, String sourceLanguage, String targetLanguage) {
        String sourceWord = (String) wordData.get("source_word");
        String mnemonicKeyword = (String) wordData.get("mnemonic_keyword");
        String transliteration = (String) wordData.get("source_transliteration");

        if (sourceWord == null || mnemonicKeyword == null) {
            return;
        }

        // Transliteration should come from the translation process (OpenAI provides it)
        // If not available, fetch it from OpenAI
        if (transliteration == null || transliteration.trim().isEmpty()) {
            System.out.println("No transliteration available for character enrichment, fetching from OpenAI: " + sourceWord);

            // Don't fetch for English words
            if (!"en".equals(sourceLanguage)) {
                try {
                    transliteration = translationService.getTransliteration(sourceWord, sourceLanguage);
                    if (transliteration != null && !transliteration.trim().isEmpty()) {
                        // Update the word entity with the new transliteration
                        wordService.updateTransliteration(sourceWord, sourceLanguage, targetLanguage, transliteration);
                        // Update the wordData map so it's available for the rest of this method
                        wordData.put("source_transliteration", transliteration);
                        System.out.println("Fetched and saved transliteration for " + sourceWord + ": " + transliteration);
                    } else {
                        System.out.println("No transliteration returned from OpenAI for: " + sourceWord);
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("Failed to fetch transliteration from OpenAI for " + sourceWord + ": " + e.getMessage());
                    // Can't enrich without transliteration
                    return;
                }
            } else {
                // For English, can't enrich without transliteration
                return;
            }
        }

        String normalizedWord = transliteration.toLowerCase().trim();

        // Check if there's a matching character guide entry
        Optional<CharacterGuideEntity> characterMatch = characterGuideService.findMatchingCharacterForWord(sourceWord, sourceLanguage, transliteration);

        if (characterMatch.isPresent()) {
            CharacterGuideEntity character = characterMatch.get();
            wordData.put("character_in_guide", true);
            wordData.put("character_start_sound", character.getStartSound());
            wordData.put("character_name", character.getCharacterName());
            wordData.put("character_context", character.getCharacterContext());
        } else {
            // Character not in guide - determine the start sound that should be used
            wordData.put("character_in_guide", false);

            // Extract a reasonable start sound (first 2-3 characters of transliterated word)
            String suggestedStartSound = normalizedWord.length() >= 3 ? normalizedWord.substring(0, 3) :
                                        normalizedWord.length() >= 2 ? normalizedWord.substring(0, 2) :
                                        normalizedWord;

            wordData.put("character_start_sound", suggestedStartSound);
            wordData.put("character_name", mnemonicKeyword);
            // Try to extract character context from mnemonic_sentence if available
            String mnemonicSentence = (String) wordData.get("mnemonic_sentence");
            if (mnemonicSentence != null && !mnemonicSentence.isEmpty()) {
                // Extract context from the sentence (simplified - just use the sentence itself)
                wordData.put("character_context", extractContextFromMnemonic(mnemonicSentence, mnemonicKeyword));
            } else {
                wordData.put("character_context", "");
            }
        }
    }

    /**
     * Extract character context from mnemonic sentence
     * This tries to find where the character appears in the mnemonic
     */
    private String extractContextFromMnemonic(String mnemonicSentence, String characterName) {
        // Simple heuristic: if the character appears in the sentence, extract surrounding context
        int index = mnemonicSentence.toLowerCase().indexOf(characterName.toLowerCase());
        if (index >= 0) {
            // Return a short description mentioning the character
            return "From mnemonic: " + characterName;
        }
        return characterName;
    }

    /**
     * Get default audio code for a language
     */
    private LanguageAudioCodes getDefaultAudioCode(String languageCode) {
        return switch (languageCode.toLowerCase()) {
            case "hi" -> LanguageAudioCodes.Hindi;
            case "pa" -> LanguageAudioCodes.Punjabi;
            case "tl", "fil" -> LanguageAudioCodes.Tagalog;
            case "es" -> LanguageAudioCodes.Spanish;
            case "fr" -> LanguageAudioCodes.French;
            case "cafr" -> LanguageAudioCodes.CanadianFrench;
            case "ko", "kr" -> LanguageAudioCodes.Korean;
            case "ja", "jp" -> LanguageAudioCodes.Japanese;
            default -> LanguageAudioCodes.English;
        };
    }

    @PostMapping("/{id}/regenerate-all-translations")
    @SuppressWarnings("unchecked")
    public String regenerateAllTranslations(@PathVariable Long id) {
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);

        if (sessionOpt.isEmpty()) {
            return "redirect:/sessions?error=session-not-found";
        }

        TranslationSessionEntity session = sessionOpt.get();
        Map<String, Object> sessionData = translationSessionService.getSessionData(id);

        // Get words from session data
        if (!sessionData.containsKey("words")) {
            return "redirect:/sessions/" + id + "?error=no-words-in-session";
        }

        List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

        if (words.isEmpty()) {
            return "redirect:/sessions/" + id + "?error=no-words-to-regenerate";
        }

        int successCount = 0;
        int failureCount = 0;

        // Process all words
        for (Map<String, Object> wordData : words) {
            String sourceWord = (String) wordData.get("source_word");

            if (sourceWord == null || sourceWord.trim().isEmpty()) {
                continue;
            }

            try {
                // Regenerate translation using the service (skip cache to get fresh translation)
                TranslationData translationData = translationService.translateText(
                        sourceWord, session.getSourceLanguage(), session.getTargetLanguage(), true);

                // Update word data with new translations
                if (translationData.getTranslations() != null && !translationData.getTranslations().isEmpty()) {
                    wordData.put("translations", new ArrayList<>(translationData.getTranslations()));
                }

                // Update transliteration if available
                if (translationData.getTransliteration() != null && !translationData.getTransliteration().isEmpty()) {
                    wordData.put("source_transliteration", translationData.getTransliteration());
                }

                // Update word entity in database
                wordService.updateTranslation(sourceWord, session.getSourceLanguage(),
                        session.getTargetLanguage(), translationData.getTranslations());

                if (translationData.getTransliteration() != null && !translationData.getTransliteration().isEmpty()) {
                    wordService.updateTransliteration(sourceWord, session.getSourceLanguage(),
                            session.getTargetLanguage(), translationData.getTransliteration());
                }

                successCount++;
                System.out.println("Regenerated translation for word: " + sourceWord);

            } catch (Exception e) {
                System.err.println("Failed to regenerate translation for word '" + sourceWord + "': " + e.getMessage());
                failureCount++;
            }
        }

        // Update session data
        sessionData.put("words", words);
        translationSessionService.updateSessionData(id, sessionData);

        System.out.println("Regenerated translations for session " + id +
                " - Success: " + successCount + ", Failed: " + failureCount);

        if (failureCount > 0) {
            return "redirect:/sessions/" + id + "?message=translations-regenerated-with-errors&success=" + successCount + "&failed=" + failureCount;
        } else {
            return "redirect:/sessions/" + id + "?message=all-translations-regenerated&count=" + successCount;
        }
    }

    @PostMapping("/{id}/regenerate-all-mnemonics-and-images")
    @SuppressWarnings("unchecked")
    public String regenerateAllMnemonicsAndImages(@PathVariable Long id) {
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);

        if (sessionOpt.isEmpty()) {
            return "redirect:/sessions?error=session-not-found";
        }

        TranslationSessionEntity session = sessionOpt.get();
        Map<String, Object> sessionData = translationSessionService.getSessionData(id);

        // Get words from session data
        if (!sessionData.containsKey("words")) {
            return "redirect:/sessions/" + id + "?error=no-words-in-session";
        }

        List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");

        if (words.isEmpty()) {
            return "redirect:/sessions/" + id + "?error=no-words-to-regenerate";
        }

        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;

        // Process all words
        for (Map<String, Object> wordData : words) {
            String sourceWord = (String) wordData.get("source_word");

            if (sourceWord == null || sourceWord.trim().isEmpty()) {
                continue;
            }

            // Skip if word already has an image
            if (wordData.containsKey("image_file") && wordData.get("image_file") != null) {
                skippedCount++;
                continue;
            }

            // Check if we have a translation
            if (!wordData.containsKey("translations") ||
                ((List<?>) wordData.get("translations")).isEmpty()) {
                System.err.println("Skipping word '" + sourceWord + "': no translation available");
                failureCount++;
                continue;
            }

            try {
                // Get first translation
                List<String> translations = (List<String>) wordData.get("translations");
                String targetWord = translations.get(0);

                // Get transliteration
                String transliteration = (String) wordData.get("source_transliteration");
                if (transliteration == null || transliteration.trim().isEmpty()) {
                    // Try to get it from OpenAI
                    transliteration = translationService.getTransliteration(sourceWord, session.getSourceLanguage());
                    if (transliteration != null) {
                        wordData.put("source_transliteration", transliteration);
                        wordService.updateTransliteration(sourceWord, session.getSourceLanguage(),
                                session.getTargetLanguage(), transliteration);
                    }
                }

                // Get image style from session's original request
                Map<String, Object> originalRequest = sessionData.containsKey("original_request")
                        ? (Map<String, Object>) sessionData.get("original_request")
                        : new HashMap<>();

                String imageStyleStr = (String) originalRequest.get("image_style");
                com.raidrin.eme.image.ImageStyle imageStyle = imageStyleStr != null
                        ? com.raidrin.eme.image.ImageStyle.valueOf(imageStyleStr)
                        : com.raidrin.eme.image.ImageStyle.REALISTIC_CINEMATIC;

                // Generate mnemonic and image prompt
                com.raidrin.eme.mnemonic.MnemonicGenerationService.MnemonicData mnemonicData =
                        mnemonicGenerationService.generateMnemonic(
                                sourceWord, targetWord, session.getSourceLanguage(),
                                session.getTargetLanguage(), transliteration, imageStyle);

                // Update word data
                wordData.put("mnemonic_keyword", mnemonicData.getMnemonicKeyword());
                wordData.put("mnemonic_sentence", mnemonicData.getMnemonicSentence());
                wordData.put("image_prompt", mnemonicData.getImagePrompt());

                // Update word entity in database with mnemonic data
                wordService.updateMnemonic(sourceWord, session.getSourceLanguage(),
                        session.getTargetLanguage(), mnemonicData.getMnemonicKeyword(),
                        mnemonicData.getMnemonicSentence(), mnemonicData.getImagePrompt());

                // Generate image
                String sanitizedPrompt = mnemonicGenerationService.sanitizeImagePrompt(mnemonicData.getImagePrompt());
                String imageFileName = FileNameSanitizer.fromMnemonicSentence(
                        mnemonicData.getMnemonicSentence() != null ? mnemonicData.getMnemonicSentence() : sourceWord,
                        "jpg"
                );

                com.raidrin.eme.image.OpenAiImageService.GeneratedImage openAiImage =
                        openAiImageService.generateImage(sanitizedPrompt, "1536x1024", "medium", null);

                // Download image to local file
                downloadImageToLocal(openAiImage.getImageUrl(), imageFileName);

                // Upload to GCP
                gcpStorageService.downloadAndUpload(openAiImage.getImageUrl(), imageFileName);

                // Update word data with image file
                wordData.put("image_file", imageFileName);
                wordData.put("image_status", "success");

                // Update word entity with image file
                wordService.updateImage(sourceWord, session.getSourceLanguage(),
                        session.getTargetLanguage(), imageFileName, mnemonicData.getImagePrompt());

                successCount++;
                System.out.println("Regenerated mnemonic and image for word: " + sourceWord);

            } catch (Exception e) {
                System.err.println("Failed to regenerate mnemonic/image for word '" + sourceWord + "': " + e.getMessage());
                e.printStackTrace();
                wordData.put("image_status", "failed");
                wordData.put("image_error", e.getMessage());
                failureCount++;
            }
        }

        // Update session data
        sessionData.put("words", words);
        translationSessionService.updateSessionData(id, sessionData);

        System.out.println("Regenerated mnemonics and images for session " + id +
                " - Success: " + successCount + ", Failed: " + failureCount + ", Skipped (already has image): " + skippedCount);

        if (failureCount > 0) {
            return "redirect:/sessions/" + id + "?message=mnemonics-regenerated-with-errors&success=" + successCount + "&failed=" + failureCount;
        } else {
            return "redirect:/sessions/" + id + "?message=all-mnemonics-regenerated&count=" + successCount;
        }
    }

    /**
     * Download an image from a URL to the local image output directory
     */
    private Path downloadImageToLocal(String imageUrl, String fileName) throws IOException {
        Files.createDirectories(Paths.get(imageOutputDirectory));
        Path outputPath = Paths.get(imageOutputDirectory, fileName);

        // Handle file:// URLs (local files from base64 conversion)
        if (imageUrl.startsWith("file://")) {
            Path sourcePath = Paths.get(java.net.URI.create(imageUrl));
            Files.copy(sourcePath, outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Copied local file from: " + sourcePath + " to: " + outputPath);
            return outputPath;
        }

        // Handle remote URLs (http/https)
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            URL url = new URL(imageUrl);
            url.openStream().transferTo(fos);
        }

        return outputPath;
    }

    // ========================================================================
    // GRANULAR REGENERATION ENDPOINTS (Phase 2.3)
    // These endpoints selectively regenerate specific fields, respecting overrides
    // ========================================================================

    /**
     * Selectively regenerate translations for all words in session (skips overridden words)
     */
    @PostMapping("/{id}/generate-translations")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateTranslations(@PathVariable Long id) {
        try {
            SessionOrchestrationService.SelectiveGenerationResult result =
                sessionOrchestrationService.generateTranslationsSelectively(id).get();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("operation", result.getOperationType());
            response.put("total", result.getTotalCount());
            response.put("success_count", result.getSuccessCount());
            response.put("failed_count", result.getFailedCount());
            response.put("skipped_count", result.getSkippedCount());
            response.put("message", result.getMessage());
            response.put("skipped_words", result.getSkippedWords());
            response.put("failed_words", result.getFailedWords());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Selectively regenerate transliterations for all words in session (skips overridden words)
     */
    @PostMapping("/{id}/generate-transliterations")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateTransliterations(@PathVariable Long id) {
        try {
            SessionOrchestrationService.SelectiveGenerationResult result =
                sessionOrchestrationService.generateTransliterationsSelectively(id).get();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("operation", result.getOperationType());
            response.put("total", result.getTotalCount());
            response.put("success_count", result.getSuccessCount());
            response.put("failed_count", result.getFailedCount());
            response.put("skipped_count", result.getSkippedCount());
            response.put("message", result.getMessage());
            response.put("skipped_words", result.getSkippedWords());
            response.put("failed_words", result.getFailedWords());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Selectively regenerate mnemonic keywords for all words in session (skips overridden words)
     */
    @PostMapping("/{id}/generate-mnemonic-keywords")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateMnemonicKeywords(
            @PathVariable Long id,
            @RequestParam(required = false) String imageStyle) {
        try {
            com.raidrin.eme.image.ImageStyle style = imageStyle != null
                ? com.raidrin.eme.image.ImageStyle.fromString(imageStyle)
                : com.raidrin.eme.image.ImageStyle.REALISTIC_CINEMATIC;

            SessionOrchestrationService.SelectiveGenerationResult result =
                sessionOrchestrationService.generateMnemonicKeywordsSelectively(id, style).get();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("operation", result.getOperationType());
            response.put("total", result.getTotalCount());
            response.put("success_count", result.getSuccessCount());
            response.put("failed_count", result.getFailedCount());
            response.put("skipped_count", result.getSkippedCount());
            response.put("message", result.getMessage());
            response.put("skipped_words", result.getSkippedWords());
            response.put("failed_words", result.getFailedWords());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Selectively regenerate mnemonic sentences for all words in session (checks prerequisites)
     */
    @PostMapping("/{id}/generate-mnemonic-sentences")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateMnemonicSentences(
            @PathVariable Long id,
            @RequestParam(required = false) String imageStyle) {
        try {
            com.raidrin.eme.image.ImageStyle style = imageStyle != null
                ? com.raidrin.eme.image.ImageStyle.fromString(imageStyle)
                : com.raidrin.eme.image.ImageStyle.REALISTIC_CINEMATIC;

            SessionOrchestrationService.SelectiveGenerationResult result =
                sessionOrchestrationService.generateMnemonicSentencesSelectively(id, style).get();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("operation", result.getOperationType());
            response.put("total", result.getTotalCount());
            response.put("success_count", result.getSuccessCount());
            response.put("failed_count", result.getFailedCount());
            response.put("skipped_count", result.getSkippedCount());
            response.put("message", result.getMessage());
            response.put("skipped_words", result.getSkippedWords());
            response.put("failed_words", result.getFailedWords());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Selectively regenerate images for all words in session (validates prerequisites)
     */
    @PostMapping("/{id}/generate-images")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateImages(@PathVariable Long id) {
        try {
            SessionOrchestrationService.SelectiveGenerationResult result =
                sessionOrchestrationService.generateImagesSelectively(id).get();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("operation", result.getOperationType());
            response.put("total", result.getTotalCount());
            response.put("success_count", result.getSuccessCount());
            response.put("failed_count", result.getFailedCount());
            response.put("skipped_count", result.getSkippedCount());
            response.put("message", result.getMessage());
            response.put("skipped_words", result.getSkippedWords());
            response.put("failed_words", result.getFailedWords());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Selectively regenerate source audio for all words in session
     */
    @PostMapping("/{id}/generate-audio")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateAudio(@PathVariable Long id) {
        try {
            SessionOrchestrationService.SelectiveGenerationResult result =
                sessionOrchestrationService.generateAudioSelectively(id).get();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("operation", result.getOperationType());
            response.put("total", result.getTotalCount());
            response.put("success_count", result.getSuccessCount());
            response.put("failed_count", result.getFailedCount());
            response.put("skipped_count", result.getSkippedCount());
            response.put("message", result.getMessage());
            response.put("skipped_words", result.getSkippedWords());
            response.put("failed_words", result.getFailedWords());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Selectively regenerate example sentences for all words in session (checks prerequisites)
     */
    @PostMapping("/{id}/generate-example-sentences")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateExampleSentences(@PathVariable Long id) {
        try {
            SessionOrchestrationService.SelectiveGenerationResult result =
                sessionOrchestrationService.generateExampleSentencesSelectively(id).get();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("operation", result.getOperationType());
            response.put("total", result.getTotalCount());
            response.put("success_count", result.getSuccessCount());
            response.put("failed_count", result.getFailedCount());
            response.put("skipped_count", result.getSkippedCount());
            response.put("message", result.getMessage());
            response.put("skipped_words", result.getSkippedWords());
            response.put("failed_words", result.getFailedWords());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Generate image prompts ONLY (no images) for all words in session.
     * This is the first step - prompts can be reviewed/edited before image generation.
     */
    @PostMapping("/{id}/generate-image-prompts")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateImagePrompts(
            @PathVariable Long id,
            @RequestParam(required = false) String imageStyle) {
        try {
            com.raidrin.eme.image.ImageStyle style = imageStyle != null
                ? com.raidrin.eme.image.ImageStyle.fromString(imageStyle)
                : com.raidrin.eme.image.ImageStyle.REALISTIC_CINEMATIC;

            SessionOrchestrationService.SelectiveGenerationResult result =
                sessionOrchestrationService.generateImagePromptsSelectively(id, style).get();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("operation", result.getOperationType());
            response.put("total", result.getTotalCount());
            response.put("success_count", result.getSuccessCount());
            response.put("failed_count", result.getFailedCount());
            response.put("skipped_count", result.getSkippedCount());
            response.put("message", result.getMessage());
            response.put("skipped_words", result.getSkippedWords());
            response.put("failed_words", result.getFailedWords());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Generate images ONLY for words with APPROVED prompts.
     * This is the second step after reviewing/editing/approving prompts.
     */
    @PostMapping("/{id}/generate-images-from-prompts")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateImagesFromApprovedPrompts(@PathVariable Long id) {
        try {
            SessionOrchestrationService.SelectiveGenerationResult result =
                sessionOrchestrationService.generateImagesFromApprovedPrompts(id).get();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("operation", result.getOperationType());
            response.put("total", result.getTotalCount());
            response.put("success_count", result.getSuccessCount());
            response.put("failed_count", result.getFailedCount());
            response.put("skipped_count", result.getSkippedCount());
            response.put("message", result.getMessage());
            response.put("skipped_words", result.getSkippedWords());
            response.put("failed_words", result.getFailedWords());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Update session name
     */
    @PutMapping("/{id}/update-name")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateSessionName(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        try {
            String newName = request.get("sessionName");

            if (newName == null || newName.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Session name cannot be empty");
                return ResponseEntity.badRequest().body(error);
            }

            if (newName.length() > 200) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Session name cannot exceed 200 characters");
                return ResponseEntity.badRequest().body(error);
            }

            Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);
            if (sessionOpt.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Session not found");
                return ResponseEntity.notFound().build();
            }

            TranslationSessionEntity session = sessionOpt.get();
            translationSessionService.updateSessionName(id, newName.trim());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sessionName", newName.trim());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
