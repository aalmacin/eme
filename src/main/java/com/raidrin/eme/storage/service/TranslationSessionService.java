package com.raidrin.eme.storage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raidrin.eme.storage.entity.TranslationSessionEntity;
import com.raidrin.eme.storage.entity.TranslationSessionEntity.SessionStatus;
import com.raidrin.eme.storage.entity.WordEntity;
import com.raidrin.eme.storage.repository.TranslationSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TranslationSessionService {

    private final TranslationSessionRepository sessionRepository;
    private final WordService wordService;
    private final ObjectMapper objectMapper;

    @Transactional
    public TranslationSessionEntity createSession(String word, String sourceLanguage, String targetLanguage,
                                                   boolean imageGenerationEnabled, boolean audioGenerationEnabled) {
        return createSession(word, sourceLanguage, targetLanguage,
                imageGenerationEnabled, audioGenerationEnabled,
                false, false, false, null, null, null);
    }

    @Transactional
    public TranslationSessionEntity createSession(String word, String sourceLanguage, String targetLanguage,
                                                   boolean imageGenerationEnabled, boolean audioGenerationEnabled,
                                                   boolean sentenceGenerationEnabled, boolean ankiEnabled,
                                                   boolean overrideTranslationEnabled,
                                                   String ankiDeck, String ankiFrontTemplate, String ankiBackTemplate) {
        validateParameters(word, sourceLanguage, targetLanguage);

        TranslationSessionEntity session = new TranslationSessionEntity(
                word, sourceLanguage, targetLanguage,
                imageGenerationEnabled, audioGenerationEnabled,
                sentenceGenerationEnabled, ankiEnabled,
                overrideTranslationEnabled,
                ankiDeck, ankiFrontTemplate, ankiBackTemplate
        );

        return sessionRepository.save(session);
    }

    public Optional<TranslationSessionEntity> findById(Long id) {
        return sessionRepository.findById(id);
    }

    public List<TranslationSessionEntity> findAll() {
        return sessionRepository.findAllOrderedByCreatedAtDesc();
    }

    public List<TranslationSessionEntity> findByStatus(SessionStatus status) {
        return sessionRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public List<TranslationSessionEntity> findRecentSessions(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return sessionRepository.findRecentSessions(since);
    }

    @Transactional
    public void updateStatus(Long sessionId, SessionStatus status) {
        TranslationSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        session.setStatus(status);
        if (status == SessionStatus.COMPLETED || status == SessionStatus.FAILED) {
            session.setCompletedAt(LocalDateTime.now());
        } else if (status == SessionStatus.CANCELLED) {
            session.setCancelledAt(LocalDateTime.now());
        }

        sessionRepository.save(session);
    }

    @Transactional
    public void updateSessionData(Long sessionId, Map<String, Object> data) {
        TranslationSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        String jsonData = serializeData(data);
        session.setSessionData(jsonData);

        sessionRepository.save(session);
    }

    @Transactional
    public void updateZipFilePath(Long sessionId, String zipFilePath) {
        TranslationSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        session.setZipFilePath(zipFilePath);

        sessionRepository.save(session);
    }

    @Transactional
    public void markAsCompleted(Long sessionId, String zipFilePath) {
        TranslationSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        session.setStatus(SessionStatus.COMPLETED);
        session.setZipFilePath(zipFilePath);
        session.setCompletedAt(LocalDateTime.now());

        sessionRepository.save(session);
    }

    @Transactional
    public void markAsFailed(Long sessionId, String errorMessage) {
        TranslationSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        session.setStatus(SessionStatus.FAILED);
        session.setCompletedAt(LocalDateTime.now());

        // Add error to session data
        Map<String, Object> currentData = deserializeData(session.getSessionData());
        currentData.put("error", errorMessage);
        currentData.put("errorTime", LocalDateTime.now().toString());
        session.setSessionData(serializeData(currentData));

        sessionRepository.save(session);
    }

    @Transactional
    public void markAsCancelled(Long sessionId, String cancellationReason) {
        TranslationSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        session.setStatus(SessionStatus.CANCELLED);
        session.setCancelledAt(LocalDateTime.now());
        session.setCancellationReason(cancellationReason != null ? cancellationReason : "Cancelled by user");

        // Add cancellation info to session data
        Map<String, Object> currentData = deserializeData(session.getSessionData());
        currentData.put("cancelled", true);
        currentData.put("cancellationTime", LocalDateTime.now().toString());
        currentData.put("cancellationReason", session.getCancellationReason());
        session.setSessionData(serializeData(currentData));

        sessionRepository.save(session);
    }

    public Map<String, Object> getSessionData(Long sessionId) {
        TranslationSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        return deserializeData(session.getSessionData());
    }

    public long countByStatus(SessionStatus status) {
        return sessionRepository.countByStatus(status);
    }

    /**
     * Find existing word data from WordEntity (preferred) or a previous completed session
     * Returns the word data if found, or null if not found
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> findExistingWordData(String sourceWord, String sourceLanguage, String targetLanguage) {
        // First, check WordEntity (centralized word storage)
        Optional<WordEntity> wordEntity = wordService.findWord(sourceWord, sourceLanguage, targetLanguage);
        if (wordEntity.isPresent()) {
            WordEntity word = wordEntity.get();
            // Only return if the word has translation (minimum requirement)
            if (word.getTranslation() != null && !word.getTranslation().isEmpty()) {
                return convertWordEntityToMap(word);
            }
        }

        // Fallback: Find completed sessions with matching languages (for backward compatibility)
        List<TranslationSessionEntity> completedSessions = sessionRepository.findByStatusOrderByCreatedAtDesc(SessionStatus.COMPLETED);

        for (TranslationSessionEntity session : completedSessions) {
            // Check if session has matching languages
            if (!session.getSourceLanguage().equals(sourceLanguage) ||
                !session.getTargetLanguage().equals(targetLanguage)) {
                continue;
            }

            // Parse session data
            Map<String, Object> sessionData = deserializeData(session.getSessionData());

            if (!sessionData.containsKey("words")) {
                continue;
            }

            List<?> words = (List<?>) sessionData.get("words");
            for (Object wordObj : words) {
                if (!(wordObj instanceof Map)) {
                    continue;
                }

                Map<?, ?> wordData = (Map<?, ?>) wordObj;
                String word = (String) wordData.get("source_word");

                // Check if this is the word we're looking for
                if (sourceWord.equals(word)) {
                    // Check if all required processes succeeded
                    boolean translationSuccess = "success".equals(wordData.get("translation_status"));

                    // Only return if translation succeeded (core requirement)
                    if (translationSuccess) {
                        // Convert to mutable map
                        return new java.util.HashMap<>((Map<String, Object>) wordData);
                    }
                }
            }
        }

        return null; // No existing data found
    }

    /**
     * Convert WordEntity to the Map format expected by SessionOrchestrationService
     */
    private Map<String, Object> convertWordEntityToMap(WordEntity wordEntity) {
        Map<String, Object> wordData = new HashMap<>();
        wordData.put("source_word", wordEntity.getWord());

        // Translation
        if (wordEntity.getTranslation() != null) {
            try {
                Set<String> translations = wordService.deserializeTranslations(wordEntity.getTranslation());
                wordData.put("translations", new ArrayList<>(translations));
                wordData.put("translation_status", "success");
            } catch (Exception e) {
                wordData.put("translation_status", "failed");
            }
        }

        // Audio files
        if (wordEntity.getAudioSourceFile() != null) {
            wordData.put("source_audio_file", wordEntity.getAudioSourceFile());
        }
        if (wordEntity.getAudioTargetFile() != null) {
            List<String> targetAudioFiles = new ArrayList<>();
            targetAudioFiles.add(wordEntity.getAudioTargetFile());
            wordData.put("target_audio_files", targetAudioFiles);
        }

        // Image data
        if (wordEntity.getImageFile() != null) {
            wordData.put("image_file", wordEntity.getImageFile());
            wordData.put("image_status", "success");
        }
        if (wordEntity.getImagePrompt() != null) {
            wordData.put("image_prompt", wordEntity.getImagePrompt());
        }

        // Mnemonic data
        if (wordEntity.getMnemonicKeyword() != null) {
            wordData.put("mnemonic_keyword", wordEntity.getMnemonicKeyword());
        }
        if (wordEntity.getMnemonicSentence() != null) {
            wordData.put("mnemonic_sentence", wordEntity.getMnemonicSentence());
        }

        return wordData;
    }

    /**
     * Save word data to WordEntity for future reuse
     * This is called after word data is generated in a session
     */
    @Transactional
    public void saveWordDataToEntity(Map<String, Object> wordData, String sourceLanguage, String targetLanguage) {
        String sourceWord = (String) wordData.get("source_word");
        if (sourceWord == null) {
            return; // No word to save
        }

        // Ensure word entity exists
        WordEntity wordEntity = wordService.saveOrUpdateWord(sourceWord, sourceLanguage, targetLanguage);

        // Update translation if available and not manually overridden
        if (wordData.containsKey("translations") && "success".equals(wordData.get("translation_status"))) {
            // Check if translation has been manually overridden
            if (wordEntity.getTranslationOverrideAt() == null) {
                @SuppressWarnings("unchecked")
                List<String> translationsList = (List<String>) wordData.get("translations");
                if (translationsList != null && !translationsList.isEmpty()) {
                    Set<String> translations = new HashSet<>(translationsList);
                    wordService.updateTranslation(sourceWord, sourceLanguage, targetLanguage, translations);
                }
            } else {
                System.out.println("Skipping automated translation update for manually overridden word: " + sourceWord +
                        " (override at: " + wordEntity.getTranslationOverrideAt() + ")");
            }
        }

        // Update transliteration if available
        if (wordData.containsKey("source_transliteration")) {
            String transliteration = (String) wordData.get("source_transliteration");
            if (transliteration != null && !transliteration.isEmpty()) {
                wordService.updateTransliteration(sourceWord, sourceLanguage, targetLanguage, transliteration);
            }
        }

        // Update audio files if available
        String audioSourceFile = (String) wordData.get("source_audio_file");
        String audioTargetFile = null;
        if (wordData.containsKey("target_audio_files")) {
            @SuppressWarnings("unchecked")
            List<String> targetAudioFiles = (List<String>) wordData.get("target_audio_files");
            if (targetAudioFiles != null && !targetAudioFiles.isEmpty()) {
                audioTargetFile = targetAudioFiles.get(0); // Use first target audio
            }
        }
        if (audioSourceFile != null || audioTargetFile != null) {
            wordService.updateAudio(sourceWord, sourceLanguage, targetLanguage, audioSourceFile, audioTargetFile);
        }

        // Update mnemonic and image if available
        String mnemonicKeyword = (String) wordData.get("mnemonic_keyword");
        String mnemonicSentence = (String) wordData.get("mnemonic_sentence");
        String imagePrompt = (String) wordData.get("image_prompt");
        String imageFile = (String) wordData.get("image_file");

        if (mnemonicKeyword != null || mnemonicSentence != null || imagePrompt != null) {
            // Check if mnemonic keyword has been manually updated
            if (wordEntity.getMnemonicKeywordUpdatedAt() != null && mnemonicKeyword != null) {
                // Skip updating mnemonic keyword if it was manually overridden
                System.out.println("Skipping automated mnemonic keyword update for manually overridden word: " + sourceWord +
                        " (updated at: " + wordEntity.getMnemonicKeywordUpdatedAt() + ")");
                // Only update mnemonic sentence and image prompt, not the keyword
                wordService.updateMnemonic(sourceWord, sourceLanguage, targetLanguage, null, mnemonicSentence, imagePrompt);
            } else {
                // No manual override, update everything normally
                wordService.updateMnemonic(sourceWord, sourceLanguage, targetLanguage, mnemonicKeyword, mnemonicSentence, imagePrompt);
            }
        }

        if (imageFile != null && "success".equals(wordData.get("image_status"))) {
            wordService.updateImage(sourceWord, sourceLanguage, targetLanguage, imageFile, imagePrompt);
        }
    }

    private void validateParameters(String word, String sourceLanguage, String targetLanguage) {
        if (word == null || word.trim().isEmpty()) {
            throw new IllegalArgumentException("Word must be provided");
        }
        if (sourceLanguage == null || sourceLanguage.trim().isEmpty()) {
            throw new IllegalArgumentException("Source language must be provided");
        }
        if (targetLanguage == null || targetLanguage.trim().isEmpty()) {
            throw new IllegalArgumentException("Target language must be provided");
        }
    }

    private String serializeData(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize session data", e);
        }
    }

    private Map<String, Object> deserializeData(String jsonData) {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return new java.util.HashMap<>();
        }
        try {
            return objectMapper.readValue(jsonData, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize session data", e);
        }
    }
}
