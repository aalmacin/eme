package com.raidrin.eme.controller;

import com.raidrin.eme.storage.entity.CharacterGuideEntity;
import com.raidrin.eme.storage.service.CharacterGuideService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/character-guide")
@RequiredArgsConstructor
public class CharacterGuideController {

    private final CharacterGuideService characterGuideService;

    @GetMapping
    public String listCharacters(Model model, @RequestParam(required = false) String language) {
        List<CharacterGuideEntity> characters;
        if (language != null && !language.trim().isEmpty()) {
            characters = characterGuideService.findByLanguage(language);
        } else {
            characters = characterGuideService.findAll();
        }
        model.addAttribute("characters", characters);
        model.addAttribute("selectedLanguage", language);
        return "character-guide/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("character", new CharacterGuideForm());
        return "character-guide/create";
    }

    @PostMapping("/create")
    public String createCharacter(@ModelAttribute CharacterGuideForm form, RedirectAttributes redirectAttributes) {
        try {
            characterGuideService.save(form.getLanguage(), form.getStartSound(),
                    form.getCharacterName(), form.getCharacterContext());
            redirectAttributes.addFlashAttribute("success", "Character guide entry created successfully!");
            return "redirect:/character-guide";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create character guide: " + e.getMessage());
            return "redirect:/character-guide/create";
        }
    }

    @GetMapping("/{language}/{startSound}/edit")
    public String editForm(@PathVariable String language,
                          @PathVariable String startSound,
                          Model model) {
        Optional<CharacterGuideEntity> character = characterGuideService.findByLanguageAndStartSound(language, startSound);
        if (character.isPresent()) {
            CharacterGuideEntity entity = character.get();
            CharacterGuideForm form = new CharacterGuideForm();
            form.setLanguage(entity.getLanguage());
            form.setStartSound(entity.getStartSound());
            form.setCharacterName(entity.getCharacterName());
            form.setCharacterContext(entity.getCharacterContext());
            model.addAttribute("character", form);
            return "character-guide/edit";
        } else {
            return "redirect:/character-guide?error=not-found";
        }
    }

    @PostMapping("/{language}/{startSound}/edit")
    public String updateCharacter(@PathVariable String language,
                                 @PathVariable String startSound,
                                 @ModelAttribute CharacterGuideForm form,
                                 RedirectAttributes redirectAttributes) {
        try {
            characterGuideService.save(form.getLanguage(), form.getStartSound(),
                    form.getCharacterName(), form.getCharacterContext());
            redirectAttributes.addFlashAttribute("success", "Character guide updated successfully!");
            return "redirect:/character-guide";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update character guide: " + e.getMessage());
            return "redirect:/character-guide/" + language + "/" + startSound + "/edit";
        }
    }

    @PostMapping("/{language}/{startSound}/delete")
    public String deleteCharacter(@PathVariable String language,
                                 @PathVariable String startSound,
                                 RedirectAttributes redirectAttributes) {
        try {
            characterGuideService.delete(language, startSound);
            redirectAttributes.addFlashAttribute("success", "Character guide deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete character guide: " + e.getMessage());
        }
        return "redirect:/character-guide";
    }

    /**
     * Form class for character guide
     */
    public static class CharacterGuideForm {
        private String language;
        private String startSound;
        private String characterName;
        private String characterContext;

        public CharacterGuideForm() {}

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public String getStartSound() { return startSound; }
        public void setStartSound(String startSound) { this.startSound = startSound; }

        public String getCharacterName() { return characterName; }
        public void setCharacterName(String characterName) { this.characterName = characterName; }

        public String getCharacterContext() { return characterContext; }
        public void setCharacterContext(String characterContext) { this.characterContext = characterContext; }
    }
}
