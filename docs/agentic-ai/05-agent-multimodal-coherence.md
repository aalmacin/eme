# Agent #3: Multi-Modal Coherence Agent

[← Back to Main Document](../../AGENTIC_AI_INTEGRATION.md) | [← Previous: Quality Assurance](04-agent-quality-assurance.md) | [Next: Cost Optimization →](06-agent-cost-optimization.md)

---

## Executive Summary

The **Multi-Modal Coherence Agent** ensures that text, audio, and images work together cohesively to create effective multi-sensory learning experiences. It validates that audio matches transliteration, images represent mnemonics, and all modalities reinforce the same learning objective.

**Priority:** MEDIUM (Phase 2)
**Estimated Effort:** 50 hours
**Expected ROI:** Enhanced memory retention through coherent multi-modal learning

---

## Problem Statement

### Current Issue: Disconnected Modalities

**Problem:** Text, audio, and images are generated independently without coordination

**Example Issues:**

1. **Audio-Transliteration Mismatch**
   ```
   Word: "namaste"
   Transliteration: "nah-mah-STAY"
   Audio pronunciation: "nah-mah-steh"
   Issue: Emphasis pattern doesn't match
   ```

2. **Image-Mnemonic Disconnect**
   ```
   Mnemonic: "Imagine Goku eating a golden samosa"
   Generated Image: Picture of a generic samosa (no Goku, not golden)
   Issue: Key memory anchors missing from image
   ```

3. **Sentence Doesn't Reinforce Word**
   ```
   Word: "नमस्ते" (namaste)
   Sentence: "मैं हिंदी बोलता हूं" (I speak Hindi)
   Issue: Sentence doesn't use or demonstrate the target word
   ```

---

## Agent Capabilities

### 1. Audio-Text Coherence Validation

**Validates:**
- Audio pronunciation matches romanized transliteration
- Emphasis/stress patterns align
- Audio clarity and quality
- No audio artifacts or errors

**Implementation Approach:**
```java
public CoherenceScore validateAudioTextCoherence(String audioFileUrl,
                                                 String transliteration,
                                                 String originalText,
                                                 String language) {
    // 1. Transcribe audio using speech-to-text
    String transcribed = speechToTextService.transcribe(audioFileUrl, language);

    // 2. Compare transcription with original text
    double textMatchScore = calculateSimilarity(transcribed, originalText);

    // 3. Validate transliteration matches pronunciation
    double transliterationScore = validateTransliterationAccuracy(
        audioFileUrl, transliteration, language
    );

    // 4. Check audio quality (no clipping, clear speech)
    AudioQualityMetrics quality = analyzeAudioQuality(audioFileUrl);

    return CoherenceScore.builder()
        .overallScore((textMatchScore + transliterationScore + quality.getScore()) / 3.0)
        .textMatchScore(textMatchScore)
        .transliterationScore(transliterationScore)
        .audioQualityScore(quality.getScore())
        .passed(textMatchScore > 0.85 && quality.getScore() > 0.7)
        .build();
}
```

---

### 2. Image-Mnemonic Coherence Validation

**Validates:**
- Image visually represents mnemonic elements
- Key characters/objects from mnemonic are visible
- Scene composition matches description
- Visual metaphors align with memory strategy

**Implementation:**
```java
public CoherenceScore validateImageMnemonicCoherence(String imageUrl,
                                                     MnemonicData mnemonic) {
    // 1. Get image description using vision AI
    ImageAnalysis imageAnalysis = visionService.analyzeImage(imageUrl);
    String imageDescription = imageAnalysis.getDescription();
    List<String> detectedObjects = imageAnalysis.getDetectedObjects();

    // 2. Extract key elements from mnemonic
    List<String> mnemonicElements = extractKeyElements(mnemonic.getMnemonicSentence());

    // 3. Check if key elements present in image
    int matchingElements = 0;
    for (String element : mnemonicElements) {
        if (isElementInImage(element, detectedObjects, imageDescription)) {
            matchingElements++;
        }
    }
    double elementCoverageScore = (double) matchingElements / mnemonicElements.size();

    // 4. Semantic similarity between mnemonic and image description
    double semanticScore = calculateSemanticSimilarity(
        mnemonic.getMnemonicSentence(),
        imageDescription
    );

    // 5. Check if image prompt was followed
    double promptAdherenceScore = checkPromptAdherence(
        mnemonic.getImagePrompt(),
        imageDescription
    );

    return CoherenceScore.builder()
        .overallScore((elementCoverageScore + semanticScore + promptAdherenceScore) / 3.0)
        .elementCoverageScore(elementCoverageScore)
        .semanticSimilarityScore(semanticScore)
        .promptAdherenceScore(promptAdherenceScore)
        .passed(elementCoverageScore > 0.6 && semanticScore > 0.7)
        .build();
}
```

