package com.raidrin.eme.storage.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // Legacy fields - kept for backward compatibility during migration
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

    @Column(name = "character_guide_id")
    private Long characterGuideId;

    @Column(name = "image_status", length = 20)
    private String imageStatus; // PENDING, GENERATING, COMPLETED, FAILED

    @Column(name = "image_prompt_status", length = 20)
    private String imagePromptStatus; // NONE, GENERATED, APPROVED, REJECTED

    @Column(name = "audio_status", length = 20)
    private String audioStatus; // PENDING, GENERATING, COMPLETED, FAILED

    @Column(name = "translation_override_at")
    private LocalDateTime translationOverrideAt; // Set when user manually overrides translation

    @Column(name = "mnemonic_keyword_updated_at")
    private LocalDateTime mnemonicKeywordUpdatedAt; // Set when user manually updates mnemonic keyword

    @Column(name = "transliteration_override_at")
    private LocalDateTime transliterationOverrideAt; // Set when user manually overrides transliteration

    @Column(name = "image_style", length = 50)
    private String imageStyle; // REALISTIC_CINEMATIC, ANIMATED_2D_CINEMATIC, ANIMATED_3D_CINEMATIC

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // New variant relationships
    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<WordTranslationEntity> translations = new ArrayList<>();

    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<WordMnemonicEntity> mnemonics = new ArrayList<>();

    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<WordImageEntity> images = new ArrayList<>();

    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<WordSentenceEntity> sentences = new ArrayList<>();

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

    // Helper methods to get current variants
    public WordTranslationEntity getCurrentTranslation() {
        return translations.stream()
            .filter(WordTranslationEntity::getIsCurrent)
            .findFirst()
            .orElse(null);
    }

    public WordMnemonicEntity getCurrentMnemonic() {
        return mnemonics.stream()
            .filter(WordMnemonicEntity::getIsCurrent)
            .findFirst()
            .orElse(null);
    }

    public WordImageEntity getCurrentImage() {
        return images.stream()
            .filter(WordImageEntity::getIsCurrent)
            .findFirst()
            .orElse(null);
    }

    public WordSentenceEntity getCurrentSentence() {
        return sentences.stream()
            .filter(WordSentenceEntity::getIsCurrent)
            .findFirst()
            .orElse(null);
    }
}
