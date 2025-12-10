package com.raidrin.eme.storage.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "word_translations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WordTranslationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    @JsonIgnore
    private WordEntity word;

    @Column(name = "translation", nullable = false, columnDefinition = "TEXT")
    private String translation;

    @Column(name = "transliteration", columnDefinition = "TEXT")
    private String transliteration;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    @Column(name = "is_user_created", nullable = false)
    private Boolean isUserCreated = false;

    @Column(name = "source", length = 50)
    private String source;  // 'openai', 'google', 'user', 'legacy'

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public WordTranslationEntity(WordEntity word, String translation, String transliteration,
                                  String source, boolean isCurrent, boolean isUserCreated) {
        this.word = word;
        this.translation = translation;
        this.transliteration = transliteration;
        this.source = source;
        this.isCurrent = isCurrent;
        this.isUserCreated = isUserCreated;
    }
}
