package com.raidrin.eme.storage.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "word_sentences")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WordSentenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    @JsonIgnore
    private WordEntity word;

    @Column(name = "sentence_source", columnDefinition = "TEXT")
    private String sentenceSource;

    @Column(name = "sentence_transliteration", columnDefinition = "TEXT")
    private String sentenceTransliteration;

    @Column(name = "sentence_target", columnDefinition = "TEXT")
    private String sentenceTarget;

    @Column(name = "word_structure", columnDefinition = "TEXT")
    private String wordStructure;

    @Column(name = "word_romanized", columnDefinition = "TEXT")
    private String wordRomanized;

    @Column(name = "audio_file", columnDefinition = "TEXT")
    private String audioFile;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public WordSentenceEntity(WordEntity word, String sentenceSource, String sentenceTransliteration,
                               String sentenceTarget, String wordStructure, String wordRomanized,
                               String audioFile, boolean isCurrent) {
        this.word = word;
        this.sentenceSource = sentenceSource;
        this.sentenceTransliteration = sentenceTransliteration;
        this.sentenceTarget = sentenceTarget;
        this.wordStructure = wordStructure;
        this.wordRomanized = wordRomanized;
        this.audioFile = audioFile;
        this.isCurrent = isCurrent;
    }
}