---

### 3. Cross-Modal Consistency Validation

**Validates:**
- All modalities teach the same concept
- No conflicting information across text/audio/image
- Difficulty level consistent across modalities

**Implementation:**
```java
public CoherenceScore validateCrossModalConsistency(WordData wordData) {
    List<Double> coherenceScores = new ArrayList<>();

    // 1. Translation consistency
    if (wordData.hasTranslation() && wordData.hasSentence()) {
        double translationConsistency = checkTranslationInSentence(
            wordData.getTranslation(),
            wordData.getSentence()
        );
        coherenceScores.add(translationConsistency);
    }

    // 2. Mnemonic-Translation alignment
    if (wordData.hasMnemonic() && wordData.hasTranslation()) {
        double mnemonicAlignment = checkMnemonicRelatesToTranslation(
            wordData.getMnemonic(),
            wordData.getTranslation()
        );
        coherenceScores.add(mnemonicAlignment);
    }

    // 3. All assets reference same word
    double wordPresenceScore = checkWordPresenceAcrossModalities(wordData);
    coherenceScores.add(wordPresenceScore);

    double overall = coherenceScores.stream()
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(0.0);

    return CoherenceScore.builder()
        .overallScore(overall)
        .passed(overall > 0.75)
        .build();
}
```

---

### 4. Automated Correction Suggestions

**Capability:** When coherence issues detected, suggest corrections

**Example:**
```java
public CoherenceCorrection suggestCorrections(CoherenceScore score, WordData wordData) {
    List<Correction> corrections = new ArrayList<>();

    // If audio doesn't match text
    if (score.getTextMatchScore() < 0.85) {
        corrections.add(Correction.builder()
            .issue("Audio pronunciation doesn't match text")
            .suggestion("Regenerate audio with corrected pronunciation")
            .action(CorrectionAction.REGENERATE_AUDIO)
            .parameters(Map.of(
                "text", wordData.getWord(),
                "language", wordData.getSourceLanguage(),
                "emphasis", extractIntendedEmphasis(wordData.getTransliteration())
            ))
            .build());
    }

    // If image missing key mnemonic elements
    if (score.getElementCoverageScore() < 0.6) {
        List<String> missingElements = identifyMissingElements(score);
        corrections.add(Correction.builder()
            .issue("Image missing key mnemonic elements: " + missingElements)
            .suggestion("Regenerate image with enhanced prompt emphasizing missing elements")
            .action(CorrectionAction.REGENERATE_IMAGE)
            .parameters(Map.of(
                "enhanced_prompt", enhancePromptWithMissingElements(
                    wordData.getMnemonic().getImagePrompt(),
                    missingElements
                )
            ))
            .build());
    }

    return CoherenceCorrection.builder()
        .corrections(corrections)
        .autoFixable(!corrections.isEmpty())
        .build();
}
```

---

## Technical Architecture

