package com.raidrin.eme.storage.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "character_guide", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"language", "start_sound"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CharacterGuideEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Column(name = "start_sound", nullable = false, length = 50)
    private String startSound;

    @Column(name = "character_name", nullable = false)
    private String characterName;

    @Column(name = "character_context", nullable = false)
    private String characterContext;

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

    public CharacterGuideEntity(String language, String startSound, String characterName, String characterContext) {
        this.language = language;
        this.startSound = startSound;
        this.characterName = characterName;
        this.characterContext = characterContext;
    }
}
