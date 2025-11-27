package com.raidrin.eme.storage.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "sentences")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SentenceEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "word", nullable = false, columnDefinition = "TEXT")
    private String word;
    
    @Column(name = "source_language", nullable = false, length = 10)
    private String sourceLanguage;
    
    @Column(name = "target_language", nullable = false, length = 10)
    private String targetLanguage;
    
    @Column(name = "word_romanized", columnDefinition = "TEXT")
    private String wordRomanized;
    
    @Column(name = "sentence_source", columnDefinition = "TEXT")
    private String sentenceSource;
    
    @Column(name = "sentence_transliteration", columnDefinition = "TEXT")
    private String sentenceTransliteration;
    
    @Column(name = "sentence_target", columnDefinition = "TEXT")
    private String sentenceTarget;
    
    @Column(name = "word_structure", columnDefinition = "TEXT")
    private String wordStructure;

    @Column(name = "audio_file", columnDefinition = "TEXT")
    private String audioFile;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public SentenceEntity(String word, String sourceLanguage, String targetLanguage,
                         String wordRomanized, String sentenceSource, String sentenceTransliteration,
                         String sentenceTarget, String wordStructure, String audioFile) {
        this.word = word;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.wordRomanized = wordRomanized;
        this.sentenceSource = sentenceSource;
        this.sentenceTransliteration = sentenceTransliteration;
        this.sentenceTarget = sentenceTarget;
        this.wordStructure = wordStructure;
        this.audioFile = audioFile;
    }
}
