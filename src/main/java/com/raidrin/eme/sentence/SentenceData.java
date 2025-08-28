package com.raidrin.eme.sentence;

import lombok.Data;

@Data
public class SentenceData {
    private String targetLanguageLatinCharacters;    // Word in Latin characters (romanized)
    private String targetLanguageSentence;           // Sentence in source language (e.g., Hindi)
    private String targetLanguageTransliteration;    // Sentence transliteration in Latin characters
    private String sourceLanguageSentence;           // Sentence translated to target language (usually English)
    private String sourceLanguageStructure;          // Word-by-word structure analysis
}
