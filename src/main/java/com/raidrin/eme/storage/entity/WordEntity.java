package com.raidrin.eme.storage.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "words", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"word", "source_language", "target_language"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "word", nullable = false, columnDefinition = "TEXT")
    private String word;

    @Column(name = "source_language", nullable = false, length = 10)
    private String sourceLanguage;

    @Column(name = "target_language", nullable = false, length = 10)
    private String targetLanguage;

    @Column(name = "translation", columnDefinition = "TEXT")
    private String translation; // JSON string representation of translations

    @Column(name = "audio_source_file", columnDefinition = "TEXT")
    private String audioSourceFile;

    @Column(name = "audio_target_file", columnDefinition = "TEXT")
    private String audioTargetFile;

    @Column(name = "image_file", columnDefinition = "TEXT")
    private String imageFile;

    @Column(name = "image_prompt", columnDefinition = "TEXT")
    private String imagePrompt;

    @Column(name = "mnemonic_keyword", columnDefinition = "TEXT")
    private String mnemonicKeyword;

    @Column(name = "mnemonic_sentence", columnDefinition = "TEXT")
    private String mnemonicSentence;

    @Column(name = "source_transliteration", columnDefinition = "TEXT")
    private String sourceTransliteration;

    @Enumerated(EnumType.STRING)
    @Column(name = "translation_status", length = 20)
    private ProcessingStatus translationStatus = ProcessingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "audio_generation_status", length = 20)
    private ProcessingStatus audioGenerationStatus = ProcessingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_generation_status", length = 20)
    private ProcessingStatus imageGenerationStatus = ProcessingStatus.PENDING;

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

    public WordEntity(String word, String sourceLanguage, String targetLanguage) {
        this.word = word;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
    }
}
