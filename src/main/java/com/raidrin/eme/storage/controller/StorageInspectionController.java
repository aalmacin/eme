package com.raidrin.eme.storage.controller;

import com.raidrin.eme.sentence.SentenceData;
import com.raidrin.eme.sentence.SentenceGenerationService;
import com.raidrin.eme.storage.service.SentenceStorageService;
import com.raidrin.eme.storage.service.TranslationStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageInspectionController {
    
    private final TranslationStorageService translationStorageService;
    private final SentenceStorageService sentenceStorageService;
    private final SentenceGenerationService sentenceGenerationService;
    
    @GetMapping("/translation")
    public Map<String, Object> getTranslationStorage() {
        return translationStorageService.getStorageInfo();
    }
    
    @GetMapping("/sentence")
    public Map<String, Object> getSentenceStorage() {
        return sentenceStorageService.getStorageInfo();
    }
    
    @GetMapping("/info")
    public Map<String, Object> getStorageInfo() {
        Map<String, Object> info = new HashMap<>();
        
        Map<String, Object> translationInfo = translationStorageService.getStorageInfo();
        Map<String, Object> sentenceInfo = sentenceStorageService.getStorageInfo();
        
        info.put("translations", translationInfo);
        info.put("sentences", sentenceInfo);
        info.put("message", "Database storage is persistent across application restarts");
        
        return info;
    }
    
    @GetMapping("/test/translation")
    public Map<String, Object> testTranslationStorage() {
        Map<String, Object> result = new HashMap<>();
        try {
            // Test saving and retrieving a translation
            String testWord = "टेस्ट";
            String sourceLanguage = "hi";
            String targetLanguage = "en";
            java.util.Set<String> testTranslations = java.util.Set.of("test", "trial");
            
            translationStorageService.saveTranslations(testWord, sourceLanguage, targetLanguage, testTranslations);
            
            java.util.Optional<java.util.Set<String>> retrieved = translationStorageService.findTranslations(testWord, sourceLanguage, targetLanguage);
            
            result.put("success", true);
            result.put("word", testWord);
            result.put("sourceLanguage", sourceLanguage);
            result.put("targetLanguage", targetLanguage);
            result.put("saved", testTranslations);
            result.put("retrieved", retrieved.orElse(null));
            result.put("test", retrieved.isPresent() && retrieved.get().equals(testTranslations));
            
            // Clean up
            translationStorageService.deleteTranslations(testWord, sourceLanguage, targetLanguage);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        return result;
    }
    
    @GetMapping("/test/sentence")
    public Map<String, Object> testSentenceStorage() {
        Map<String, Object> result = new HashMap<>();
        try {
            // Test saving and retrieving a sentence
            String testWord = "टेस्ट";
            String sourceLanguage = "hi";
            String targetLanguage = "en";
            SentenceData testSentence = new SentenceData();
            testSentence.setTargetLanguageLatinCharacters("test");
            testSentence.setTargetLanguageSentence("यह एक टेस्ट है।");
            testSentence.setTargetLanguageTransliteration("Yah ek test hai.");
            testSentence.setSourceLanguageSentence("This is a test.");
            testSentence.setSourceLanguageStructure("This is a test is.");
            
            sentenceStorageService.saveSentence(testWord, sourceLanguage, targetLanguage, testSentence);
            
            java.util.Optional<SentenceData> retrieved = sentenceStorageService.findSentence(testWord, sourceLanguage, targetLanguage);
            
            result.put("success", true);
            result.put("word", testWord);
            result.put("sourceLanguage", sourceLanguage);
            result.put("targetLanguage", targetLanguage);
            result.put("saved", testSentence);
            result.put("retrieved", retrieved.orElse(null));
            result.put("test", retrieved.isPresent());
            
            // Clean up
            sentenceStorageService.deleteSentence(testWord, sourceLanguage, targetLanguage);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        return result;
    }
    
    @GetMapping("/test/openai")
    public Map<String, Object> testOpenAI() {
        Map<String, Object> result = new HashMap<>();
        try {
            System.out.println("Testing OpenAI integration with database storage...");
            SentenceData data = sentenceGenerationService.generateSentence("test", "English", "Hindi");
            result.put("success", true);
            result.put("data", data);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        return result;
    }
}
