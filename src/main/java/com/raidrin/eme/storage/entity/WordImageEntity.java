package com.raidrin.eme.storage.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "word_images")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WordImageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    @JsonIgnore
    private WordEntity word;

    @Column(name = "image_file", columnDefinition = "TEXT")
    private String imageFile;

    @Column(name = "image_gcs_url", columnDefinition = "TEXT")
    private String imageGcsUrl;

    @Column(name = "image_prompt", columnDefinition = "TEXT")
    private String imagePrompt;

    @Column(name = "image_style", length = 50)
    private String imageStyle;  // 'REALISTIC_CINEMATIC', 'CARTOON', etc.

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public WordImageEntity(WordEntity word, String imageFile, String imageGcsUrl,
                            String imagePrompt, String imageStyle, boolean isCurrent) {
        this.word = word;
        this.imageFile = imageFile;
        this.imageGcsUrl = imageGcsUrl;
        this.imagePrompt = imagePrompt;
        this.imageStyle = imageStyle;
        this.isCurrent = isCurrent;
    }
}
