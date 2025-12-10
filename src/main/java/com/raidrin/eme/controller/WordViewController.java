package com.raidrin.eme.controller;

import com.raidrin.eme.storage.entity.WordEntity;
import com.raidrin.eme.storage.service.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Controller for word web pages (as opposed to REST API endpoints)
 */
@Controller
@RequestMapping("/words")
@RequiredArgsConstructor
public class WordViewController {

    private final WordService wordService;

    /**
     * View word details page
     */
    @GetMapping("/{id}")
    public String viewWord(@PathVariable Long id, Model model) {
        Optional<WordEntity> wordOpt = wordService.getAllWords().stream()
                .filter(w -> w.getId().equals(id))
                .findFirst();

        if (wordOpt.isEmpty()) {
            return "redirect:/sessions?error=word-not-found";
        }

        WordEntity word = wordOpt.get();
        model.addAttribute("word", word);

        // Parse translations if available
        if (word.getTranslation() != null && !word.getTranslation().isEmpty()) {
            try {
                Set<String> translations = wordService.deserializeTranslations(word.getTranslation());
                model.addAttribute("translations", translations);
            } catch (Exception e) {
                // Ignore parse errors
            }
        }

        return "words/detail";
    }

    /**
     * List all words with pagination
     */
    @GetMapping
    public String listWords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        Page<WordEntity> wordPage = wordService.getAllWords(pageable);
        model.addAttribute("words", wordPage.getContent());
        model.addAttribute("page", wordPage);
        return "words/list";
    }
}
