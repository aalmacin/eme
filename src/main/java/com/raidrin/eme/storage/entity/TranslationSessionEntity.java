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

    @Column(name = "session_name", length = 200)
    private String sessionName;

    @Column(name = "source_language", nullable = false, length = 10)
    private String sourceLanguage;

    @Column(name = "target_language", nullable = false, length = 10)
    private String targetLanguage;

    @Column(name = "anki_enabled", nullable = false)
    private Boolean ankiEnabled = false;

    @Column(name = "anki_deck", columnDefinition = "TEXT")
    private String ankiDeck;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anki_format_id")
    private AnkiFormatEntity ankiFormat;

    @Column(name = "session_data", columnDefinition = "TEXT")
    private String sessionData; // JSON with mnemonic data, file paths, errors, etc.

    @Column(name = "zip_file_path", columnDefinition = "TEXT")
    private String zipFilePath;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        // Default session name to first word if not provided
        if (sessionName == null || sessionName.trim().isEmpty()) {
            sessionName = word;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public TranslationSessionEntity(String word, String sourceLanguage, String targetLanguage,
                                    Boolean ankiEnabled, String ankiDeck, AnkiFormatEntity ankiFormat) {
        this.word = word;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.ankiEnabled = ankiEnabled;
        this.ankiDeck = ankiDeck;
        this.ankiFormat = ankiFormat;
    }

    public TranslationSessionEntity(String word, String sessionName, String sourceLanguage, String targetLanguage,
                                    Boolean ankiEnabled, String ankiDeck, AnkiFormatEntity ankiFormat) {
        this.word = word;
        this.sessionName = sessionName;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.ankiEnabled = ankiEnabled;
        this.ankiDeck = ankiDeck;
        this.ankiFormat = ankiFormat;
    }
}
