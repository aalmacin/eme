package com.raidrin.eme.anki;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility for mapping CardType enum values to standardized Anki field names
 * Used for custom note type creation and field mapping
 */
@Component
public class AnkiFieldMapper {

    /**
     * Get the standardized Anki field name for a CardType
     * @param cardType The card type to map
     * @return The standard field name (e.g., "SourceText", "Translation")
     */
    public String getFieldName(CardType cardType) {
        return switch (cardType) {
            case SOURCE_TEXT -> "SourceText";
            case TARGET_TEXT -> "Translation";
            case SOURCE_TRANSLITERATION -> "Transliteration";
            case SOURCE_AUDIO -> "SourceAudio";
            case SENTENCE_LATIN -> "SentenceLatin";
            case SENTENCE_TRANSLITERATION -> "SentenceTransliteration";
            case SENTENCE_SOURCE -> "SentenceSource";
            case SENTENCE_STRUCTURE -> "SentenceStructure";
            case SENTENCE_SOURCE_AUDIO -> "SentenceSourceAudio";
            case IMAGE -> "Image";
            case MNEMONIC_KEYWORD -> "MnemonicKeyword";
            case MNEMONIC_SENTENCE -> "MnemonicSentence";
            case LINE_BREAK -> null; // LINE_BREAK is not a field, it's formatting only
        };
    }

    /**
     * Get the Anki field placeholder for a CardType
     * Example: {{SourceText}}, {{#Translation}}...{{/Translation}}
     * @param cardType The card type
     * @param conditional If true, wrap in conditional tags
     * @return The Anki template placeholder
     */
    public String getFieldPlaceholder(CardType cardType, boolean conditional) {
        String fieldName = getFieldName(cardType);
        if (fieldName == null) {
            return null; // LINE_BREAK has no field
        }

        if (conditional) {
            return "{{#" + fieldName + "}}{{" + fieldName + "}}{{/" + fieldName + "}}";
        } else {
            return "{{" + fieldName + "}}";
        }
    }

    /**
     * Get all field names from a list of CardItems
     * @param cardItems List of card items
     * @return List of unique field names (excluding LINE_BREAK)
     */
    public List<String> getFieldNamesFromCardItems(List<CardItem> cardItems) {
        if (cardItems == null || cardItems.isEmpty()) {
            return new ArrayList<>();
        }

        return cardItems.stream()
                .map(CardItem::getCardType)
                .map(this::getFieldName)
                .filter(fieldName -> fieldName != null) // Exclude LINE_BREAK
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Get all unique field names from both front and back card items
     * @param frontCardItems Front card items
     * @param backCardItems Back card items
     * @return List of unique field names needed for the note type
     */
    public List<String> getAllFieldNames(List<CardItem> frontCardItems, List<CardItem> backCardItems) {
        List<String> allFields = new ArrayList<>();

        if (frontCardItems != null) {
            allFields.addAll(getFieldNamesFromCardItems(frontCardItems));
        }

        if (backCardItems != null) {
            allFields.addAll(getFieldNamesFromCardItems(backCardItems));
        }

        // Return distinct fields maintaining order
        return allFields.stream().distinct().collect(Collectors.toList());
    }
}
