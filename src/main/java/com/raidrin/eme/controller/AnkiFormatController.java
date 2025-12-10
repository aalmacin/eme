package com.raidrin.eme.controller;

import com.raidrin.eme.anki.AnkiFormat;
import com.raidrin.eme.anki.CardItem;
import com.raidrin.eme.anki.CardType;
import com.raidrin.eme.storage.entity.AnkiFormatEntity;
import com.raidrin.eme.storage.service.AnkiFormatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/anki-formats")
@RequiredArgsConstructor
public class AnkiFormatController {

    private final AnkiFormatService ankiFormatService;

    @GetMapping
    public String listFormats(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("isDefault").descending().and(Sort.by("name").ascending()));
        Page<AnkiFormatEntity> formatPage = ankiFormatService.findAll(pageable);
        model.addAttribute("formats", formatPage.getContent());
        model.addAttribute("page", formatPage);
        return "anki-formats/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("cardTypes", CardType.values());
        return "anki-formats/create";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Optional<AnkiFormatEntity> formatOpt = ankiFormatService.findById(id);
        if (formatOpt.isEmpty()) {
            return "redirect:/anki-formats?error=not-found";
        }

        model.addAttribute("format", formatOpt.get());
        model.addAttribute("cardTypes", CardType.values());
        return "anki-formats/edit";
    }

    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> create(@RequestBody Map<String, Object> request) {
        try {
            String name = (String) request.get("name");
            String description = (String) request.get("description");
            AnkiFormat format = parseAnkiFormat(request);

            AnkiFormatEntity entity = ankiFormatService.createFormat(name, description, format);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "id", entity.getId(),
                "message", "Format created successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/{id}/update")
    @ResponseBody
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            String name = (String) request.get("name");
            String description = (String) request.get("description");
            AnkiFormat format = parseAnkiFormat(request);

            AnkiFormatEntity entity = ankiFormatService.updateFormat(id, name, description, format);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "id", entity.getId(),
                "message", "Format updated successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        try {
            ankiFormatService.deleteFormat(id);
            return "redirect:/anki-formats?message=deleted";
        } catch (Exception e) {
            return "redirect:/anki-formats?error=" + e.getMessage();
        }
    }

    @PostMapping("/{id}/set-default")
    public String setDefault(@PathVariable Long id) {
        try {
            ankiFormatService.setDefaultFormat(id);
            return "redirect:/anki-formats?message=default-set";
        } catch (Exception e) {
            return "redirect:/anki-formats?error=" + e.getMessage();
        }
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> getFormat(@PathVariable Long id) {
        Optional<AnkiFormatEntity> formatOpt = ankiFormatService.findById(id);
        if (formatOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AnkiFormatEntity entity = formatOpt.get();
        return ResponseEntity.ok(Map.of(
            "id", entity.getId(),
            "name", entity.getName(),
            "description", entity.getDescription() != null ? entity.getDescription() : "",
            "format", entity.getFormat()
        ));
    }

    @GetMapping("/all")
    @ResponseBody
    public ResponseEntity<?> getAllFormats() {
        List<AnkiFormatEntity> formats = ankiFormatService.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (AnkiFormatEntity entity : formats) {
            result.add(Map.of(
                "id", entity.getId(),
                "name", entity.getName(),
                "description", entity.getDescription() != null ? entity.getDescription() : "",
                "isDefault", entity.getIsDefault()
            ));
        }

        return ResponseEntity.ok(result);
    }

    @SuppressWarnings("unchecked")
    private AnkiFormat parseAnkiFormat(Map<String, Object> request) {
        Map<String, Object> formatData = (Map<String, Object>) request.get("format");

        String formatName = (String) formatData.get("name");
        List<Map<String, Object>> frontItems = (List<Map<String, Object>>) formatData.get("frontCardItems");
        List<Map<String, Object>> backItems = (List<Map<String, Object>>) formatData.get("backCardItems");

        AnkiFormat format = new AnkiFormat();
        format.setName(formatName);
        format.setFrontCardItems(parseCardItems(frontItems));
        format.setBackCardItems(parseCardItems(backItems));

        return format;
    }

    @SuppressWarnings("unchecked")
    private List<CardItem> parseCardItems(List<Map<String, Object>> items) {
        List<CardItem> cardItems = new ArrayList<>();

        for (Map<String, Object> item : items) {
            CardType cardType = CardType.valueOf((String) item.get("cardType"));
            Integer order = ((Number) item.get("order")).intValue();
            Boolean isToggled = (Boolean) item.get("isToggled");

            cardItems.add(new CardItem(cardType, order, isToggled));
        }

        return cardItems;
    }
}
