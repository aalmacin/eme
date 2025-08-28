package com.raidrin.eme.sentence;

import lombok.Data;
import java.io.Serializable;

@Data
public class SentenceData implements Serializable {
    private static final long serialVersionUID = 1L;
    private String word;                             // The original word
    private String sourceLanguage;                   // Source language code
    private String targetLanguage;                   // Target language code
    private String targetLanguageLatinCharacters;    // Word in Latin characters (romanized)
    private String targetLanguageSentence;           // Sentence in source language (e.g., Hindi)
    private String targetLanguageTransliteration;    // Sentence transliteration in Latin characters
    private String sourceLanguageSentence;           // Sentence translated to target language (usually English)
    private String sourceLanguageStructure;          // Word-by-word structure analysis
}
