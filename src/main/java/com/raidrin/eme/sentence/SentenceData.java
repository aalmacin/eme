package com.raidrin.eme.sentence;

import lombok.Data;
import java.io.Serializable;

@Data
public class SentenceData implements Serializable {
    private static final long serialVersionUID = 1L;
    private String word;                             // The original word
    private String sourceLanguage;                   // Source language code (e.g., "hi" for Hindi)
    private String targetLanguage;                   // Target language code (e.g., "en" for English)
    private String targetLanguageLatinCharacters;    // Word in Latin characters (romanized)
    private String sourceLanguageSentence;           // Sentence in source language (e.g., Hindi sentence for Hindi word)
    private String targetLanguageTransliteration;    // Sentence transliteration in Latin characters
    private String targetLanguageSentence;           // Sentence in target language (e.g., English translation)
    private String sourceLanguageStructure;          // Word-by-word structure analysis
    private String audioFile;                        // Audio file name for the sentence (e.g., "encoded-sentence.mp3")
}
