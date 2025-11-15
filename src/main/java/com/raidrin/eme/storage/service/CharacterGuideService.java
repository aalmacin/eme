package com.raidrin.eme.storage.service;

import com.raidrin.eme.storage.entity.CharacterGuideEntity;
import com.raidrin.eme.storage.repository.CharacterGuideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        String normalizedWord = transliteration.toLowerCase().trim();

        System.out.println("Character matching: original word='" + word + "', transliteration='" + transliteration + "', normalized='" + normalizedWord + "', language='" + language + "'");

        List<CharacterGuideEntity> charactersForLanguage = findByLanguage(language);

        // Find the longest matching start sound
        Optional<CharacterGuideEntity> match = charactersForLanguage.stream()
                .filter(c -> normalizedWord.startsWith(c.getStartSound().toLowerCase()))
                .max((c1, c2) -> Integer.compare(c1.getStartSound().length(), c2.getStartSound().length()));

        if (match.isPresent()) {
            System.out.println("Found character match: " + match.get().getCharacterName() + " from " + match.get().getCharacterContext() + " (start sound: " + match.get().getStartSound() + ")");
        } else {
            System.out.println("No character match found for '" + normalizedWord + "' in language '" + language + "'");
        }

        return match;
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
