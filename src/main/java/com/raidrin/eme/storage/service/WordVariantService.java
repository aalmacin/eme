package com.raidrin.eme.storage.service;

import com.raidrin.eme.sentence.SentenceData;
import com.raidrin.eme.storage.entity.*;
import com.raidrin.eme.storage.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing word variants (translations, mnemonics, images, sentences).
 * Each word can have multiple variants of each type, with one marked as "current".
 */
@Service
@RequiredArgsConstructor
public class WordVariantService {

    private final WordRepository wordRepository;
    private final WordTranslationRepository translationRepository;
    private final WordMnemonicRepository mnemonicRepository;
    private final WordImageRepository imageRepository;
    private final WordSentenceRepository sentenceRepository;
    private final CharacterGuideRepository characterGuideRepository;

    // ==================== Translation Variants ====================

    @Transactional
    public WordTranslationEntity addTranslation(Long wordId, String translation, String transliteration,
                                                 String source, boolean setAsCurrent, boolean isUserCreated) {
        WordEntity word = wordRepository.findById(wordId)
            .orElseThrow(() -> new IllegalArgumentException("Word not found: " + wordId));

        if (setAsCurrent) {
            translationRepository.clearCurrentForWord(wordId);
        }

        WordTranslationEntity entity = new WordTranslationEntity(
            word, translation, transliteration, source, setAsCurrent, isUserCreated
        );

        return translationRepository.save(entity);
    }

    @Transactional
    public WordTranslationEntity addTranslation(String wordText, String sourceLanguage, String targetLanguage,
                                                 String translation, String transliteration,
                                                 String source, boolean setAsCurrent, boolean isUserCreated) {
        WordEntity word = wordRepository.findByWordAndSourceLanguageAndTargetLanguage(
            wordText, sourceLanguage, targetLanguage
        ).orElseThrow(() -> new IllegalArgumentException(
            "Word not found: " + wordText + " (" + sourceLanguage + " -> " + targetLanguage + ")"
        ));

        return addTranslation(word.getId(), translation, transliteration, source, setAsCurrent, isUserCreated);
    }

    @Transactional
    public void setCurrentTranslation(Long wordId, Long translationId) {
        translationRepository.clearCurrentForWord(wordId);
        translationRepository.setAsCurrent(translationId);
    }

    public List<WordTranslationEntity> getTranslationHistory(Long wordId) {
        return translationRepository.findByWordIdOrderByCreatedAtDesc(wordId);
    }

    public Optional<WordTranslationEntity> getCurrentTranslation(Long wordId) {
        return translationRepository.findByWordIdAndIsCurrent(wordId, true);
    }

    // ==================== Mnemonic Variants ====================

    @Transactional
    public WordMnemonicEntity addMnemonic(Long wordId, String keyword, String sentence,
                                           Long characterGuideId, boolean setAsCurrent, boolean isUserCreated) {
        WordEntity word = wordRepository.findById(wordId)
            .orElseThrow(() -> new IllegalArgumentException("Word not found: " + wordId));

        CharacterGuideEntity characterGuide = null;
        if (characterGuideId != null) {
            characterGuide = characterGuideRepository.findById(characterGuideId).orElse(null);
        }

        if (setAsCurrent) {
            mnemonicRepository.clearCurrentForWord(wordId);
        }

        WordMnemonicEntity entity = new WordMnemonicEntity(
            word, keyword, sentence, characterGuide, setAsCurrent, isUserCreated
        );

        return mnemonicRepository.save(entity);
    }

    @Transactional
    public WordMnemonicEntity addMnemonic(String wordText, String sourceLanguage, String targetLanguage,
                                           String keyword, String sentence,
                                           Long characterGuideId, boolean setAsCurrent, boolean isUserCreated) {
        WordEntity word = wordRepository.findByWordAndSourceLanguageAndTargetLanguage(
            wordText, sourceLanguage, targetLanguage
        ).orElseThrow(() -> new IllegalArgumentException(
            "Word not found: " + wordText + " (" + sourceLanguage + " -> " + targetLanguage + ")"
        ));

        return addMnemonic(word.getId(), keyword, sentence, characterGuideId, setAsCurrent, isUserCreated);
    }

    @Transactional
    public void setCurrentMnemonic(Long wordId, Long mnemonicId) {
        mnemonicRepository.clearCurrentForWord(wordId);
        mnemonicRepository.setAsCurrent(mnemonicId);
    }

    public List<WordMnemonicEntity> getMnemonicHistory(Long wordId) {
        return mnemonicRepository.findByWordIdOrderByCreatedAtDesc(wordId);
    }

    public Optional<WordMnemonicEntity> getCurrentMnemonic(Long wordId) {
        return mnemonicRepository.findByWordIdAndIsCurrent(wordId, true);
    }

    // ==================== Image Variants ====================

