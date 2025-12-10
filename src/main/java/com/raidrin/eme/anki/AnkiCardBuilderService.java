package com.raidrin.eme.anki;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AnkiCardBuilderService {

    public String buildCardSide(List<CardItem> cardItems, Map<?, ?> wordData) {
        if (cardItems == null || cardItems.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (CardItem item : cardItems) {
            String content = getCardItemContent(item.getCardType(), wordData);

            if (content != null && !content.isEmpty()) {
                if (item.getIsToggled()) {
                    // Create collapsible/toggle content
                    result.append(createToggleContent(item.getCardType(), content));
                } else {
                    result.append(content);
                }

                // LINE_BREAK already includes its own <br/>, so don't add another
                if (item.getCardType() != CardType.LINE_BREAK) {
                    result.append("<br/>");
                }
            }
        }

        return result.toString();
    }

    private String getCardItemContent(CardType cardType, Map<?, ?> wordData) {
        return switch (cardType) {
            case SOURCE_TEXT -> {
                Object sourceWord = wordData.get("source_word");
                yield sourceWord != null ? sourceWord.toString() : "";
            }
            case TARGET_TEXT -> {
                if (wordData.containsKey("translations")) {
                    List<?> translations = (List<?>) wordData.get("translations");
                    StringBuilder sb = new StringBuilder();
                    if (!translations.isEmpty()) {
                        for (Object trans : translations) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(trans.toString());
                        }
                    }
                    yield sb.toString();
                }
                yield "";
            }
            case SOURCE_TRANSLITERATION -> {
                Object transliteration = wordData.get("source_transliteration");
                yield transliteration != null ? transliteration.toString() : "";
            }
            case SOURCE_AUDIO -> {
                if (wordData.containsKey("source_audio_file")) {
                    String audioFile = wordData.get("source_audio_file").toString();
                    yield "[sound:" + audioFile + "]";
                }
                yield "";
            }
            case SENTENCE_LATIN -> {
                Object sentenceLatin = wordData.get("sentence_latin");
                yield sentenceLatin != null ? sentenceLatin.toString() : "";
            }
            case SENTENCE_TRANSLITERATION -> {
                Object sentenceTransliteration = wordData.get("sentence_transliteration");
                yield sentenceTransliteration != null ? sentenceTransliteration.toString() : "";
            }
            case SENTENCE_SOURCE -> {
                Object sentenceSource = wordData.get("sentence_source");
                yield sentenceSource != null ? sentenceSource.toString() : "";
            }
            case SENTENCE_STRUCTURE -> {
                Object sentenceStructure = wordData.get("sentence_structure");
                yield sentenceStructure != null ? sentenceStructure.toString() : "";
            }
            case SENTENCE_SOURCE_AUDIO -> {
                if (wordData.containsKey("sentence_source_audio_file")) {
                    String audioFile = wordData.get("sentence_source_audio_file").toString();
                    yield "[sound:" + audioFile + "]";
                }
                yield "";
            }
            case IMAGE -> {
                if (wordData.containsKey("image_file") && wordData.get("image_file") != null) {
                    String imageFile = wordData.get("image_file").toString();
                    if (!imageFile.isEmpty()) {
                        yield "<img src=\"" + imageFile + "\" style=\"max-width: 300px;\" />";
                    }
                }
                yield "";
            }
            case MNEMONIC_KEYWORD -> {
                Object mnemonicKeyword = wordData.get("mnemonic_keyword");
                yield mnemonicKeyword != null ? mnemonicKeyword.toString() : "";
            }
            case MNEMONIC_SENTENCE -> {
                Object mnemonicSentence = wordData.get("mnemonic_sentence");
                yield mnemonicSentence != null ? mnemonicSentence.toString() : "";
            }
            case LINE_BREAK -> "<br/>";
        };
    }

    private String createToggleContent(CardType cardType, String content) {
        String uniqueId = "toggle-" + System.currentTimeMillis() + "-" + Math.random();
        String label = formatCardTypeLabel(cardType);

        return "<div style=\"margin: 10px 0; border: 1px solid #ddd; border-radius: 4px; padding: 5px;\">" +
                "<button onclick=\"var el = document.getElementById('" + uniqueId + "'); " +
                "if(el.style.display === 'none') { el.style.display = 'block'; this.textContent = 'Hide " + label + "'; } " +
                "else { el.style.display = 'none'; this.textContent = 'Show " + label + "'; }\" " +
                "style=\"background: #4CAF50; color: white; border: none; padding: 8px 16px; " +
                "cursor: pointer; border-radius: 4px; font-size: 14px; margin-bottom: 5px;\">" +
                "Show " + label +
                "</button>" +
                "<div id=\"" + uniqueId + "\" style=\"display: none; padding: 10px; background: #f9f9f9; border-radius: 4px; margin-top: 5px;\">" +
                content +
                "</div>" +
                "</div>";
    }

    private String formatCardTypeLabel(CardType cardType) {
        return switch (cardType) {
            case SOURCE_TEXT -> "Source Text";
            case TARGET_TEXT -> "Target Text";
            case SOURCE_TRANSLITERATION -> "Source Transliteration";
            case SOURCE_AUDIO -> "Source Audio";
            case SENTENCE_LATIN -> "Sentence Latin";
            case SENTENCE_TRANSLITERATION -> "Sentence Transliteration";
            case SENTENCE_SOURCE -> "Sentence Source";
            case SENTENCE_STRUCTURE -> "Sentence Structure";
            case SENTENCE_SOURCE_AUDIO -> "Sentence Source Audio";
            case IMAGE -> "Mnemonic Image";
            case MNEMONIC_KEYWORD -> "Mnemonic Keyword";
            case MNEMONIC_SENTENCE -> "Mnemonic Sentence";
            case LINE_BREAK -> "Line Break";
        };
    }

    public String buildPreviewHtml(List<CardItem> cardItems) {
        if (cardItems == null || cardItems.isEmpty()) {
            return "<div class=\"text-muted\">Empty</div>";
        }

        StringBuilder sb = new StringBuilder();
        for (CardItem item : cardItems) {
            String label = formatCardTypeLabel(item.getCardType());
            String toggleMarker = item.getIsToggled() ? " <span class=\"badge bg-primary\">Collapsible</span>" : "";

            sb.append("<div>").append(label).append(toggleMarker).append("</div>");
        }

        return sb.toString();
    }
}
