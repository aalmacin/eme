package com.raidrin.eme.controller;

import com.raidrin.eme.storage.entity.WordEntity;
import com.raidrin.eme.storage.service.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Controller for checking word processing statuses
 */
@RestController
@RequestMapping("/api/words")
@RequiredArgsConstructor
public class WordStatusController {

    private final WordService wordService;

    /**
     * Get status for multiple words by their IDs
     *
     * @param wordIds Comma-separated list of word IDs
     * @return Map of word IDs to their processing statuses
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getWordStatuses(@RequestParam String wordIds) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> wordStatuses = new ArrayList<>();

        String[] ids = wordIds.split(",");
        for (String idStr : ids) {
            try {
                Long wordId = Long.parseLong(idStr.trim());
                Optional<WordEntity> wordOpt = wordService.getAllWords().stream()
                    .filter(w -> w.getId().equals(wordId))
                    .findFirst();

                if (wordOpt.isPresent()) {
                    WordEntity word = wordOpt.get();
                    Map<String, Object> wordStatus = new HashMap<>();
                    wordStatus.put("wordId", word.getId());
                    wordStatus.put("word", word.getWord());
                    wordStatus.put("translationStatus", word.getTranslationStatus().toString());
                    wordStatus.put("audioGenerationStatus", word.getAudioGenerationStatus().toString());
                    wordStatus.put("imageGenerationStatus", word.getImageGenerationStatus().toString());
                    wordStatus.put("hasTranslation", word.getTranslation() != null);
                    wordStatus.put("hasAudio", word.getAudioSourceFile() != null || word.getAudioTargetFile() != null);
                    wordStatus.put("hasImage", word.getImageFile() != null);
                    wordStatuses.add(wordStatus);
                }
            } catch (NumberFormatException e) {
                // Skip invalid IDs
            }
        }

        response.put("success", true);
        response.put("statuses", wordStatuses);
        return ResponseEntity.ok(response);
    }

    /**
     * Get detailed status for a single word
     *
     * @param wordId Word ID
     * @return Detailed word status information
     */
    @GetMapping("/{wordId}/status")
    public ResponseEntity<Map<String, Object>> getWordStatus(@PathVariable Long wordId) {
        Optional<WordEntity> wordOpt = wordService.getAllWords().stream()
            .filter(w -> w.getId().equals(wordId))
            .findFirst();

        if (wordOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WordEntity word = wordOpt.get();
        Map<String, Object> response = new HashMap<>();

        response.put("success", true);
        response.put("wordId", word.getId());
        response.put("word", word.getWord());
        response.put("sourceLanguage", word.getSourceLanguage());
        response.put("targetLanguage", word.getTargetLanguage());

        // Processing statuses
        response.put("translationStatus", word.getTranslationStatus().toString());
        response.put("audioGenerationStatus", word.getAudioGenerationStatus().toString());
        response.put("imageGenerationStatus", word.getImageGenerationStatus().toString());

        // Data availability
        response.put("hasTranslation", word.getTranslation() != null);
        response.put("hasAudio", word.getAudioSourceFile() != null || word.getAudioTargetFile() != null);
        response.put("hasImage", word.getImageFile() != null);

        // Files
        if (word.getTranslation() != null) {
            response.put("translation", word.getTranslation());
        }
        if (word.getAudioSourceFile() != null) {
            response.put("audioSourceFile", word.getAudioSourceFile());
        }
        if (word.getAudioTargetFile() != null) {
            response.put("audioTargetFile", word.getAudioTargetFile());
        }
        if (word.getImageFile() != null) {
            response.put("imageFile", word.getImageFile());
        }
        if (word.getImagePrompt() != null) {
            response.put("imagePrompt", word.getImagePrompt());
        }

        // Overall completion status
        boolean allCompleted = word.getTranslationStatus().toString().equals("COMPLETED")
            && word.getAudioGenerationStatus().toString().equals("COMPLETED")
            && word.getImageGenerationStatus().toString().equals("COMPLETED");
        boolean anyFailed = word.getTranslationStatus().toString().equals("FAILED")
            || word.getAudioGenerationStatus().toString().equals("FAILED")
            || word.getImageGenerationStatus().toString().equals("FAILED");
        boolean anyProcessing = word.getTranslationStatus().toString().equals("PROCESSING")
            || word.getAudioGenerationStatus().toString().equals("PROCESSING")
            || word.getImageGenerationStatus().toString().equals("PROCESSING");

        if (allCompleted) {
            response.put("overallStatus", "COMPLETED");
        } else if (anyFailed) {
            response.put("overallStatus", "FAILED");
        } else if (anyProcessing) {
            response.put("overallStatus", "PROCESSING");
        } else {
            response.put("overallStatus", "PENDING");
        }

        return ResponseEntity.ok(response);
    }
}
