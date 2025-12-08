package com.raidrin.eme.storage.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * @deprecated This entity is deprecated. Use {@link WordTranslationEntity} instead.
 * <p>
 * The new WordTranslationEntity provides:
 * - Proper foreign key relationship to WordEntity
 * - Support for multiple translation variants per word
 * - is_current flag to mark active translation
 * - is_user_created flag to track manual overrides
 * <p>
 * This entity is kept for backward compatibility and will be removed in a future release.
 */
@Deprecated(since = "2.0", forRemoval = true)
@Entity
@Table(name = "translations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranslationEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "word", nullable = false, columnDefinition = "TEXT")
    private String word;
    
    @Column(name = "source_language", nullable = false, length = 10)
    private String sourceLanguage;
    
    @Column(name = "target_language", nullable = false, length = 10)
    private String targetLanguage;
    
    @Column(name = "translations", nullable = false, columnDefinition = "TEXT")
    private String translations; // JSON string representation of Set<String>
    
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
    
    public TranslationEntity(String word, String sourceLanguage, String targetLanguage, String translations) {
        this.word = word;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.translations = translations;
    }
}
