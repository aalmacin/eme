package com.raidrin.eme.controller;

import com.raidrin.eme.sentence.SentenceData;
import com.raidrin.eme.storage.service.SentenceStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/sentences")
@RequiredArgsConstructor
public class SentenceController {

    private final SentenceStorageService sentenceStorageService;

    @GetMapping
    public String listSentences(Model model) {
        List<SentenceData> allSentences = sentenceStorageService.getAllSentences();
        model.addAttribute("sentences", allSentences);
        return "sentences/list";
    }

    @GetMapping("/{word}/{sourceLang}/{targetLang}")
    public String viewSentence(@PathVariable String word, 
                              @PathVariable String sourceLang, 
                              @PathVariable String targetLang, 
                              Model model) {
        Optional<SentenceData> sentence = sentenceStorageService.findSentence(word, sourceLang, targetLang);
        if (sentence.isPresent()) {
            model.addAttribute("word", word);
            model.addAttribute("sourceLanguage", sourceLang);
            model.addAttribute("targetLanguage", targetLang);
            model.addAttribute("sentence", sentence.get());
            return "sentences/view";
        } else {
            return "redirect:/sentences?error=not-found";
        }
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("sentence", new SentenceForm());
        return "sentences/create";
    }

    @PostMapping("/create")
    public String createSentence(@ModelAttribute SentenceForm form, RedirectAttributes redirectAttributes) {
        try {
            SentenceData sentenceData = new SentenceData();
            sentenceData.setTargetLanguageLatinCharacters(form.getWordRomanized());
            sentenceData.setTargetLanguageSentence(form.getSentenceTarget());
            sentenceData.setTargetLanguageTransliteration(form.getSentenceTransliteration());
            sentenceData.setSourceLanguageSentence(form.getSentenceSource());
            sentenceData.setSourceLanguageStructure(form.getWordStructure());
            
            sentenceStorageService.saveSentence(form.getWord(), form.getSourceLanguage(), 
                                              form.getTargetLanguage(), sentenceData);
            redirectAttributes.addFlashAttribute("success", "Sentence created successfully!");
            return "redirect:/sentences";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create sentence: " + e.getMessage());
            return "redirect:/sentences/create";
        }
    }

    @GetMapping("/{word}/{sourceLang}/{targetLang}/edit")
    public String editForm(@PathVariable String word, 
                          @PathVariable String sourceLang, 
                          @PathVariable String targetLang, 
                          Model model) {
        Optional<SentenceData> sentence = sentenceStorageService.findSentence(word, sourceLang, targetLang);
        if (sentence.isPresent()) {
            SentenceData data = sentence.get();
            SentenceForm form = new SentenceForm();
            form.setWord(word);
            form.setSourceLanguage(sourceLang);
            form.setTargetLanguage(targetLang);
            form.setWordRomanized(data.getTargetLanguageLatinCharacters());
            form.setSentenceSource(data.getSourceLanguageSentence());
            form.setSentenceTransliteration(data.getTargetLanguageTransliteration());
            form.setSentenceTarget(data.getSourceLanguageSentence());
            form.setWordStructure(data.getSourceLanguageStructure());
            model.addAttribute("sentence", form);
            return "sentences/edit";
        } else {
            return "redirect:/sentences?error=not-found";
        }
    }

    @PostMapping("/{word}/{sourceLang}/{targetLang}/edit")
    public String updateSentence(@PathVariable String word, 
                                @PathVariable String sourceLang, 
                                @PathVariable String targetLang, 
                                @ModelAttribute SentenceForm form, 
                                RedirectAttributes redirectAttributes) {
        try {
            SentenceData sentenceData = new SentenceData();
            sentenceData.setTargetLanguageLatinCharacters(form.getWordRomanized());
            sentenceData.setTargetLanguageSentence(form.getSentenceSource());
            sentenceData.setTargetLanguageTransliteration(form.getSentenceTransliteration());
            sentenceData.setSourceLanguageSentence(form.getSentenceTarget());
            sentenceData.setSourceLanguageStructure(form.getWordStructure());
            
            sentenceStorageService.saveSentence(form.getWord(), form.getSourceLanguage(), 
                                              form.getTargetLanguage(), sentenceData);
            redirectAttributes.addFlashAttribute("success", "Sentence updated successfully!");
            return "redirect:/sentences";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update sentence: " + e.getMessage());
            return "redirect:/sentences/" + word + "/" + sourceLang + "/" + targetLang + "/edit";
        }
    }

    @PostMapping("/{word}/{sourceLang}/{targetLang}/delete")
    public String deleteSentence(@PathVariable String word, 
                                @PathVariable String sourceLang, 
                                @PathVariable String targetLang, 
                                RedirectAttributes redirectAttributes) {
        try {
            sentenceStorageService.deleteSentence(word, sourceLang, targetLang);
            redirectAttributes.addFlashAttribute("success", "Sentence deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete sentence: " + e.getMessage());
        }
        return "redirect:/sentences";
    }

    // Form class for handling sentence data
    public static class SentenceForm {
        private String word;
        private String sourceLanguage;
        private String targetLanguage;
        private String wordRomanized;
        private String sentenceSource;
        private String sentenceTransliteration;
        private String sentenceTarget;
        private String wordStructure;

        // Getters and setters
        public String getWord() { return word; }
        public void setWord(String word) { this.word = word; }
        
        public String getSourceLanguage() { return sourceLanguage; }
        public void setSourceLanguage(String sourceLanguage) { this.sourceLanguage = sourceLanguage; }
        
        public String getTargetLanguage() { return targetLanguage; }
        public void setTargetLanguage(String targetLanguage) { this.targetLanguage = targetLanguage; }
        
        public String getWordRomanized() { return wordRomanized; }
        public void setWordRomanized(String wordRomanized) { this.wordRomanized = wordRomanized; }
        
        public String getSentenceSource() { return sentenceSource; }
        public void setSentenceSource(String sentenceSource) { this.sentenceSource = sentenceSource; }
        
        public String getSentenceTransliteration() { return sentenceTransliteration; }
        public void setSentenceTransliteration(String sentenceTransliteration) { this.sentenceTransliteration = sentenceTransliteration; }
        
        public String getSentenceTarget() { return sentenceTarget; }
        public void setSentenceTarget(String sentenceTarget) { this.sentenceTarget = sentenceTarget; }
        
        public String getWordStructure() { return wordStructure; }
        public void setWordStructure(String wordStructure) { this.wordStructure = wordStructure; }
    }
}
