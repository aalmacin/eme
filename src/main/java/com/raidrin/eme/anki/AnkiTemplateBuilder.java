package com.raidrin.eme.anki;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Utility for building Anki card templates from CardItem lists
 * Generates HTML templates with Anki field placeholders
 */
@Component
@RequiredArgsConstructor
public class AnkiTemplateBuilder {

    private final AnkiFieldMapper fieldMapper;

    /**
     * Build an Anki template from a list of card items
     * @param cardItems List of card items to include in the template
     * @return HTML template with Anki field placeholders
     */
    public String buildTemplate(List<CardItem> cardItems) {
        if (cardItems == null || cardItems.isEmpty()) {
            return "";
        }

        StringBuilder template = new StringBuilder();
        template.append("<div class=\"card-content\">\n");

        for (CardItem item : cardItems) {
            String content = buildCardItemTemplate(item);
            if (content != null && !content.isEmpty()) {
                template.append(content);
            }
        }

        template.append("</div>\n");
        return template.toString();
    }

    /**
     * Build template content for a single card item
     */
    private String buildCardItemTemplate(CardItem item) {
        CardType cardType = item.getCardType();
        String fieldName = fieldMapper.getFieldName(cardType);

        // Handle LINE_BREAK (no field, just formatting)
        if (cardType == CardType.LINE_BREAK) {
            return "<br/>\n";
        }

        // Handle toggled (collapsible) content
        if (item.getIsToggled() != null && item.getIsToggled()) {
            return buildToggledTemplate(cardType, fieldName);
        }

        // Regular field display with conditional rendering
        return buildRegularTemplate(cardType, fieldName);
    }

    /**
     * Build template for a regular (non-toggled) field
     */
    private String buildRegularTemplate(CardType cardType, String fieldName) {
        StringBuilder sb = new StringBuilder();

        // Open conditional block
        sb.append("{{#").append(fieldName).append("}}\n");

        // Add field content with appropriate formatting
        sb.append("<div class=\"field-").append(fieldName.toLowerCase()).append("\">\n");

        if (cardType == CardType.IMAGE) {
            // Special handling for images
            sb.append("  <img src=\"{{").append(fieldName).append("}}\" class=\"mnemonic-image\" />\n");
        } else if (cardType == CardType.SOURCE_AUDIO || cardType == CardType.SENTENCE_SOURCE_AUDIO) {
            // Special handling for audio
            sb.append("  {{").append(fieldName).append("}}\n");
        } else {
            // Regular text field
            sb.append("  {{").append(fieldName).append("}}\n");
        }

        sb.append("</div>\n");

        // Close conditional block
        sb.append("{{/").append(fieldName).append("}}\n");

        return sb.toString();
    }

    /**
     * Build template for a toggled (collapsible) field
     */
    private String buildToggledTemplate(CardType cardType, String fieldName) {
        String label = formatCardTypeLabel(cardType);
        String toggleId = fieldName.toLowerCase() + "-toggle";

        StringBuilder sb = new StringBuilder();

        // Open conditional block
        sb.append("{{#").append(fieldName).append("}}\n");

        sb.append("<div class=\"toggle-container\">\n");
        sb.append("  <button class=\"toggle-btn\" onclick=\"toggleField('").append(toggleId).append("')\">\n");
        sb.append("    Show ").append(label).append("\n");
        sb.append("  </button>\n");
        sb.append("  <div id=\"").append(toggleId).append("\" class=\"toggle-content\" style=\"display: none;\">\n");

        if (cardType == CardType.IMAGE) {
            sb.append("    <img src=\"{{").append(fieldName).append("}}\" class=\"mnemonic-image\" />\n");
        } else if (cardType == CardType.SOURCE_AUDIO || cardType == CardType.SENTENCE_SOURCE_AUDIO) {
            sb.append("    {{").append(fieldName).append("}}\n");
        } else {
            sb.append("    {{").append(fieldName).append("}}\n");
        }

        sb.append("  </div>\n");
        sb.append("</div>\n");

        // Close conditional block
        sb.append("{{/").append(fieldName).append("}}\n");

        return sb.toString();
    }

    /**
     * Format CardType into a human-readable label
     */
    private String formatCardTypeLabel(CardType cardType) {
        return switch (cardType) {
            case SOURCE_TEXT -> "Source Text";
            case TARGET_TEXT -> "Translation";
            case SOURCE_TRANSLITERATION -> "Transliteration";
            case SOURCE_AUDIO -> "Source Audio";
            case SENTENCE_LATIN -> "Sentence Latin";
            case SENTENCE_TRANSLITERATION -> "Sentence Transliteration";
            case SENTENCE_SOURCE -> "Sentence Source";
            case SENTENCE_STRUCTURE -> "Sentence Structure";
            case SENTENCE_SOURCE_AUDIO -> "Sentence Audio";
            case IMAGE -> "Mnemonic Image";
            case MNEMONIC_KEYWORD -> "Mnemonic Keyword";
            case MNEMONIC_SENTENCE -> "Mnemonic Sentence";
            case LINE_BREAK -> "Line Break";
        };
    }

    /**
     * Generate JavaScript for toggle functionality
     */
    public String getToggleScript() {
        return """
            <script>
            function toggleField(fieldId) {
                var content = document.getElementById(fieldId);
                var btn = event.target;
                if (content.style.display === 'none') {
                    content.style.display = 'block';
                    btn.textContent = btn.textContent.replace('Show', 'Hide');
                } else {
                    content.style.display = 'none';
                    btn.textContent = btn.textContent.replace('Hide', 'Show');
                }
            }
            </script>
            """;
    }

    /**
     * Generate CSS styling for Anki cards
     */
    public String getDefaultCSS() {
        return """
            .card-content {
                font-family: Arial, sans-serif;
                font-size: 16px;
                padding: 20px;
                line-height: 1.6;
            }

            .field-sourcetext {
                font-size: 24px;
                font-weight: bold;
                margin-bottom: 10px;
                color: #2c3e50;
            }

            .field-translation {
                font-size: 20px;
                color: #27ae60;
                margin-bottom: 10px;
            }

            .field-transliteration {
                font-size: 16px;
                color: #7f8c8d;
                font-style: italic;
                margin-bottom: 10px;
            }

            .field-mnemonickeyword {
                font-size: 18px;
                color: #e74c3c;
                font-weight: bold;
                margin: 10px 0;
            }

            .field-mnemonicsentence {
                font-size: 16px;
                color: #555;
                padding: 10px;
                background: #f9f9f9;
                border-left: 3px solid #e74c3c;
                margin: 10px 0;
            }

            .mnemonic-image {
                max-width: 400px;
                max-height: 300px;
                display: block;
                margin: 15px auto;
                border-radius: 8px;
                box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            }

            .toggle-container {
                margin: 10px 0;
            }

            .toggle-btn {
                background: #3498db;
                color: white;
                border: none;
                padding: 8px 16px;
                border-radius: 4px;
                cursor: pointer;
                font-size: 14px;
                margin-bottom: 8px;
            }

            .toggle-btn:hover {
                background: #2980b9;
            }

            .toggle-content {
                padding: 10px;
                background: #f9f9f9;
                border-radius: 4px;
                margin-top: 5px;
            }

            .field-sentencelatin,
            .field-sentencesource {
                font-size: 16px;
                padding: 8px;
                background: #ecf0f1;
                border-radius: 4px;
                margin: 8px 0;
            }

            .field-sentencestructure,
            .field-sentencetransliteration {
                font-size: 14px;
                color: #7f8c8d;
                margin: 5px 0;
            }
            """;
    }
}
