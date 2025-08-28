package com.raidrin.eme.cache;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CacheKeyTracker {
    
    private final Set<String> translationKeys = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> sentenceKeys = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    public void addTranslationKey(String key) {
        translationKeys.add(key);
    }
    
    public void addSentenceKey(String key) {
        sentenceKeys.add(key);
    }
    
    public Set<String> getTranslationKeys() {
        return Collections.unmodifiableSet(translationKeys);
    }
    
    public Set<String> getSentenceKeys() {
        return Collections.unmodifiableSet(sentenceKeys);
    }
    
    public int getTranslationCacheSize() {
        return translationKeys.size();
    }
    
    public int getSentenceCacheSize() {
        return sentenceKeys.size();
    }
}