    @Transactional
    public WordImageEntity addImage(Long wordId, String imageFile, String gcsUrl,
                                     String prompt, String style, boolean setAsCurrent) {
        WordEntity word = wordRepository.findById(wordId)
            .orElseThrow(() -> new IllegalArgumentException("Word not found: " + wordId));

        if (setAsCurrent) {
            imageRepository.clearCurrentForWord(wordId);
        }

        WordImageEntity entity = new WordImageEntity(
            word, imageFile, gcsUrl, prompt, style, setAsCurrent
        );

        return imageRepository.save(entity);
    }

    @Transactional
    public WordImageEntity addImage(String wordText, String sourceLanguage, String targetLanguage,
                                     String imageFile, String gcsUrl,
                                     String prompt, String style, boolean setAsCurrent) {
        WordEntity word = wordRepository.findByWordAndSourceLanguageAndTargetLanguage(
            wordText, sourceLanguage, targetLanguage
        ).orElseThrow(() -> new IllegalArgumentException(
            "Word not found: " + wordText + " (" + sourceLanguage + " -> " + targetLanguage + ")"
        ));

        return addImage(word.getId(), imageFile, gcsUrl, prompt, style, setAsCurrent);
    }

    @Transactional
    public void setCurrentImage(Long wordId, Long imageId) {
        imageRepository.clearCurrentForWord(wordId);
        imageRepository.setAsCurrent(imageId);
    }

    public List<WordImageEntity> getImageHistory(Long wordId) {
        return imageRepository.findByWordIdOrderByCreatedAtDesc(wordId);
    }

    public Optional<WordImageEntity> getCurrentImage(Long wordId) {
        return imageRepository.findByWordIdAndIsCurrent(wordId, true);
    }

    // ==================== Sentence Variants ====================

    @Transactional
    public WordSentenceEntity addSentence(Long wordId, String sentenceSource, String sentenceTransliteration,
                                           String sentenceTarget, String wordStructure, String wordRomanized,
                                           String audioFile, boolean setAsCurrent) {
        WordEntity word = wordRepository.findById(wordId)
            .orElseThrow(() -> new IllegalArgumentException("Word not found: " + wordId));

        if (setAsCurrent) {
            sentenceRepository.clearCurrentForWord(wordId);
        }

        WordSentenceEntity entity = new WordSentenceEntity(
            word, sentenceSource, sentenceTransliteration, sentenceTarget,
            wordStructure, wordRomanized, audioFile, setAsCurrent
        );

        return sentenceRepository.save(entity);
    }

    @Transactional
    public WordSentenceEntity addSentence(Long wordId, SentenceData sentenceData, String audioFile, boolean setAsCurrent) {
        return addSentence(
            wordId,
            sentenceData.getSourceLanguageSentence(),
            sentenceData.getTargetLanguageTransliteration(),
            sentenceData.getTargetLanguageSentence(),
            sentenceData.getSourceLanguageStructure(),
            sentenceData.getTargetLanguageLatinCharacters(),
            audioFile,
            setAsCurrent
        );
    }

    @Transactional
    public WordSentenceEntity addSentence(String wordText, String sourceLanguage, String targetLanguage,
                                           SentenceData sentenceData, String audioFile, boolean setAsCurrent) {
        WordEntity word = wordRepository.findByWordAndSourceLanguageAndTargetLanguage(
            wordText, sourceLanguage, targetLanguage
        ).orElseThrow(() -> new IllegalArgumentException(
            "Word not found: " + wordText + " (" + sourceLanguage + " -> " + targetLanguage + ")"
        ));

        return addSentence(word.getId(), sentenceData, audioFile, setAsCurrent);
    }

    @Transactional
    public void setCurrentSentence(Long wordId, Long sentenceId) {
        sentenceRepository.clearCurrentForWord(wordId);
        sentenceRepository.setAsCurrent(sentenceId);
    }

    public List<WordSentenceEntity> getSentenceHistory(Long wordId) {
        return sentenceRepository.findByWordIdOrderByCreatedAtDesc(wordId);
    }

    public Optional<WordSentenceEntity> getCurrentSentence(Long wordId) {
        return sentenceRepository.findByWordIdAndIsCurrent(wordId, true);
    }

    @Transactional
    public void updateSentenceAudioFile(Long sentenceId, String audioFile) {
        sentenceRepository.findById(sentenceId).ifPresent(sentence -> {
            sentence.setAudioFile(audioFile);
            sentenceRepository.save(sentence);
        });
    }

    // ==================== Utility Methods ====================

    /**
     * Get counts of all variant types for a word
     */
    public VariantCounts getVariantCounts(Long wordId) {
        return new VariantCounts(
            translationRepository.countByWordId(wordId),
            mnemonicRepository.countByWordId(wordId),
            imageRepository.countByWordId(wordId),
            sentenceRepository.countByWordId(wordId)
        );
    }

    public record VariantCounts(
        long translations,
        long mnemonics,
        long images,
        long sentences
    ) {}
}
