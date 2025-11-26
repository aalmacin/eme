package com.raidrin.eme.controller;

import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.raidrin.eme.anki.AnkiNoteCreatorService;
import com.raidrin.eme.audio.AsyncAudioGenerationService;
import com.raidrin.eme.audio.LanguageAudioCodes;
import com.raidrin.eme.codec.Codec;
import com.raidrin.eme.session.SessionOrchestrationService;
import com.raidrin.eme.storage.entity.CharacterGuideEntity;
import com.raidrin.eme.storage.entity.TranslationSessionEntity;
import com.raidrin.eme.storage.entity.WordEntity;
import com.raidrin.eme.storage.service.CharacterGuideService;
import com.raidrin.eme.storage.service.TranslationSessionService;
import com.raidrin.eme.storage.service.WordService;
import com.raidrin.eme.translator.TranslationService;
import com.raidrin.eme.util.ZipFileGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping
    public String listSessions(Model model,
                              @RequestParam(required = false) String status) {
        List<TranslationSessionEntity> sessions;

        if (status != null && !status.trim().isEmpty()) {
            try {
                TranslationSessionEntity.SessionStatus sessionStatus =
                        TranslationSessionEntity.SessionStatus.valueOf(status.toUpperCase());
                sessions = translationSessionService.findByStatus(sessionStatus);
                model.addAttribute("selectedStatus", status);
            } catch (IllegalArgumentException e) {
                sessions = translationSessionService.findAll();
            }
        } else {
            sessions = translationSessionService.findAll();
        }

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
        return "sessions/list";
    }

    @GetMapping("/{id}")
    public String viewSession(@PathVariable Long id, Model model) {
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);
        if (sessionOpt.isPresent()) {
            TranslationSessionEntity session = sessionOpt.get();
            Map<String, Object> sessionData = translationSessionService.getSessionData(id);

            // Enrich word data with word IDs and latest data from WordEntity
            if (sessionData.containsKey("words")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");
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

                // Update audio count in process_summary to reflect current state
                updateAudioCountInProcessSummary(sessionData, words);
            }

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
            return "sessions/view";
        } else {
            return "redirect:/sessions?error=not-found";
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadAssets(@PathVariable Long id) {
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TranslationSessionEntity session = sessionOpt.get();

        // Always regenerate ZIP to include latest images and assets
        if (session.getStatus() == TranslationSessionEntity.SessionStatus.COMPLETED) {
            try {
                Map<String, Object> sessionData = translationSessionService.getSessionData(id);

                // Merge with latest WordEntity data to include regenerated images
                if (sessionData.containsKey("words")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> words = (List<Map<String, Object>>) sessionData.get("words");
                    for (Map<String, Object> wordData : words) {
                        String sourceWord = (String) wordData.get("source_word");
                        if (sourceWord != null) {
                            Map<String, Object> mergedData = mergeWithLatestWordData(
                                    wordData,
                                    session.getSourceLanguage(),
                                    session.getTargetLanguage()
                            );
                            wordData.putAll(mergedData);

                            // Update image_local_path if we have a new image file
                            if (mergedData.containsKey("image_file")) {
                                String imageFile = (String) mergedData.get("image_file");
                                wordData.put("image_local_path", "./generated_images/" + imageFile);
                            }
                        }
                    }
                }

                String zipPath = zipFileGenerator.createSessionZip(session, sessionData);
                translationSessionService.updateZipFilePath(id, zipPath);
                session.setZipFilePath(zipPath);
            } catch (Exception e) {
                System.err.println("Failed to generate ZIP: " + e.getMessage());
                e.printStackTrace();
                return ResponseEntity.internalServerError().build();
            }
        } else {
            return ResponseEntity.badRequest().build();
        }

        // Serve the ZIP file
        Path zipPath = Paths.get(session.getZipFilePath());
        Resource resource = new FileSystemResource(zipPath);

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        // Sanitize the word to ensure valid filename
        String sanitizedWord = com.raidrin.eme.util.FileNameSanitizer.sanitize(session.getWord(), "")
                .replaceAll("\\.$", "")
                .replaceAll("[^a-zA-Z0-9_-]", "_");
        String filename = "session_" + id + "_" + sanitizedWord + ".zip";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    @PostMapping("/{id}/retry-word-translation")
    public String retryWordTranslation(@PathVariable Long id, @RequestParam int wordIndex) {
        // TODO: Implement individual word translation retry
        return "redirect:/sessions/" + id + "?message=word-translation-retry-not-implemented";
    }

    @PostMapping("/{id}/retry-word-sentence")
    public String retryWordSentence(@PathVariable Long id, @RequestParam int wordIndex) {
        // TODO: Implement individual word sentence retry
        return "redirect:/sessions/" + id + "?message=word-sentence-retry-not-implemented";
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

        TranslationSessionEntity session = sessionOpt.get();

        // Only allow cancellation of PENDING or IN_PROGRESS sessions
        if (session.getStatus() != TranslationSessionEntity.SessionStatus.PENDING &&
            session.getStatus() != TranslationSessionEntity.SessionStatus.IN_PROGRESS) {
            return "redirect:/sessions/" + id + "?error=cannot-cancel-" + session.getStatus().name().toLowerCase();
        }

        try {
            String cancellationReason = (reason != null && !reason.trim().isEmpty())
                    ? reason
                    : "Cancelled by user";

            translationSessionService.markAsCancelled(id, cancellationReason);

            System.out.println("Cancelled session " + id + ": " + cancellationReason);
            return "redirect:/sessions/" + id + "?message=session-cancelled";

        } catch (Exception e) {
            System.err.println("Failed to cancel session: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/sessions/" + id + "?error=cancel-failed";
        }
    }

    @PostMapping("/{id}/retry")
    @SuppressWarnings("unchecked")
    public String retrySession(@PathVariable Long id) {
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);

        if (sessionOpt.isEmpty()) {
            return "redirect:/sessions?error=session-not-found";
        }

        TranslationSessionEntity session = sessionOpt.get();

        // Only allow retry of FAILED or CANCELLED sessions
        if (session.getStatus() != TranslationSessionEntity.SessionStatus.FAILED &&
            session.getStatus() != TranslationSessionEntity.SessionStatus.CANCELLED) {
            return "redirect:/sessions/" + id + "?error=cannot-retry-" + session.getStatus().name().toLowerCase();
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

            // Reset session status to IN_PROGRESS
            translationSessionService.updateStatus(id, TranslationSessionEntity.SessionStatus.IN_PROGRESS);

            // Clear error/cancellation data from previous attempt
            Map<String, Object> updatedSessionData = new HashMap<>(sessionData);

            // Clear error data
            updatedSessionData.remove("error");
            updatedSessionData.remove("errorTime");

            // Clear cancellation data
            updatedSessionData.remove("cancelled");
            updatedSessionData.remove("cancellationTime");
            updatedSessionData.remove("cancellationReason");

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
            updatedSessionData.put("retried_from_status", session.getStatus().name());

            translationSessionService.updateSessionData(id, updatedSessionData);

            // Start async processing
            sessionOrchestrationService.processTranslationBatchAsync(id, request);

            System.out.println("Started retry processing for session " + id +
                " (retrying from " + session.getStatus() + ")");
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
            boolean enableTargetAudio = (Boolean) originalRequest.getOrDefault("enable_target_audio", false);

            List<AsyncAudioGenerationService.AudioRequest> audioRequests = new ArrayList<>();

            // Get language audio configuration
            LangAudioOption sourceLangAudio = getLangAudioOption(session.getSourceLanguage());
            LangAudioOption targetLangAudio = getLangAudioOption(session.getTargetLanguage());

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
            }

            // Generate target audio
            if (enableTargetAudio && wordData.containsKey("translations")) {
                List<String> translations = (List<String>) wordData.get("translations");
                List<String> targetAudioFiles = new ArrayList<>();

                for (String translation : translations) {
                    String targetAudioFileName = Codec.encodeForAudioFileName(translation);
                    AsyncAudioGenerationService.AudioRequest targetAudioRequest =
                            new AsyncAudioGenerationService.AudioRequest(
                                    translation,
                                    targetLangAudio.languageCode,
                                    targetLangAudio.voiceGender,
                                    targetLangAudio.voiceName,
                                    targetAudioFileName
                            );
                    audioRequests.add(targetAudioRequest);
                    targetAudioFiles.add(targetAudioFileName + ".mp3");
                }

                wordData.put("target_audio_files", targetAudioFiles);
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
            // Get audio configuration from original request or use defaults
            Map<String, Object> originalRequest = sessionData.containsKey("original_request")
                    ? (Map<String, Object>) sessionData.get("original_request")
                    : new HashMap<>();

            boolean enableSourceAudio = (Boolean) originalRequest.getOrDefault("enable_source_audio", true);
            boolean enableTargetAudio = (Boolean) originalRequest.getOrDefault("enable_target_audio", false);

            List<AsyncAudioGenerationService.AudioRequest> audioRequests = new ArrayList<>();

            // Get language audio configuration
            LangAudioOption sourceLangAudio = getLangAudioOption(session.getSourceLanguage());
            LangAudioOption targetLangAudio = getLangAudioOption(session.getTargetLanguage());

            // Process all words
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
                }

                // Generate target audio
                if (enableTargetAudio && wordData.containsKey("translations")) {
                    List<String> translations = (List<String>) wordData.get("translations");
                    List<String> targetAudioFiles = new ArrayList<>();

                    for (String translation : translations) {
                        String targetAudioFileName = Codec.encodeForAudioFileName(translation);
                        AsyncAudioGenerationService.AudioRequest targetAudioRequest =
                                new AsyncAudioGenerationService.AudioRequest(
                                        translation,
                                        targetLangAudio.languageCode,
                                        targetLangAudio.voiceGender,
                                        targetLangAudio.voiceName,
                                        targetAudioFileName
                                );
                        audioRequests.add(targetAudioRequest);
                        targetAudioFiles.add(targetAudioFileName + ".mp3");
                    }

                    wordData.put("target_audio_files", targetAudioFiles);
                }
            }

            // Generate all audio files
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

        // Count audio files from word data
        for (Map<String, Object> wordData : words) {
            // Count source audio
            if (wordData.containsKey("source_audio_file") && wordData.get("source_audio_file") != null) {
                audioCount++;
            }

            // Count target audio files
            if (wordData.containsKey("target_audio_files") && wordData.get("target_audio_files") instanceof List) {
                List<?> targetAudioFiles = (List<?>) wordData.get("target_audio_files");
                audioCount += targetAudioFiles.size();
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

        System.out.println("Updated audio count in process_summary: " + audioCount + " audio files");
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

    @PostMapping("/fix-stuck-sessions")
    public String fixStuckSessions() {
        List<TranslationSessionEntity> inProgressSessions = translationSessionService.findByStatus(
                TranslationSessionEntity.SessionStatus.IN_PROGRESS);

        int fixed = 0;
        for (TranslationSessionEntity session : inProgressSessions) {
            Map<String, Object> sessionData = translationSessionService.getSessionData(session.getId());

            // If session has image data, it actually completed successfully
            if (sessionData.containsKey("image_file") || sessionData.containsKey("gcs_url") || sessionData.containsKey("local_path")) {
                translationSessionService.updateStatus(session.getId(), TranslationSessionEntity.SessionStatus.COMPLETED);
                fixed++;
            }
        }

        return "redirect:/sessions?message=fixed-" + fixed + "-sessions";
    }

    @GetMapping("/{id}/anki-cards-preview")
    @ResponseBody
    public ResponseEntity<?> getAnkiCardsPreview(@PathVariable Long id) {
        Optional<TranslationSessionEntity> sessionOpt = translationSessionService.findById(id);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TranslationSessionEntity session = sessionOpt.get();

        // Check if session is completed
        if (session.getStatus() != TranslationSessionEntity.SessionStatus.COMPLETED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Session not completed"));
        }

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
                String frontTemplate = session.getAnkiFrontTemplate();
                String backTemplate = session.getAnkiBackTemplate();
                String frontPreview = buildAnkiFront(session, latestWordData);
                String backPreview = buildAnkiBack(session, latestWordData);

                Map<String, Object> cardPreview = new HashMap<>();
                cardPreview.put("sourceWord", latestWordData.get("source_word"));
                cardPreview.put("frontTemplate", frontTemplate != null ? frontTemplate : "[source-text]");
                cardPreview.put("backTemplate", backTemplate != null ? backTemplate : "[target-text]");
                cardPreview.put("frontPreview", frontPreview);
                cardPreview.put("backPreview", backPreview);
                cardPreview.put("wordData", latestWordData);

                cardPreviews.add(cardPreview);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("deck", session.getAnkiDeck());
        response.put("cards", cardPreviews);
        response.put("sessionId", session.getId());

        return ResponseEntity.ok(response);
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

        // Check if session is completed
        if (session.getStatus() != TranslationSessionEntity.SessionStatus.COMPLETED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Session not completed"));
        }

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

        // Check if session is completed
        if (session.getStatus() != TranslationSessionEntity.SessionStatus.COMPLETED) {
            return "redirect:/sessions/" + id + "?error=session-not-completed";
        }

        // Check if Anki is enabled
        if (!Boolean.TRUE.equals(session.getAnkiEnabled())) {
            return "redirect:/sessions/" + id + "?error=anki-not-enabled";
        }

        // Get session data
        Map<String, Object> sessionData = translationSessionService.getSessionData(id);

        try {
            int cardsCreated = 0;

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
                    String front = buildAnkiFront(session, latestWordData);
                    String back = buildAnkiBack(session, latestWordData);

                    // Create Anki card
                    ankiNoteCreatorService.addNote(session.getAnkiDeck(), front, back);
                    cardsCreated++;
                }
            }

            return "redirect:/sessions/" + id + "?message=created-" + cardsCreated + "-cards";

        } catch (Exception e) {
            System.err.println("Failed to create Anki cards: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/sessions/" + id + "?error=anki-creation-failed";
        }
    }

    private String buildAnkiFront(TranslationSessionEntity session, Map<?, ?> wordData) {
        String template = session.getAnkiFrontTemplate();
        if (template == null) {
            template = "[source-text]";
        }

        return replaceAnkiPlaceholders(template, session, wordData);
    }

    private String buildAnkiBack(TranslationSessionEntity session, Map<?, ?> wordData) {
        String template = session.getAnkiBackTemplate();
        if (template == null) {
            template = "[target-text]";
        }

        return replaceAnkiPlaceholders(template, session, wordData);
    }

    private String replaceAnkiPlaceholders(String text, TranslationSessionEntity session, Map<?, ?> wordData) {
        String result = text;

        // Replace basic placeholders
        Object sourceWord = wordData.get("source_word");
        result = result.replace("[source-text]", sourceWord != null ? sourceWord.toString() : "");

        // Replace source transliteration
        Object sourceTransliteration = wordData.get("source_transliteration");
        result = result.replace("[source_transliteration]", sourceTransliteration != null ? sourceTransliteration.toString() : "");

        // Replace translations
        if (wordData.containsKey("translations")) {
            List<?> translations = (List<?>) wordData.get("translations");
            StringBuilder sb = new StringBuilder();
            if (!translations.isEmpty()) {
                for (Object trans : translations) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(trans.toString());
                }
            }
            result = result.replace("[target-text]", sb.toString());
        }

        // Replace audio placeholders
        if (wordData.containsKey("source_audio_file")) {
            String audioFile = wordData.get("source_audio_file").toString();
            result = result.replace("[source-audio]", "[sound:" + audioFile + "]");
        }

        if (wordData.containsKey("target_audio_files") && wordData.get("target_audio_files") instanceof List) {
            List<?> audioFiles = (List<?>) wordData.get("target_audio_files");
            if (!audioFiles.isEmpty()) {
                String audioFile = audioFiles.get(0).toString();
                result = result.replace("[target-audio]", "[sound:" + audioFile + "]");
            }
        }

        // Replace sentence placeholders
        if (wordData.containsKey("sentence_data") && wordData.get("sentence_data") instanceof Map) {
            Map<?, ?> sentenceData = (Map<?, ?>) wordData.get("sentence_data");
            Object sourceSent = sentenceData.get("source_language_sentence");
            Object targetSent = sentenceData.get("target_language_sentence");
            Object latin = sentenceData.get("target_language_latin");
            Object translit = sentenceData.get("target_language_transliteration");
            Object structure = sentenceData.get("source_language_structure");

            result = result.replace("[sentence-source]", sourceSent != null ? sourceSent.toString() : "");
            result = result.replace("[sentence-target]", targetSent != null ? targetSent.toString() : "");
            result = result.replace("[sentence-latin]", latin != null ? latin.toString() : "");
            result = result.replace("[sentence-transliteration]", translit != null ? translit.toString() : "");
            result = result.replace("[sentence-structure]", structure != null ? structure.toString() : "");
        }

        if (wordData.containsKey("sentence_audio_file")) {
            String audioFile = wordData.get("sentence_audio_file").toString();
            result = result.replace("[sentence-source-audio]", "[sound:" + audioFile + "]");
        }

        // Replace mnemonic placeholders
        Object mnemonicKeyword = wordData.get("mnemonic_keyword");
        Object mnemonicSentence = wordData.get("mnemonic_sentence");
        result = result.replace("[mnemonic_keyword]", mnemonicKeyword != null ? mnemonicKeyword.toString() : "");
        result = result.replace("[mnemonic_sentence]", mnemonicSentence != null ? mnemonicSentence.toString() : "");

        if (wordData.containsKey("image_file") && wordData.get("image_file") != null) {
            String imageFile = wordData.get("image_file").toString();
            if (!imageFile.isEmpty()) {
                result = result.replace("[image]", "<img src=\"" + imageFile + "\" />");
            } else {
                result = result.replace("[image]", "");
            }
        } else {
            result = result.replace("[image]", "");
        }

        return result;
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
        if (wordEntity.getAudioTargetFile() != null) {
            List<String> targetAudioFiles = new ArrayList<>();
            targetAudioFiles.add(wordEntity.getAudioTargetFile());
            mergedData.put("target_audio_files", targetAudioFiles);
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

        // Update with latest image file (this is the key update for regenerated images)
        if (wordEntity.getImageFile() != null) {
            mergedData.put("image_file", wordEntity.getImageFile());
            mergedData.put("image_status", "success");
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
}
