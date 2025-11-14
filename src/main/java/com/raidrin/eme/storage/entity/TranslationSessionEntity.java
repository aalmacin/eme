package com.raidrin.eme.storage.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "translation_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranslationSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "word", nullable = false, columnDefinition = "TEXT")
    private String word;

    @Column(name = "source_language", nullable = false, length = 10)
    private String sourceLanguage;

    @Column(name = "target_language", nullable = false, length = 10)
    private String targetLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status = SessionStatus.PENDING;

    @Column(name = "image_generation_enabled", nullable = false)
    private Boolean imageGenerationEnabled = false;

    @Column(name = "audio_generation_enabled", nullable = false)
    private Boolean audioGenerationEnabled = false;

    @Column(name = "anki_enabled", nullable = false)
    private Boolean ankiEnabled = false;

    @Column(name = "anki_deck", columnDefinition = "TEXT")
    private String ankiDeck;

    @Column(name = "anki_front_template", columnDefinition = "TEXT")
    private String ankiFrontTemplate;

    @Column(name = "anki_back_template", columnDefinition = "TEXT")
    private String ankiBackTemplate;

    @Column(name = "sentence_generation_enabled", nullable = false)
    private Boolean sentenceGenerationEnabled = false;

    @Column(name = "session_data", columnDefinition = "TEXT")
    private String sessionData; // JSON with mnemonic data, file paths, errors, etc.

    @Column(name = "zip_file_path", columnDefinition = "TEXT")
    private String zipFilePath;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = SessionStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public TranslationSessionEntity(String word, String sourceLanguage, String targetLanguage,
                                    Boolean imageGenerationEnabled, Boolean audioGenerationEnabled,
                                    Boolean sentenceGenerationEnabled, Boolean ankiEnabled,
                                    String ankiDeck, String ankiFrontTemplate, String ankiBackTemplate) {
        this.word = word;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.imageGenerationEnabled = imageGenerationEnabled;
        this.audioGenerationEnabled = audioGenerationEnabled;
        this.sentenceGenerationEnabled = sentenceGenerationEnabled;
        this.ankiEnabled = ankiEnabled;
        this.ankiDeck = ankiDeck;
        this.ankiFrontTemplate = ankiFrontTemplate;
        this.ankiBackTemplate = ankiBackTemplate;
        this.status = SessionStatus.PENDING;
    }

    public enum SessionStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
