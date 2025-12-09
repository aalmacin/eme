package com.raidrin.eme.storage.service;

import com.raidrin.eme.storage.entity.GenerationPresetEntity;
import com.raidrin.eme.storage.repository.GenerationPresetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GenerationPresetService {

    private final GenerationPresetRepository presetRepository;

    public List<GenerationPresetEntity> findAll() {
        return presetRepository.findAllOrderByUsageCountAndUpdatedAt();
    }

    public Page<GenerationPresetEntity> findAll(Pageable pageable) {
        return presetRepository.findAll(pageable);
    }

    public List<GenerationPresetEntity> findAllByCreatedDate() {
        return presetRepository.findAllOrderByCreatedAtDesc();
    }

    public Optional<GenerationPresetEntity> findById(Long id) {
        return presetRepository.findById(id);
    }

    public Optional<GenerationPresetEntity> findByName(String presetName) {
        return presetRepository.findByPresetName(presetName);
    }

    public List<GenerationPresetEntity> findDefaultPresets() {
        return presetRepository.findByIsDefaultTrue();
    }

    @Transactional
    public GenerationPresetEntity save(GenerationPresetEntity preset) {
        if (preset.getPresetName() == null || preset.getPresetName().trim().isEmpty()) {
            throw new IllegalArgumentException("Preset name is required");
        }

        // Check for duplicate name (excluding current preset if updating)
        Optional<GenerationPresetEntity> existing = presetRepository.findByPresetName(preset.getPresetName());
        if (existing.isPresent() && !existing.get().getId().equals(preset.getId())) {
            throw new IllegalArgumentException("A preset with this name already exists");
        }

        return presetRepository.save(preset);
    }

    @Transactional
    public void delete(Long id) {
        presetRepository.deleteById(id);
    }

    @Transactional
    public void incrementUsageCount(Long id) {
        Optional<GenerationPresetEntity> presetOpt = presetRepository.findById(id);
        if (presetOpt.isPresent()) {
            GenerationPresetEntity preset = presetOpt.get();
            preset.incrementUsageCount();
            presetRepository.save(preset);
        }
    }

    @Transactional
    public void setAsDefault(Long id) {
        // Clear existing defaults
        List<GenerationPresetEntity> defaults = presetRepository.findByIsDefaultTrue();
        defaults.forEach(p -> {
            p.setIsDefault(false);
            presetRepository.save(p);
        });

        // Set new default
        Optional<GenerationPresetEntity> presetOpt = presetRepository.findById(id);
        if (presetOpt.isPresent()) {
            GenerationPresetEntity preset = presetOpt.get();
            preset.setIsDefault(true);
            presetRepository.save(preset);
        }
    }

    public boolean existsByName(String presetName) {
        return presetRepository.existsByPresetName(presetName);
    }

    /**
     * Create a preset from a session's configuration
     */
    @Transactional
    public GenerationPresetEntity createFromSessionData(String presetName, java.util.Map<String, Object> sessionData) {
        GenerationPresetEntity preset = new GenerationPresetEntity(presetName);

        // Extract configuration from session data
        if (sessionData.containsKey("original_request")) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> originalRequest = (java.util.Map<String, Object>) sessionData.get("original_request");

            preset.setSourceLanguage((String) originalRequest.get("source_language"));
            preset.setTargetLanguage((String) originalRequest.get("target_language"));
            preset.setSourceLanguageCode((String) originalRequest.get("source_language_code"));
            preset.setTargetLanguageCode((String) originalRequest.get("target_language_code"));

            preset.setEnableSourceAudio((Boolean) originalRequest.getOrDefault("enable_source_audio", false));
            preset.setEnableTargetAudio((Boolean) originalRequest.getOrDefault("enable_target_audio", false));
            preset.setSourceAudioLanguageCode((String) originalRequest.get("source_audio_language_code"));
            preset.setTargetAudioLanguageCode((String) originalRequest.get("target_audio_language_code"));
            preset.setSourceVoiceGender((String) originalRequest.get("source_voice_gender"));
            preset.setTargetVoiceGender((String) originalRequest.get("target_voice_gender"));
            preset.setSourceVoiceName((String) originalRequest.get("source_voice_name"));
            preset.setTargetVoiceName((String) originalRequest.get("target_voice_name"));

            preset.setEnableTranslation((Boolean) originalRequest.getOrDefault("enable_translation", true));
            preset.setEnableSentenceGeneration((Boolean) originalRequest.getOrDefault("enable_sentence_generation", false));
            preset.setEnableImageGeneration((Boolean) originalRequest.getOrDefault("enable_image_generation", false));
        }

        return save(preset);
    }
}
