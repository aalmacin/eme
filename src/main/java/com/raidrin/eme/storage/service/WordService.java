package com.raidrin.eme.storage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raidrin.eme.storage.entity.WordEntity;
import com.raidrin.eme.storage.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WordService {

    private final WordRepository wordRepository;
    private final ObjectMapper objectMapper;

    public Optional<WordEntity> findWord(String word, String sourceLanguage, String targetLanguage) {
        validateParameters(word, sourceLanguage, targetLanguage);
        return wordRepository.findByWordAndSourceLanguageAndTargetLanguage(word, sourceLanguage, targetLanguage);
    }

    @Transactional
    public WordEntity saveOrUpdateWord(String word, String sourceLanguage, String targetLanguage) {
        validateParameters(word, sourceLanguage, targetLanguage);

        Optional<WordEntity> existing = wordRepository.findByWordAndSourceLanguageAndTargetLanguage(word, sourceLanguage, targetLanguage);
        if (existing.isPresent()) {
            return existing.get();
        } else {
            WordEntity entity = new WordEntity(word, sourceLanguage, targetLanguage);
            return wordRepository.save(entity);
        }
    }

    @Transactional
    public WordEntity updateTranslation(String word, String sourceLanguage, String targetLanguage, Set<String> translations) {
        validateParameters(word, sourceLanguage, targetLanguage);
        if (translations == null || translations.isEmpty()) {
            throw new IllegalArgumentException("Translations must be provided");
        }

        WordEntity entity = saveOrUpdateWord(word, sourceLanguage, targetLanguage);
        entity.setTranslation(serializeTranslations(translations));
        return wordRepository.save(entity);
    }

    @Transactional
    public WordEntity updateTranslationWithManualOverride(String word, String sourceLanguage, String targetLanguage, Set<String> translations) {
        validateParameters(word, sourceLanguage, targetLanguage);
        if (translations == null || translations.isEmpty()) {
            throw new IllegalArgumentException("Translations must be provided");
        }

        WordEntity entity = saveOrUpdateWord(word, sourceLanguage, targetLanguage);
        entity.setTranslation(serializeTranslations(translations));
        entity.setTranslationOverrideAt(LocalDateTime.now());
        return wordRepository.save(entity);
    }

    @Transactional
    public WordEntity updateAudio(String word, String sourceLanguage, String targetLanguage,
                                   String audioSourceFile, String audioTargetFile) {
        validateParameters(word, sourceLanguage, targetLanguage);

        WordEntity entity = saveOrUpdateWord(word, sourceLanguage, targetLanguage);
        if (audioSourceFile != null) {
            entity.setAudioSourceFile(audioSourceFile);
        }
        if (audioTargetFile != null) {
            entity.setAudioTargetFile(audioTargetFile);
        }
        return wordRepository.save(entity);
    }

    @Transactional
    public WordEntity updateImage(String word, String sourceLanguage, String targetLanguage,
                                   String imageFile, String imagePrompt) {
        validateParameters(word, sourceLanguage, targetLanguage);

        WordEntity entity = saveOrUpdateWord(word, sourceLanguage, targetLanguage);
        if (imageFile != null) {
            entity.setImageFile(imageFile);
        }
        if (imagePrompt != null) {
            entity.setImagePrompt(imagePrompt);
        }
        return wordRepository.save(entity);
    }

    @Transactional
    public WordEntity updateMnemonic(String word, String sourceLanguage, String targetLanguage,
                                      String mnemonicKeyword, String mnemonicSentence, String imagePrompt) {
        validateParameters(word, sourceLanguage, targetLanguage);

        WordEntity entity = saveOrUpdateWord(word, sourceLanguage, targetLanguage);
        if (mnemonicKeyword != null) {
            entity.setMnemonicKeyword(mnemonicKeyword);
        }
        if (mnemonicSentence != null) {
            entity.setMnemonicSentence(mnemonicSentence);
        }
        if (imagePrompt != null) {
            entity.setImagePrompt(imagePrompt);
        }
        return wordRepository.save(entity);
    }

    @Transactional
    public WordEntity updateMnemonicKeywordWithManualOverride(String word, String sourceLanguage, String targetLanguage,
                                                               String mnemonicKeyword, String imagePrompt) {
        validateParameters(word, sourceLanguage, targetLanguage);
        if (mnemonicKeyword == null || mnemonicKeyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Mnemonic keyword must be provided");
        }

        WordEntity entity = saveOrUpdateWord(word, sourceLanguage, targetLanguage);
        entity.setMnemonicKeyword(mnemonicKeyword);
        entity.setMnemonicKeywordUpdatedAt(LocalDateTime.now());

        if (imagePrompt != null) {
            entity.setImagePrompt(imagePrompt);
        }

        return wordRepository.save(entity);
    }

    @Transactional
    public WordEntity updateImagePromptAndClearImage(Long wordId, String newImagePrompt) {
        WordEntity entity = wordRepository.findById(wordId)
            .orElseThrow(() -> new IllegalArgumentException("Word not found with ID: " + wordId));

        entity.setImagePrompt(newImagePrompt);
        // Clear the old image file since we're regenerating
        entity.setImageFile(null);
        return wordRepository.save(entity);
    }

    @Transactional
    public WordEntity updateMnemonicAndClearImage(String word, String sourceLanguage, String targetLanguage,
                                                   String mnemonicKeyword, String mnemonicSentence, String imagePrompt) {
        validateParameters(word, sourceLanguage, targetLanguage);

        WordEntity entity = saveOrUpdateWord(word, sourceLanguage, targetLanguage);

        // Update mnemonic keyword only if provided (null means preserve existing)
        if (mnemonicKeyword != null) {
            entity.setMnemonicKeyword(mnemonicKeyword);
        }

        // Update mnemonic sentence if provided
        if (mnemonicSentence != null) {
            entity.setMnemonicSentence(mnemonicSentence);
        }

        // Update image prompt if provided
        if (imagePrompt != null) {
            entity.setImagePrompt(imagePrompt);
        }

        // Clear the old image file since we're regenerating
        entity.setImageFile(null);

        return wordRepository.save(entity);
    }

    @Transactional
    public WordEntity updateTransliteration(String word, String sourceLanguage, String targetLanguage,
                                             String sourceTransliteration) {
        validateParameters(word, sourceLanguage, targetLanguage);

        WordEntity entity = saveOrUpdateWord(word, sourceLanguage, targetLanguage);
        entity.setSourceTransliteration(sourceTransliteration);
        return wordRepository.save(entity);
    }

    @Transactional
    public WordEntity updateTransliterationWithManualOverride(String word, String sourceLanguage, String targetLanguage,
                                                               String sourceTransliteration) {
        validateParameters(word, sourceLanguage, targetLanguage);
        if (sourceTransliteration == null || sourceTransliteration.trim().isEmpty()) {
            throw new IllegalArgumentException("Transliteration must be provided");
        }

        WordEntity entity = saveOrUpdateWord(word, sourceLanguage, targetLanguage);
        entity.setSourceTransliteration(sourceTransliteration);
        entity.setTransliterationOverrideAt(LocalDateTime.now());
        return wordRepository.save(entity);
    }

    @Transactional
    public WordEntity updateCharacterGuide(String word, String sourceLanguage, String targetLanguage,
                                            Long characterGuideId) {
        validateParameters(word, sourceLanguage, targetLanguage);

        WordEntity entity = saveOrUpdateWord(word, sourceLanguage, targetLanguage);
        entity.setCharacterGuideId(characterGuideId);
        return wordRepository.save(entity);
    }

    @Transactional
    public WordEntity clearTranslationOverride(String word, String sourceLanguage, String targetLanguage) {
        validateParameters(word, sourceLanguage, targetLanguage);

        WordEntity entity = saveOrUpdateWord(word, sourceLanguage, targetLanguage);
        entity.setTranslationOverrideAt(null);
        return wordRepository.save(entity);
    }

    @Transactional
    public WordEntity clearTransliterationOverride(String word, String sourceLanguage, String targetLanguage) {
        validateParameters(word, sourceLanguage, targetLanguage);

        WordEntity entity = saveOrUpdateWord(word, sourceLanguage, targetLanguage);
        entity.setTransliterationOverrideAt(null);
        return wordRepository.save(entity);
    }

    @Transactional
    public WordEntity clearMnemonicKeywordOverride(String word, String sourceLanguage, String targetLanguage) {
        validateParameters(word, sourceLanguage, targetLanguage);

        WordEntity entity = saveOrUpdateWord(word, sourceLanguage, targetLanguage);
        entity.setMnemonicKeywordUpdatedAt(null);
        return wordRepository.save(entity);
    }

    @Transactional
    public WordEntity updateImageStatus(String word, String sourceLanguage, String targetLanguage,
                                         String imageStatus) {
        validateParameters(word, sourceLanguage, targetLanguage);

        WordEntity entity = saveOrUpdateWord(word, sourceLanguage, targetLanguage);
        entity.setImageStatus(imageStatus);
        return wordRepository.save(entity);
    }

    @Transactional
    public WordEntity updateAudioStatus(String word, String sourceLanguage, String targetLanguage,
                                         String audioStatus) {
        validateParameters(word, sourceLanguage, targetLanguage);

        WordEntity entity = saveOrUpdateWord(word, sourceLanguage, targetLanguage);
        entity.setAudioStatus(audioStatus);
        return wordRepository.save(entity);
    }

    public boolean hasWord(String word, String sourceLanguage, String targetLanguage) {
        validateParameters(word, sourceLanguage, targetLanguage);
        return wordRepository.existsByWordAndSourceLanguageAndTargetLanguage(word, sourceLanguage, targetLanguage);
    }

    public List<WordEntity> getAllWords() {
        return wordRepository.findAll();
    }

    public Page<WordEntity> getAllWords(Pageable pageable) {
        return wordRepository.findAll(pageable);
    }

    public List<WordEntity> getAllWordsWithImages() {
        return wordRepository.findAllWithImages();
    }

    @Transactional
    public void deleteWord(String word, String sourceLanguage, String targetLanguage) {
        validateParameters(word, sourceLanguage, targetLanguage);
        wordRepository.deleteByWordAndSourceLanguageAndTargetLanguage(word, sourceLanguage, targetLanguage);
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

    // Utility Methods
    private String serializeTranslations(Set<String> translations) {
        try {
            return objectMapper.writeValueAsString(translations);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize translations", e);
        }
    }

    public Set<String> deserializeTranslations(String translationJson) {
        try {
            return objectMapper.readValue(translationJson, new TypeReference<Set<String>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize translations", e);
        }
    }

    // Override Checking Methods
    public boolean isTranslationOverridden(WordEntity word) {
        return word.getTranslationOverrideAt() != null;
    }

    public boolean isTransliterationOverridden(WordEntity word) {
        return word.getTransliterationOverrideAt() != null;
    }

    public boolean isMnemonicKeywordOverridden(WordEntity word) {
        return word.getMnemonicKeywordUpdatedAt() != null;
    }

    // Prerequisite Validation Methods
    public boolean hasTranslation(WordEntity word) {
        return word.getTranslation() != null && !word.getTranslation().trim().isEmpty();
    }

    public boolean hasMnemonicKeyword(WordEntity word) {
        return word.getMnemonicKeyword() != null && !word.getMnemonicKeyword().trim().isEmpty();
    }

    public boolean hasMnemonicSentence(WordEntity word) {
        return word.getMnemonicSentence() != null && !word.getMnemonicSentence().trim().isEmpty();
    }

    public boolean hasImagePrerequisites(WordEntity word) {
        return hasTranslation(word) && hasMnemonicKeyword(word) && hasMnemonicSentence(word);
    }
}