### Main Agent Class

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiModalCoherenceAgent implements AgentInterface {
    private final SpeechToTextService speechToTextService;
    private final VisionAnalysisService visionService;
    private final SemanticSimilarityService semanticService;
    private final AudioQualityAnalyzer audioAnalyzer;
    private final AgentMetricsService metricsService;
    private final AgentProperties agentProperties;

    @Override
    public String getName() {
        return "MultiModalCoherenceAgent";
    }

    /**
     * Validate complete word data for multi-modal coherence
     */
    public ValidationResult validateWordDataCoherence(WordData wordData) {
        if (!agentProperties.getMultimodalCoherence().isEnabled()) {
            return ValidationResult.skipped("Multi-modal coherence agent disabled");
        }

        Map<String, CoherenceScore> scores = new HashMap<>();

        // 1. Audio-Text coherence
        if (wordData.hasAudio() && wordData.hasTransliteration()) {
            CoherenceScore audioScore = validateAudioTextCoherence(
                wordData.getAudioSourceFile(),
                wordData.getSourceTransliteration(),
                wordData.getWord(),
                wordData.getSourceLanguage()
            );
            scores.put("audio_text", audioScore);
        }

        // 2. Image-Mnemonic coherence
        if (wordData.hasImage() && wordData.hasMnemonic()) {
            CoherenceScore imageScore = validateImageMnemonicCoherence(
                wordData.getImageFile(),
                wordData.getMnemonic()
            );
            scores.put("image_mnemonic", imageScore);
        }

        // 3. Cross-modal consistency
        CoherenceScore crossModalScore = validateCrossModalConsistency(wordData);
        scores.put("cross_modal", crossModalScore);

        // Calculate overall coherence
        double overallCoherence = scores.values().stream()
            .mapToDouble(CoherenceScore::getOverallScore)
            .average()
            .orElse(0.0);

        boolean passed = overallCoherence >= 0.75;

        // Generate correction suggestions if failed
        CoherenceCorrection corrections = null;
        if (!passed) {
            corrections = suggestCorrections(scores, wordData);
        }

        return ValidationResult.builder()
            .passed(passed)
            .score(overallCoherence)
            .details(scores)
            .corrections(corrections)
            .build();
    }
}
```

---

## Integration Points

### Modified SessionOrchestrationService

```java
@Service
@RequiredArgsConstructor
public class SessionOrchestrationService {
    private final MultiModalCoherenceAgent coherenceAgent;

    public CompletableFuture<Void> processWord(String word, ...) {
        // ... generate translation, sentence, mnemonic, image, audio ...

        WordData wordData = WordData.builder()
            .word(word)
            .translation(translation)
            .sentence(sentence)
            .mnemonic(mnemonic)
            .imageFile(image.getUrl())
            .audioSourceFile(audio.getUrl())
            .sourceTransliteration(transliteration)
            .build();

        // VALIDATE MULTI-MODAL COHERENCE
        ValidationResult coherenceValidation =
            coherenceAgent.validateWordDataCoherence(wordData);

        if (!coherenceValidation.isPassed()) {
            log.warn("Multi-modal coherence issues detected", "word", word,
                "score", coherenceValidation.getScore());

            // Apply automatic corrections if available
            if (coherenceValidation.getCorrections().isAutoFixable()) {
                wordData = applyCorrections(wordData, coherenceValidation.getCorrections());

                // Re-validate
                coherenceValidation = coherenceAgent.validateWordDataCoherence(wordData);
            }
        }

        if (coherenceValidation.isPassed()) {
            log.info("Multi-modal coherence validated", "word", word,
                "score", coherenceValidation.getScore());
        }

        return CompletableFuture.completedFuture(wordData);
    }

    private WordData applyCorrections(WordData original, CoherenceCorrection corrections) {
        WordData corrected = original;

        for (Correction correction : corrections.getCorrections()) {
            switch (correction.getAction()) {
                case REGENERATE_AUDIO:
                    String newAudio = audioService.regenerate(correction.getParameters());
                    corrected = corrected.withAudioSourceFile(newAudio);
                    break;

                case REGENERATE_IMAGE:
                    String newImage = imageService.regenerate(correction.getParameters());
                    corrected = corrected.withImageFile(newImage);
                    break;

                case ADJUST_TRANSLITERATION:
                    String newTranslit = adjustTransliteration(correction.getParameters());
                    corrected = corrected.withSourceTransliteration(newTranslit);
                    break;
            }
        }

        return corrected;
    }
}
```

---

## Database Schema

### V12__add_coherence_tracking.sql

```sql
CREATE TABLE coherence_scores (
    id BIGSERIAL PRIMARY KEY,
    word_id BIGINT,
    audio_text_score DECIMAL(4,3),
    image_mnemonic_score DECIMAL(4,3),
    cross_modal_score DECIMAL(4,3),
    overall_score DECIMAL(4,3) NOT NULL,
    corrections_applied JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE,
    CHECK (overall_score >= 0.0 AND overall_score <= 1.0)
);

CREATE INDEX idx_coherence_scores_word_id ON coherence_scores(word_id);
CREATE INDEX idx_coherence_scores_overall ON coherence_scores(overall_score DESC);
```

---

## Success Metrics

- ✅ Audio-text coherence score average >0.90
- ✅ Image-mnemonic coherence score average >0.75
- ✅ Automatic correction success rate >70%
- ✅ User reports of misaligned content decrease by >80%

---

[Next: Cost Optimization Agent →](06-agent-cost-optimization.md)

[← Back to Main Document](../../AGENTIC_AI_INTEGRATION.md)
