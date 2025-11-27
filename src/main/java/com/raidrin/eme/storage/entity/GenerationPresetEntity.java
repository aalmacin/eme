package com.raidrin.eme.storage.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for storing generation presets/configurations
 */
@Entity
@Table(name = "generation_presets")
@Data
@NoArgsConstructor
public class GenerationPresetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String presetName;

    @Column(length = 500)
    private String description;

    // Language settings
    @Column(nullable = false, length = 10)
    private String sourceLanguage;

    @Column(nullable = false, length = 10)
    private String targetLanguage;

    @Column(length = 10)
    private String sourceLanguageCode;

    @Column(length = 10)
    private String targetLanguageCode;

    // Audio settings
    @Column(nullable = false)
    private Boolean enableSourceAudio = false;

    @Column(nullable = false)
    private Boolean enableTargetAudio = false;

    @Column(length = 50)
    private String sourceAudioLanguageCode;

    @Column(length = 50)
    private String targetAudioLanguageCode;

    @Column(length = 50)
    private String sourceVoiceGender;

    @Column(length = 50)
    private String targetVoiceGender;

    @Column(length = 100)
    private String sourceVoiceName;

    @Column(length = 100)
    private String targetVoiceName;

    // Feature flags
    @Column(nullable = false)
    private Boolean enableTranslation = true;

    @Column(nullable = false)
    private Boolean enableSentenceGeneration = false;

    @Column(nullable = false)
    private Boolean enableImageGeneration = false;

    // Anki settings
    @Column(nullable = false)
    private Boolean ankiEnabled = false;

    @Column(length = 100)
    private String ankiDeck;

    @Column(columnDefinition = "TEXT")
    private String ankiFrontTemplate;

    @Column(columnDefinition = "TEXT")
    private String ankiBackTemplate;

    // Metadata
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Boolean isDefault = false;

    @Column(nullable = false)
    private Integer usageCount = 0;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public GenerationPresetEntity(String presetName) {
        this.presetName = presetName;
    }

    /**
     * Increment usage count when preset is used
     */
    public void incrementUsageCount() {
        this.usageCount++;
        this.updatedAt = LocalDateTime.now();
    }
}
