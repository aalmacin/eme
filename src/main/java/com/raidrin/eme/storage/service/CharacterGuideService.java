package com.raidrin.eme.storage.service;

import com.raidrin.eme.storage.entity.CharacterGuideEntity;
import com.raidrin.eme.storage.repository.CharacterGuideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CharacterGuideService {

    private final CharacterGuideRepository characterGuideRepository;

    public Optional<CharacterGuideEntity> findByLanguageAndStartSound(String language, String startSound) {
        validateParameters(language, startSound);
        return characterGuideRepository.findByLanguageAndStartSound(language, startSound);
    }

    public List<CharacterGuideEntity> findByLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("Language must be provided");
        }
        return characterGuideRepository.findByLanguageOrderByStartSound(language);
    }

    public List<CharacterGuideEntity> findAll() {
        return characterGuideRepository.findAllOrderedByLanguageAndSound();
    }

    @Transactional
    public CharacterGuideEntity save(String language, String startSound, String characterName, String characterContext) {
        validateParameters(language, startSound);
        if (characterName == null || characterName.trim().isEmpty()) {
            throw new IllegalArgumentException("Character name must be provided");
        }
        if (characterContext == null || characterContext.trim().isEmpty()) {
            throw new IllegalArgumentException("Character context must be provided");
        }

        Optional<CharacterGuideEntity> existing = characterGuideRepository.findByLanguageAndStartSound(language, startSound);
        if (existing.isPresent()) {
            CharacterGuideEntity entity = existing.get();
            entity.setCharacterName(characterName);
            entity.setCharacterContext(characterContext);
            entity.setUpdatedAt(LocalDateTime.now());
            return characterGuideRepository.save(entity);
        } else {
            CharacterGuideEntity entity = new CharacterGuideEntity(language, startSound, characterName, characterContext);
            return characterGuideRepository.save(entity);
        }
    }

    @Transactional
    public void delete(String language, String startSound) {
        validateParameters(language, startSound);
        characterGuideRepository.deleteByLanguageAndStartSound(language, startSound);
    }

    public boolean exists(String language, String startSound) {
        validateParameters(language, startSound);
        return characterGuideRepository.existsByLanguageAndStartSound(language, startSound);
    }

    /**
     * Helper method to find character by matching the start of a word.
     * Requires transliteration to be provided (from translation service or cache).
     *
     * Lookup strategy:
     * 1. Strip accents from transliteration (e.g., mā -> ma)
     * 2. Try to match first 3 characters
     * 3. If not found, try first 2 characters
     * 4. If not found, try first 1 character
     * 5. If still not found, return empty
     */
    public Optional<CharacterGuideEntity> findMatchingCharacterForWord(String word, String language, String transliteration) {
        if (word == null || word.trim().isEmpty()) {
            return Optional.empty();
        }
        if (language == null || language.trim().isEmpty()) {
            return Optional.empty();
        }
        if (transliteration == null || transliteration.trim().isEmpty()) {
            System.out.println("No transliteration provided for character matching: word='" + word + "', language='" + language + "'");
            return Optional.empty();
        }

        // Strip accents and normalize (e.g., mā -> ma, é -> e)
        String normalizedWord = stripAccents(transliteration.toLowerCase().trim());

        // System.out.println("Character matching: original word='" + word + "', transliteration='" + transliteration + "', normalized='" + normalizedWord + "', language='" + language + "'");

        // Try 3 chars, then 2 chars, then 1 char
        Optional<CharacterGuideEntity> match = tryFindByStartSound(normalizedWord, language, 3);
        if (match.isEmpty()) {
            match = tryFindByStartSound(normalizedWord, language, 2);
        }
        if (match.isEmpty()) {
            match = tryFindByStartSound(normalizedWord, language, 1);
        }

        if (match.isPresent()) {
            // System.out.println("Found character match: " + match.get().getCharacterName() + " from " + match.get().getCharacterContext() + " (start sound: " + match.get().getStartSound() + ")");
        } else {
            System.out.println("No character match found for '" + normalizedWord + "' in language '" + language + "' (tried 3, 2, 1 chars)");
        }

        return match;
    }

    /**
     * Try to find a character guide by matching a specific number of starting characters
     */
    private Optional<CharacterGuideEntity> tryFindByStartSound(String normalizedWord, String language, int chars) {
        if (normalizedWord.length() < chars) {
            return Optional.empty();
        }

        String prefix = normalizedWord.substring(0, chars);
        // System.out.println("Trying to find character with start sound: '" + prefix + "'");

        return findByLanguageAndStartSound(language, prefix);
    }

    /**
     * Strip accents from text (e.g., mā -> ma, é -> e, ñ -> n)
     */
    private String stripAccents(String text) {
        if (text == null) {
            return null;
        }

        // Normalize to NFD (decomposed form) and remove diacritical marks
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        // Remove all combining diacritical marks (accents)
        String stripped = normalized.replaceAll("\\p{M}", "");

        return stripped;
    }

    private void validateParameters(String language, String startSound) {
        if (language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("Language must be provided");
        }
        if (startSound == null || startSound.trim().isEmpty()) {
            throw new IllegalArgumentException("Start sound must be provided");
        }
    }
}
