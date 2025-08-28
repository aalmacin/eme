package com.raidrin.eme.storage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raidrin.eme.storage.entity.TranslationEntity;
import com.raidrin.eme.storage.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TranslationStorageService {
    
    private final TranslationRepository translationRepository;
    private final ObjectMapper objectMapper;
    
    public Optional<Set<String>> findTranslations(String word, String sourceLanguage, String targetLanguage) {
        return translationRepository.findByWordAndSourceLanguageAndTargetLanguage(word, sourceLanguage, targetLanguage)
                .map(this::deserializeTranslations);
    }
    

    
    @Transactional
    public void saveTranslations(String word, String sourceLanguage, String targetLanguage, Set<String> translations) {
        String serializedTranslations = serializeTranslations(translations);
        
        Optional<TranslationEntity> existing = translationRepository.findByWordAndSourceLanguageAndTargetLanguage(word, sourceLanguage, targetLanguage);
        if (existing.isPresent()) {
            TranslationEntity entity = existing.get();
            entity.setTranslations(serializedTranslations);
            entity.setUpdatedAt(LocalDateTime.now());
            translationRepository.save(entity);
        } else {
            TranslationEntity entity = new TranslationEntity(word, sourceLanguage, targetLanguage, serializedTranslations);
            translationRepository.save(entity);
        }
        
        System.out.println("Saved translation for: " + word + " (" + sourceLanguage + " -> " + targetLanguage + ")");
    }
    

    
    public boolean hasTranslations(String word, String sourceLanguage, String targetLanguage) {
        return translationRepository.existsByWordAndSourceLanguageAndTargetLanguage(word, sourceLanguage, targetLanguage);
    }
    

    
    public List<String> getAllWordTranslations() {
        return translationRepository.findAllWordTranslations();
    }
    
    public Map<String, Object> getStorageInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("totalEntries", translationRepository.countEntries());
        info.put("keys", getAllWordTranslations());
        
        // Get some sample entries
        List<TranslationEntity> recent = translationRepository.findRecentEntries(
            LocalDateTime.now().minusDays(7)
        );
        Map<String, Object> sampleEntries = new HashMap<>();
        recent.stream().limit(10).forEach(entity -> {
            sampleEntries.put(entity.getLookupKey(), deserializeTranslations(entity));
        });
        info.put("recentEntries", sampleEntries);
        
        return info;
    }
    
    @Transactional
    public void deleteTranslations(String word, String sourceLanguage, String targetLanguage) {
        translationRepository.deleteByWordAndSourceLanguageAndTargetLanguage(word, sourceLanguage, targetLanguage);
    }
    

    
    // Utility Methods
    private String serializeTranslations(Set<String> translations) {
        try {
            return objectMapper.writeValueAsString(translations);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize translations", e);
        }
    }
    
    private Set<String> deserializeTranslations(TranslationEntity entity) {
        try {
            return objectMapper.readValue(entity.getTranslations(), new TypeReference<Set<String>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize translations", e);
        }
    }
}
