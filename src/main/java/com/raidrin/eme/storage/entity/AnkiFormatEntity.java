package com.raidrin.eme.storage.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raidrin.eme.anki.AnkiFormat;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "anki_formats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnkiFormatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "format_json", nullable = false, columnDefinition = "TEXT")
    private String formatJson;

    @Transient
    private AnkiFormat format;

    @Transient
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        serializeFormat();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        serializeFormat();
    }

    @PostLoad
    protected void onLoad() {
        deserializeFormat();
    }

    private void serializeFormat() {
        if (format != null) {
            try {
                formatJson = objectMapper.writeValueAsString(format);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize AnkiFormat", e);
            }
        }
    }

    private void deserializeFormat() {
        if (formatJson != null && !formatJson.isEmpty()) {
            try {
                format = objectMapper.readValue(formatJson, AnkiFormat.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize AnkiFormat", e);
            }
        }
    }

    public AnkiFormatEntity(String name, AnkiFormat format) {
        this.name = name;
        this.format = format;
    }
}
