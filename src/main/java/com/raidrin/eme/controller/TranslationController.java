package com.raidrin.eme.controller;

import com.raidrin.eme.storage.entity.TranslationEntity;
import com.raidrin.eme.storage.service.TranslationStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Controller
@RequestMapping("/translations")
@RequiredArgsConstructor
public class TranslationController {

    private final TranslationStorageService translationStorageService;

    @GetMapping
    public String listTranslations(Model model) {
        List<String> allWords = translationStorageService.getAllWordTranslations();
        model.addAttribute("words", allWords);
        return "translations/list";
    }

    @GetMapping("/{word}/{sourceLang}/{targetLang}")
    public String viewTranslation(@PathVariable String word, 
                                 @PathVariable String sourceLang, 
                                 @PathVariable String targetLang, 
                                 Model model) {
        Optional<Set<String>> translations = translationStorageService.findTranslations(word, sourceLang, targetLang);
        if (translations.isPresent()) {
            model.addAttribute("word", word);
            model.addAttribute("sourceLanguage", sourceLang);
            model.addAttribute("targetLanguage", targetLang);
            model.addAttribute("translations", translations.get());
            return "translations/view";
        } else {
            return "redirect:/translations?error=not-found";
        }
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("translation", new TranslationForm());
        return "translations/create";
    }

    @PostMapping("/create")
    public String createTranslation(@ModelAttribute TranslationForm form, RedirectAttributes redirectAttributes) {
        try {
            translationStorageService.saveTranslations(form.getWord(), form.getSourceLanguage(), 
                                                     form.getTargetLanguage(), form.getTranslationsSet());
            redirectAttributes.addFlashAttribute("success", "Translation created successfully!");
            return "redirect:/translations";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create translation: " + e.getMessage());
            return "redirect:/translations/create";
        }
    }

    @GetMapping("/{word}/{sourceLang}/{targetLang}/edit")
    public String editForm(@PathVariable String word, 
                          @PathVariable String sourceLang, 
                          @PathVariable String targetLang, 
                          Model model) {
        Optional<Set<String>> translations = translationStorageService.findTranslations(word, sourceLang, targetLang);
        if (translations.isPresent()) {
            TranslationForm form = new TranslationForm();
            form.setWord(word);
            form.setSourceLanguage(sourceLang);
            form.setTargetLanguage(targetLang);
            form.setTranslations(translations.get().stream().reduce("", (a, b) -> a + (a.isEmpty() ? "" : "\n") + b));
            model.addAttribute("translation", form);
            return "translations/edit";
        } else {
            return "redirect:/translations?error=not-found";
        }
    }

    @PostMapping("/{word}/{sourceLang}/{targetLang}/edit")
    public String updateTranslation(@PathVariable String word, 
                                   @PathVariable String sourceLang, 
                                   @PathVariable String targetLang, 
                                   @ModelAttribute TranslationForm form, 
                                   RedirectAttributes redirectAttributes) {
        try {
            translationStorageService.saveTranslations(form.getWord(), form.getSourceLanguage(), 
                                                     form.getTargetLanguage(), form.getTranslationsSet());
            redirectAttributes.addFlashAttribute("success", "Translation updated successfully!");
            return "redirect:/translations";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update translation: " + e.getMessage());
            return "redirect:/translations/" + word + "/" + sourceLang + "/" + targetLang + "/edit";
        }
    }

    @PostMapping("/{word}/{sourceLang}/{targetLang}/delete")
    public String deleteTranslation(@PathVariable String word, 
                                   @PathVariable String sourceLang, 
                                   @PathVariable String targetLang, 
                                   RedirectAttributes redirectAttributes) {
        try {
            translationStorageService.deleteTranslations(word, sourceLang, targetLang);
            redirectAttributes.addFlashAttribute("success", "Translation deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete translation: " + e.getMessage());
        }
        return "redirect:/translations";
    }

    // Form class for handling translation data
    public static class TranslationForm {
        private String word;
        private String sourceLanguage;
        private String targetLanguage;
        private String translations; // Newline-separated translations

        // Getters and setters
        public String getWord() { return word; }
        public void setWord(String word) { this.word = word; }
        
        public String getSourceLanguage() { return sourceLanguage; }
        public void setSourceLanguage(String sourceLanguage) { this.sourceLanguage = sourceLanguage; }
        
        public String getTargetLanguage() { return targetLanguage; }
        public void setTargetLanguage(String targetLanguage) { this.targetLanguage = targetLanguage; }
        
        public String getTranslations() { return translations; }
        public void setTranslations(String translations) { this.translations = translations; }
        
        public Set<String> getTranslationsSet() {
            if (translations == null || translations.trim().isEmpty()) {
                return Set.of();
            }
            return Set.of(translations.split("\n"));
        }
    }
}
