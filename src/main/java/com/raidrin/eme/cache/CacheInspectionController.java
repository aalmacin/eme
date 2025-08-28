package com.raidrin.eme.cache;

import com.raidrin.eme.sentence.SentenceData;
import com.raidrin.eme.sentence.SentenceGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/cache")
public class CacheInspectionController {
    
    @Autowired
    private CacheManager cacheManager;
    
    @Autowired
    private CacheKeyTracker cacheKeyTracker;
    
    @Autowired
    private SentenceGenerationService sentenceGenerationService;
    
    @GetMapping("/names")
    public java.util.Collection<String> getCacheNames() {
        return cacheManager.getCacheNames();
    }
    
    @GetMapping("/translation/keys")
    public Map<String, Object> getTranslationCacheInfo() {
        Cache cache = cacheManager.getCache("translationCache");
        Map<String, Object> info = new HashMap<>();
        
        if (cache != null) {
            try {
                Set<String> trackedKeys = cacheKeyTracker.getTranslationKeys();
                Map<String, Object> cacheContents = new HashMap<>();
                
                // Get actual values for the tracked keys
                for (String key : trackedKeys) {
                    Cache.ValueWrapper wrapper = cache.get(key);
                    if (wrapper != null) {
                        cacheContents.put(key, wrapper.get());
                    }
                }
                
                info.put("name", "translationCache");
                info.put("size", cacheContents.size());
                info.put("trackedKeys", trackedKeys.size());
                info.put("contents", cacheContents);
                
            } catch (Exception e) {
                info.put("error", "Unable to access cache details: " + e.getMessage());
            }
        } else {
            info.put("error", "Translation cache not found");
        }
        
        return info;
    }
    
    @GetMapping("/sentence/keys")
    public Map<String, Object> getSentenceCacheInfo() {
        Cache cache = cacheManager.getCache("sentenceCache");
        Map<String, Object> info = new HashMap<>();
        
        if (cache != null) {
            try {
                Set<String> trackedKeys = cacheKeyTracker.getSentenceKeys();
                Map<String, Object> cacheContents = new HashMap<>();
                
                // Get actual values for the tracked keys
                for (String key : trackedKeys) {
                    Cache.ValueWrapper wrapper = cache.get(key);
                    if (wrapper != null) {
                        cacheContents.put(key, wrapper.get());
                    }
                }
                
                info.put("name", "sentenceCache");
                info.put("size", cacheContents.size());
                info.put("trackedKeys", trackedKeys.size());
                info.put("contents", cacheContents);
                
            } catch (Exception e) {
                info.put("error", "Unable to access cache details: " + e.getMessage());
            }
        } else {
            info.put("error", "Sentence cache not found");
        }
        
        return info;
    }
    
    @GetMapping("/translation/{key}")
    public Map<String, Object> getTranslationByKey(@PathVariable String key) {
        Cache cache = cacheManager.getCache("translationCache");
        Map<String, Object> result = new HashMap<>();
        
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(key);
            if (wrapper != null) {
                result.put("key", key);
                result.put("value", wrapper.get());
                result.put("found", true);
            } else {
                result.put("key", key);
                result.put("found", false);
            }
        }
        
        return result;
    }
    
    @GetMapping("/sentence/{key}")
    public Map<String, Object> getSentenceByKey(@PathVariable String key) {
        Cache cache = cacheManager.getCache("sentenceCache");
        Map<String, Object> result = new HashMap<>();
        
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(key);
            if (wrapper != null) {
                result.put("key", key);
                result.put("value", wrapper.get());
                result.put("found", true);
            } else {
                result.put("key", key);
                result.put("found", false);
            }
        }
        
        return result;
    }
    
    @GetMapping("/test/openai")
    public Map<String, Object> testOpenAI() {
        Map<String, Object> result = new HashMap<>();
        try {
            System.out.println("Testing OpenAI integration...");
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
