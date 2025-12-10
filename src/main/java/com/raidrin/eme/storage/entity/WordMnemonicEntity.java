package com.raidrin.eme.storage.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "word_mnemonics")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WordMnemonicEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    @JsonIgnore
    private WordEntity word;

    @Column(name = "mnemonic_keyword", columnDefinition = "TEXT")
    private String mnemonicKeyword;

    @Column(name = "mnemonic_sentence", columnDefinition = "TEXT")
    private String mnemonicSentence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_guide_id")
    @JsonIgnore
    private CharacterGuideEntity characterGuide;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    @Column(name = "is_user_created", nullable = false)
    private Boolean isUserCreated = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public WordMnemonicEntity(WordEntity word, String mnemonicKeyword, String mnemonicSentence,
                               CharacterGuideEntity characterGuide, boolean isCurrent, boolean isUserCreated) {
        this.word = word;
        this.mnemonicKeyword = mnemonicKeyword;
        this.mnemonicSentence = mnemonicSentence;
        this.characterGuide = characterGuide;
        this.isCurrent = isCurrent;
        this.isUserCreated = isUserCreated;
    }
}
