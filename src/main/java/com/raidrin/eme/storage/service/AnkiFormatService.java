package com.raidrin.eme.storage.service;

import com.raidrin.eme.anki.AnkiFormat;
import com.raidrin.eme.storage.entity.AnkiFormatEntity;
import com.raidrin.eme.storage.repository.AnkiFormatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnkiFormatService {

    private final AnkiFormatRepository ankiFormatRepository;

    @Transactional
    public AnkiFormatEntity createFormat(String name, String description, AnkiFormat format) {
        validateFormatName(name);

        AnkiFormatEntity entity = new AnkiFormatEntity();
        entity.setName(name);
        entity.setDescription(description);
        entity.setFormat(format);
        entity.setIsDefault(false);

        return ankiFormatRepository.save(entity);
    }

    @Transactional
    public AnkiFormatEntity updateFormat(Long id, String name, String description, AnkiFormat format) {
        AnkiFormatEntity entity = ankiFormatRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Anki format not found: " + id));

        // If name is changing, validate it's not a duplicate
        if (!entity.getName().equals(name)) {
            validateFormatName(name);
        }

        entity.setName(name);
        entity.setDescription(description);
        entity.setFormat(format);

        return ankiFormatRepository.save(entity);
    }

    @Transactional
    public void deleteFormat(Long id) {
        AnkiFormatEntity entity = ankiFormatRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Anki format not found: " + id));

        ankiFormatRepository.delete(entity);
    }

    @Transactional
    public void setDefaultFormat(Long id) {
        // Clear all existing defaults
        List<AnkiFormatEntity> allFormats = ankiFormatRepository.findAll();
        for (AnkiFormatEntity format : allFormats) {
            if (format.getIsDefault()) {
                format.setIsDefault(false);
                ankiFormatRepository.save(format);
            }
        }

        // Set the new default
        AnkiFormatEntity entity = ankiFormatRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Anki format not found: " + id));
        entity.setIsDefault(true);
        ankiFormatRepository.save(entity);
    }

    public Optional<AnkiFormatEntity> findById(Long id) {
        return ankiFormatRepository.findById(id);
    }

    public Optional<AnkiFormatEntity> findByName(String name) {
        return ankiFormatRepository.findByName(name);
    }

    public List<AnkiFormatEntity> findAll() {
        return ankiFormatRepository.findAllOrderedByDefaultAndName();
    }

    public Page<AnkiFormatEntity> findAll(Pageable pageable) {
        return ankiFormatRepository.findAll(pageable);
    }

    public Optional<AnkiFormatEntity> findDefault() {
        return ankiFormatRepository.findByIsDefaultTrue();
    }

    private void validateFormatName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Format name must be provided");
        }

        Optional<AnkiFormatEntity> existing = ankiFormatRepository.findByName(name);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Format with name '" + name + "' already exists");
        }
    }
}
