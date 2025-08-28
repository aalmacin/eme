package com.raidrin.eme.storage.service;

import com.raidrin.eme.sentence.SentenceData;
import com.raidrin.eme.storage.entity.SentenceEntity;
import com.raidrin.eme.storage.repository.SentenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SentenceStorageService {
    
    private final SentenceRepository sentenceRepository;
    
    public Optional<SentenceData> findSentence(String word, String sourceLanguage, String targetLanguage) {
        return sentenceRepository.findByWordAndSourceLanguageAndTargetLanguage(word, sourceLanguage, targetLanguage)
                .map(this::entityToSentenceData);
    }
    

    
    @Transactional
    public void saveSentence(String word, String sourceLanguage, String targetLanguage, SentenceData sentenceData) {
        Optional<SentenceEntity> existing = sentenceRepository.findByWordAndSourceLanguageAndTargetLanguage(word, sourceLanguage, targetLanguage);
        if (existing.isPresent()) {
            SentenceEntity entity = existing.get();
            updateSentenceEntity(entity, sentenceData);
            entity.setUpdatedAt(LocalDateTime.now());
            sentenceRepository.save(entity);
        } else {
            SentenceEntity entity = sentenceDataToEntity(word, sourceLanguage, targetLanguage, sentenceData);
            sentenceRepository.save(entity);
        }
        
        System.out.println("Saved sentence for: " + word + " (" + sourceLanguage + " -> " + targetLanguage + ")");
    }
    

    
    public boolean hasSentence(String word, String sourceLanguage, String targetLanguage) {
        return sentenceRepository.existsByWordAndSourceLanguageAndTargetLanguage(word, sourceLanguage, targetLanguage);
    }
    

    
    public List<String> getAllWordSentences() {
        return sentenceRepository.findAllWordSentences();
    }
    
    public Map<String, Object> getStorageInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("totalEntries", sentenceRepository.countEntries());
        info.put("keys", getAllWordSentences());
        
        // Get some sample entries
        List<SentenceEntity> recent = sentenceRepository.findRecentEntries(
            LocalDateTime.now().minusDays(7)
        );
        Map<String, Object> sampleEntries = new HashMap<>();
        recent.stream().limit(10).forEach(entity -> {
            sampleEntries.put(entity.getLookupKey(), entityToSentenceData(entity));
        });
        info.put("recentEntries", sampleEntries);
        
        return info;
    }
    
    @Transactional
    public void deleteSentence(String word, String sourceLanguage, String targetLanguage) {
        sentenceRepository.deleteByWordAndSourceLanguageAndTargetLanguage(word, sourceLanguage, targetLanguage);
    }
    

    
    // Utility Methods
    private SentenceData entityToSentenceData(SentenceEntity entity) {
        SentenceData data = new SentenceData();
        data.setTargetLanguageLatinCharacters(entity.getWordRomanized());
        data.setTargetLanguageSentence(entity.getSentenceSource());
        data.setTargetLanguageTransliteration(entity.getSentenceTransliteration());
        data.setSourceLanguageSentence(entity.getSentenceTarget());
        data.setSourceLanguageStructure(entity.getWordStructure());
        return data;
    }
    
    private SentenceEntity sentenceDataToEntity(String word, String sourceLanguage, String targetLanguage, SentenceData sentenceData) {
        return new SentenceEntity(
            word,
            sourceLanguage,
            targetLanguage,
            sentenceData.getTargetLanguageLatinCharacters(),
            sentenceData.getTargetLanguageSentence(),
            sentenceData.getTargetLanguageTransliteration(),
            sentenceData.getSourceLanguageSentence(),
            sentenceData.getSourceLanguageStructure()
        );
    }
    
    private void updateSentenceEntity(SentenceEntity entity, SentenceData sentenceData) {
        entity.setWordRomanized(sentenceData.getTargetLanguageLatinCharacters());
        entity.setSentenceSource(sentenceData.getTargetLanguageSentence());
        entity.setSentenceTransliteration(sentenceData.getTargetLanguageTransliteration());
        entity.setSentenceTarget(sentenceData.getSourceLanguageSentence());
        entity.setWordStructure(sentenceData.getSourceLanguageStructure());
    }
}
