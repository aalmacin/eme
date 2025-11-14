package com.raidrin.eme.controller;

import com.raidrin.eme.storage.entity.GenerationPresetEntity;
import com.raidrin.eme.storage.service.GenerationPresetService;
import com.raidrin.eme.storage.service.TranslationSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/presets")
@RequiredArgsConstructor
public class GenerationPresetController {

    private final GenerationPresetService presetService;
    private final TranslationSessionService sessionService;

    /**
     * List all presets
     */
    @GetMapping
    public String listPresets(Model model) {
        List<GenerationPresetEntity> presets = presetService.findAll();
        model.addAttribute("presets", presets);
        return "presets/list";
    }

    /**
     * Get preset as JSON (for API usage)
     */
    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<GenerationPresetEntity> getPreset(@PathVariable Long id) {
        return presetService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all presets as JSON
     */
    @GetMapping("/api/all")
    @ResponseBody
    public ResponseEntity<List<GenerationPresetEntity>> getAllPresets() {
        return ResponseEntity.ok(presetService.findAll());
    }

    /**
     * Save a new preset
     */
    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> savePreset(@RequestBody GenerationPresetEntity preset) {
        Map<String, Object> response = new HashMap<>();
        try {
            System.out.println("Received preset: " + preset.getPresetName());
            System.out.println("enableImageGeneration: " + preset.getEnableImageGeneration());
            System.out.println("enableSentenceGeneration: " + preset.getEnableSentenceGeneration());
            System.out.println("enableTranslation: " + preset.getEnableTranslation());

            GenerationPresetEntity saved = presetService.save(preset);

            System.out.println("Saved preset ID: " + saved.getId());
            System.out.println("Saved enableImageGeneration: " + saved.getEnableImageGeneration());

            response.put("success", true);
            response.put("preset", saved);
            response.put("message", "Preset saved successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Save preset from session configuration
     */
    @PostMapping("/from-session/{sessionId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> savePresetFromSession(
            @PathVariable Long sessionId,
            @RequestParam String presetName,
            @RequestParam(required = false) String description) {

        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> sessionData = sessionService.getSessionData(sessionId);

            if (!sessionData.containsKey("original_request")) {
                response.put("success", false);
                response.put("error", "Session does not contain configuration data");
                return ResponseEntity.badRequest().body(response);
            }

            GenerationPresetEntity preset = presetService.createFromSessionData(presetName, sessionData);
            if (description != null && !description.trim().isEmpty()) {
                preset.setDescription(description);
                preset = presetService.save(preset);
            }

            response.put("success", true);
            response.put("preset", preset);
            response.put("message", "Preset created from session successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Delete a preset
     */
    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deletePreset(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            presetService.delete(id);
            response.put("success", true);
            response.put("message", "Preset deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Set a preset as default
     */
    @PostMapping("/{id}/set-default")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setAsDefault(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            presetService.setAsDefault(id);
            response.put("success", true);
            response.put("message", "Preset set as default successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Increment usage count (called when preset is used)
     */
    @PostMapping("/{id}/use")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> usePreset(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            presetService.incrementUsageCount(id);
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
