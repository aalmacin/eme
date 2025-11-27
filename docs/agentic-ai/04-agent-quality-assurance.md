# Agent #2: Quality Assurance Agent

[← Back to Main Document](../../AGENTIC_AI_INTEGRATION.md) | [← Previous: Error Recovery](03-agent-error-recovery.md) | [Next: Multi-Modal Coherence →](05-agent-multimodal-coherence.md)

---

## Executive Summary

The **Quality Assurance Agent** validates all AI-generated content before it reaches users or gets cached, ensuring translations are accurate, sentences are grammatically correct, mnemonics align with images, and no culturally inappropriate content slips through.

**Priority:** HIGH (Phase 2)
**Estimated Effort:** 60 hours
**Expected ROI:** 2x improvement in content quality scores

---

## Problem Statement

### Current State: Zero Quality Validation

**Issue:** AI outputs are trusted blindly and immediately cached/presented

**Examples of Problems That Occur:**

1. **Incorrect Translations**
   ```
   Word: "embarazada" (Spanish)
   AI Translation: "embarrassed"
   Actual Meaning: "pregnant"
   Issue: False friend not caught
   ```

2. **Sentences Not Using Target Word**
   ```
   Word: "namaste"
   Generated Sentence: "Hello, how are you today?"
   Issue: Doesn't actually contain the word "namaste"
   ```

3. **Grammatically Incorrect Sentences**
   ```
   Word: "samosa"
   Generated: "I eating samosa yesterday at restaurant"
   Issue: Wrong tense, missing article
   ```

4. **Mnemonic-Image Misalignment**
   ```
   Mnemonic: "Imagine Goku eating a samosa"
   Image Generated: Picture of a sandwich
   Issue: Image doesn't match mnemonic
   ```

5. **Culturally Inappropriate Content**
   ```
   Word: "jihad" (Arabic: struggle)
   Mnemonic: Uses violent imagery
   Issue: Potentially offensive interpretation
   ```

### Impact

- **User Trust Erosion:** Bad content damages credibility
- **Learning Effectiveness:** Incorrect examples teach wrong patterns
- **Waste:** Bad content gets cached and reused
- **Manual Review Burden:** Would require human QA team to scale

---

## Agent Capabilities

### 1. Translation Quality Validation

**Checks:**
- ✅ Translation matches source word meaning
- ✅ Appropriate for context (formal vs. informal)
- ✅ Handles false friends and homophones
- ✅ Multiple translations are distinct (not synonyms)
- ✅ Cultural appropriateness

**Implementation:**
```java
public QualityScore validateTranslation(String sourceWord, String sourceLanguage,
                                       Set<String> translations, String targetLanguage) {
    Map<String, Double> dimensionScores = new HashMap<>();

    // 1. Accuracy: Reverse translate and compare
    double accuracyScore = checkAccuracy(sourceWord, translations, sourceLanguage, targetLanguage);
    dimensionScores.put("accuracy", accuracyScore);

    // 2. Completeness: Check if major meanings covered
    double completenessScore = checkCompleteness(sourceWord, translations, sourceLanguage);
    dimensionScores.put("completeness", completenessScore);

    // 3. Appropriateness: Check register/formality
    double appropriatenessScore = checkAppropriateness(translations, targetLanguage);
    dimensionScores.put("appropriateness", appropriatenessScore);

    // 4. Cultural sensitivity
    double culturalScore = checkCulturalSensitivity(sourceWord, translations);
    dimensionScores.put("cultural_sensitivity", culturalScore);

    // Overall score (weighted average)
    double overall = dimensionScores.values().stream()
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(0.0);

    return QualityScore.builder()
        .score(overall)
        .dimensions(dimensionScores)
        .passed(overall >= 0.7)  // 70% threshold
        .build();
}

private double checkAccuracy(String source, Set<String> translations,
                             String sourceLang, String targetLang) {
    // Reverse translate each translation back to source language
    int accurateCount = 0;
    for (String translation : translations) {
        Set<String> reverseTranslations = translationService.translate(
            translation, targetLang, sourceLang
        );

        // Check if source word is in reverse translations (with fuzzy matching)
        boolean matches = reverseTranslations.stream()
            .anyMatch(rt -> areSimilar(rt, source));

        if (matches) accurateCount++;
    }

    return (double) accurateCount / translations.size();
}
```

---

### 2. Sentence Quality Validation

**Checks:**
- ✅ Sentence actually contains the target word
- ✅ Grammatically correct
- ✅ Natural and idiomatic
- ✅ Appropriate difficulty level
- ✅ Useful for learning context

**Implementation:**
```java
public QualityScore validateSentence(SentenceData sentence, String targetWord,
                                     String sourceLanguage) {
    Map<String, Double> dimensionScores = new HashMap<>();

    // 1. Word Usage: Target word must be present
    boolean containsWord = sentence.getSentenceSource().toLowerCase()
        .contains(targetWord.toLowerCase());
    dimensionScores.put("word_usage", containsWord ? 1.0 : 0.0);

    // 2. Grammar: Use LLM to check grammar
    double grammarScore = checkGrammar(sentence.getSentenceSource(), sourceLanguage);
    dimensionScores.put("grammar", grammarScore);

    // 3. Naturalness: Check if sentence sounds natural
    double naturalnessScore = checkNaturalness(sentence.getSentenceSource(), sourceLanguage);
    dimensionScores.put("naturalness", naturalnessScore);

    // 4. Translation Quality: Sentence translation should be accurate
    double translationScore = validateSentenceTranslation(
        sentence.getSentenceSource(),
        sentence.getSentenceTarget(),
        sourceLanguage
    );
    dimensionScores.put("translation_quality", translationScore);

    // 5. Transliteration Accuracy: Check if transliteration matches pronunciation
    double transliterationScore = validateTransliteration(
        sentence.getSentenceSource(),
        sentence.getSentenceTransliteration(),
        sourceLanguage
    );
    dimensionScores.put("transliteration_accuracy", transliterationScore);

    double overall = dimensionScores.values().stream()
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(0.0);

    return QualityScore.builder()
        .score(overall)
        .dimensions(dimensionScores)
        .passed(overall >= 0.75)  // Higher threshold for sentences
        .build();
}

private double checkGrammar(String sentence, String language) {
    // Use LLM to assess grammar
    String prompt = String.format(
        "Evaluate the grammatical correctness of this %s sentence. " +
        "Respond with a score from 0.0 to 1.0 where 1.0 is perfect grammar.\n\n" +
        "Sentence: %s\n\n" +
        "Respond with just the score as a decimal number.",
        getLanguageName(language), sentence
    );

    String response = llmService.query(prompt, LLMModel.FAST_CHEAP);
    return Double.parseDouble(response.trim());
}
```

---

### 3. Mnemonic Quality Validation

**Checks:**
- ✅ Mnemonic relates to word pronunciation
- ✅ Mnemonic includes memory anchor (character/context)
- ✅ Mnemonic connects to translation meaning
- ✅ Image prompt describes mnemonic scene
- ✅ Not offensive or inappropriate

**Implementation:**
```java
public QualityScore validateMnemonic(MnemonicData mnemonic, String sourceWord,
                                     String translation) {
    Map<String, Double> dimensionScores = new HashMap<>();

    // 1. Phonetic Connection: Mnemonic keyword should sound like source word
    double phoneticScore = checkPhoneticSimilarity(
        mnemonic.getMnemonicKeyword(),
        sourceWord
    );
    dimensionScores.put("phonetic_connection", phoneticScore);

    // 2. Semantic Connection: Mnemonic should relate to translation meaning
    double semanticScore = checkSemanticRelevance(
        mnemonic.getMnemonicSentence(),
        translation
    );
    dimensionScores.put("semantic_connection", semanticScore);

    // 3. Memorability: Mnemonic should be vivid and specific
    double memorabilityScore = checkMemorability(mnemonic.getMnemonicSentence());
    dimensionScores.put("memorability", memorabilityScore);

    // 4. Image Alignment: Image prompt should describe mnemonic scene
    double alignmentScore = checkMnemonicImageAlignment(
        mnemonic.getMnemonicSentence(),
        mnemonic.getImagePrompt()
    );
    dimensionScores.put("image_alignment", alignmentScore);

    // 5. Appropriateness: No offensive content
    double appropriatenessScore = checkContentAppropriateness(
        mnemonic.getMnemonicSentence() + " " + mnemonic.getImagePrompt()
    );
    dimensionScores.put("appropriateness", appropriatenessScore);

    double overall = dimensionScores.values().stream()
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(0.0);

    return QualityScore.builder()
        .score(overall)
        .dimensions(dimensionScores)
        .passed(overall >= 0.70)
        .build();
}
```

---

### 4. Image Quality Validation

**Checks:**
- ✅ Image visually represents the mnemonic
- ✅ Image is clear and high quality
- ✅ No inappropriate content (NSFW, offensive)
- ✅ Relevant elements are visible

**Implementation:**
```java
public QualityScore validateImage(String imageUrl, String mnemonicSentence) {
    Map<String, Double> dimensionScores = new HashMap<>();

    // 1. Image Analysis: Use vision AI to analyze image
    VisionAnalysisResult analysis = visionService.analyzeImage(imageUrl);

    // 2. Safety Check: No NSFW or offensive content
    double safetyScore = analysis.getSafetyScore();
    dimensionScores.put("safety", safetyScore);

    // 3. Quality Check: Image clarity and resolution
    double qualityScore = analysis.getQualityScore();
    dimensionScores.put("quality", qualityScore);

    // 4. Relevance Check: Image content matches mnemonic
    String imageDescription = analysis.getDescription();
    double relevanceScore = checkImageRelevance(imageDescription, mnemonicSentence);
    dimensionScores.put("relevance", relevanceScore);

    double overall = dimensionScores.values().stream()
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(0.0);

    return QualityScore.builder()
        .score(overall)
        .dimensions(dimensionScores)
        .passed(overall >= 0.75 && safetyScore >= 0.9)  // High safety threshold
        .build();
}
```

---

### 5. Quality Gate Decision

**What it does:** Decides whether content passes quality standards

**Decision Tree:**
```java
public QualityGateDecision evaluate(QualityScore score, ContentType contentType) {
    // Hard failures (always reject)
    if (score.getDimensions().get("safety") < 0.9) {
        return QualityGateDecision.reject("Safety score too low");
    }

    if (contentType == ContentType.SENTENCE &&
        score.getDimensions().get("word_usage") < 1.0) {
        return QualityGateDecision.reject("Sentence doesn't use target word");
    }

    // Soft failures (reject if below threshold)
    if (score.getScore() < 0.7) {
        return QualityGateDecision.reject(
            String.format("Overall quality score %.2f below threshold 0.70", score.getScore())
        );
    }

    // Flag for review (pass but alert)
    if (score.getScore() >= 0.7 && score.getScore() < 0.85) {
        return QualityGateDecision.passWithWarning(
            "Quality score acceptable but could be improved",
            score
        );
    }

    // Pass
    return QualityGateDecision.pass(score);
}
```

---

## Technical Architecture

### Class Structure

```
src/main/java/com/raidrin/eme/agent/quality/
├── QualityAssuranceAgent.java               # Main agent
├── validator/
│   ├── TranslationValidator.java
│   ├── SentenceValidator.java
│   ├── MnemonicValidator.java
│   └── ImageValidator.java
├── checker/
│   ├── GrammarChecker.java
│   ├── PhoneticSimilarityChecker.java
│   ├── SemanticRelevanceChecker.java
│   └── ContentAppropriatenessChecker.java
├── service/
│   ├── LLMQualityService.java               # LLM calls for quality checks
│   └── VisionAnalysisService.java           # Image analysis
├── model/
│   ├── QualityScore.java
│   ├── QualityGateDecision.java
│   ├── ValidationResult.java
│   └── ContentType.java
└── repository/
    └── QualityScoreRepository.java
```

### Main Implementation

**QualityAssuranceAgent.java**
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityAssuranceAgent implements AgentInterface {
    private final TranslationValidator translationValidator;
    private final SentenceValidator sentenceValidator;
    private final MnemonicValidator mnemonicValidator;
    private final ImageValidator imageValidator;
    private final QualityScoreRepository qualityScoreRepository;
    private final AgentMetricsService metricsService;
    private final AgentProperties agentProperties;

    @Override
    public String getName() {
        return "QualityAssuranceAgent";
    }

    /**
     * Validate translation quality
     */
    public ValidationResult validateTranslation(String sourceWord, String sourceLanguage,
                                               Set<String> translations, String targetLanguage) {
        if (!agentProperties.getQualityAssurance().isEnabled()) {
            return ValidationResult.skipped("QA Agent disabled");
        }

        long startTime = System.currentTimeMillis();

        QualityScore score = translationValidator.validate(
            sourceWord, sourceLanguage, translations, targetLanguage
        );

        QualityGateDecision decision = evaluateQualityGate(score, ContentType.TRANSLATION);

        // Save score
        saveQualityScore("translation", sourceWord, score);

        // Record metrics
        recordMetrics("translation", score, decision, startTime);

        return ValidationResult.from(decision, score);
    }

    /**
     * Validate sentence quality
     */
    public ValidationResult validateSentence(SentenceData sentence, String targetWord,
                                            String sourceLanguage, String targetLanguage) {
        if (!agentProperties.getQualityAssurance().isEnabled()) {
            return ValidationResult.skipped("QA Agent disabled");
        }

        long startTime = System.currentTimeMillis();

        QualityScore score = sentenceValidator.validate(
            sentence, targetWord, sourceLanguage, targetLanguage
        );

        QualityGateDecision decision = evaluateQualityGate(score, ContentType.SENTENCE);

        saveQualityScore("sentence", targetWord, score);
        recordMetrics("sentence", score, decision, startTime);

        return ValidationResult.from(decision, score);
    }

    /**
     * Validate mnemonic quality
     */
    public ValidationResult validateMnemonic(MnemonicData mnemonic, String sourceWord,
                                            String translation) {
        if (!agentProperties.getQualityAssurance().isEnabled()) {
            return ValidationResult.skipped("QA Agent disabled");
        }

        long startTime = System.currentTimeMillis();

        QualityScore score = mnemonicValidator.validate(mnemonic, sourceWord, translation);

        QualityGateDecision decision = evaluateQualityGate(score, ContentType.MNEMONIC);

        saveQualityScore("mnemonic", sourceWord, score);
        recordMetrics("mnemonic", score, decision, startTime);

        return ValidationResult.from(decision, score);
    }

    /**
     * Validate image quality
     */
    public ValidationResult validateImage(String imageUrl, String mnemonicSentence) {
        if (!agentProperties.getQualityAssurance().isEnabled()) {
            return ValidationResult.skipped("QA Agent disabled");
        }

        long startTime = System.currentTimeMillis();

        QualityScore score = imageValidator.validate(imageUrl, mnemonicSentence);

        QualityGateDecision decision = evaluateQualityGate(score, ContentType.IMAGE);

        saveQualityScore("image", imageUrl, score);
        recordMetrics("image", score, decision, startTime);

        return ValidationResult.from(decision, score);
    }

    private QualityGateDecision evaluateQualityGate(QualityScore score, ContentType type) {
        double minScore = agentProperties.getQualityAssurance().getMinScore();
        boolean strictMode = agentProperties.getQualityAssurance().isStrictMode();

        // Safety checks (always strict)
        Double safetyScore = score.getDimensions().get("safety");
        if (safetyScore != null && safetyScore < 0.9) {
            return QualityGateDecision.reject("Safety threshold not met");
        }

        // Type-specific hard requirements
        if (type == ContentType.SENTENCE) {
            Double wordUsage = score.getDimensions().get("word_usage");
            if (wordUsage != null && wordUsage < 1.0) {
                return QualityGateDecision.reject("Sentence doesn't use target word");
            }
        }

        // Overall quality threshold
        if (score.getScore() < minScore) {
            if (strictMode) {
                return QualityGateDecision.reject(
                    String.format("Quality score %.2f below threshold %.2f",
                        score.getScore(), minScore)
                );
            } else {
                return QualityGateDecision.passWithWarning(
                    "Quality below threshold but passing in non-strict mode", score
                );
            }
        }

        // Warning for mediocre quality
        if (score.getScore() >= minScore && score.getScore() < 0.85) {
            return QualityGateDecision.passWithWarning(
                "Quality acceptable but could be improved", score
            );
        }

        return QualityGateDecision.pass(score);
    }

    private void saveQualityScore(String entityType, String entityKey, QualityScore score) {
        QualityScoreEntity entity = QualityScoreEntity.builder()
            .entityType(entityType)
            .entityKey(entityKey)
            .score(score.getScore())
            .dimensions(toJson(score.getDimensions()))
            .validatedBy(getName())
            .createdAt(LocalDateTime.now())
            .build();

        qualityScoreRepository.save(entity);
    }

    private void recordMetrics(String contentType, QualityScore score,
                               QualityGateDecision decision, long startTime) {
        long duration = System.currentTimeMillis() - startTime;

        metricsService.recordAgentExecution(getName(), Duration.ofMillis(duration), "success");

        metricsService.recordGauge("agent.quality.score",
            score.getScore(),
            "content_type", contentType,
            "outcome", decision.getOutcome().name()
        );

        metricsService.incrementCounter("agent.quality.validations",
            "content_type", contentType,
            "outcome", decision.getOutcome().name()
        );
    }
}
```

---

## Database Schema Changes

### Flyway Migration: V11__create_quality_scores_table.sql

```sql
CREATE TABLE quality_scores (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,   -- 'translation', 'sentence', 'mnemonic', 'image'
    entity_key VARCHAR(500) NOT NULL,   -- Word or identifier
    score DECIMAL(4,3) NOT NULL,        -- 0.000 to 1.000
    dimensions JSONB,                    -- Individual dimension scores
    validated_by VARCHAR(100),           -- 'QualityAssuranceAgent' or 'human'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CHECK (score >= 0.0 AND score <= 1.0)
);

CREATE INDEX idx_quality_scores_entity_type
    ON quality_scores(entity_type);

CREATE INDEX idx_quality_scores_entity_key
    ON quality_scores(entity_key);

CREATE INDEX idx_quality_scores_score
    ON quality_scores(score DESC);

CREATE INDEX idx_quality_scores_created_at
    ON quality_scores(created_at DESC);

-- GIN index for dimension queries
CREATE INDEX idx_quality_scores_dimensions
    ON quality_scores USING GIN (dimensions);

COMMENT ON TABLE quality_scores IS 'Quality validation scores for AI-generated content';
COMMENT ON COLUMN quality_scores.dimensions IS 'JSON object with individual quality dimension scores (accuracy, grammar, safety, etc.)';
```

---

## Integration with Existing Services

### Modified OpenAITranslationService

```java
@Service
@RequiredArgsConstructor
public class OpenAITranslationService implements TranslationService {
    private final QualityAssuranceAgent qualityAgent;
    private final TranslationStorageService storageService;

    @Override
    public Set<String> translate(String text, String sourceLanguage, String targetLanguage) {
        // Check cache first
        Optional<TranslationEntity> cached =
            storageService.findTranslation(text, sourceLanguage, targetLanguage);
        if (cached.isPresent()) {
            return cached.get().getTranslations();
        }

        // Call OpenAI
        Set<String> translations = callOpenAI(text, sourceLanguage, targetLanguage);

        // VALIDATE WITH QA AGENT
        ValidationResult validation = qualityAgent.validateTranslation(
            text, sourceLanguage, translations, targetLanguage
        );

        if (!validation.isPassed()) {
            log.warn("Translation failed quality check",
                "word", text,
                "score", validation.getScore(),
                "reason", validation.getReason());

            // Option 1: Retry with different prompt
            translations = retryWithDifferentApproach(text, sourceLanguage, targetLanguage);

            // Re-validate
            validation = qualityAgent.validateTranslation(
                text, sourceLanguage, translations, targetLanguage
            );

            if (!validation.isPassed()) {
                // Option 2: Fallback to different provider
                translations = fallbackProvider.translate(text, sourceLanguage, targetLanguage);
            }
        }

        // Cache only if quality passed
        if (validation.isPassed()) {
            storageService.saveTranslation(text, sourceLanguage, targetLanguage, translations);
        }

        return translations;
    }
}
```

---

## Testing Strategy

### Unit Tests

```java
@SpringBootTest
class QualityAssuranceAgentTest {

    @Autowired
    private QualityAssuranceAgent agent;

    @Test
    void shouldRejectInaccurateTranslation() {
        Set<String> badTranslations = Set.of("embarrassed");  // Wrong for "embarazada"

        ValidationResult result = agent.validateTranslation(
            "embarazada", "es", badTranslations, "en"
        );

        assertFalse(result.isPassed());
        assertTrue(result.getScore() < 0.7);
    }

    @Test
    void shouldRejectSentenceNotUsingWord() {
        SentenceData sentence = SentenceData.builder()
            .sentenceSource("Hello, how are you?")  // Doesn't use "namaste"
            .build();

        ValidationResult result = agent.validateSentence(
            sentence, "namaste", "hi", "en"
        );

        assertFalse(result.isPassed());
        assertEquals(0.0, result.getScore().getDimensions().get("word_usage"));
    }

    @Test
    void shouldPassHighQualityContent() {
        Set<String> goodTranslations = Set.of("pregnant", "expecting");

        ValidationResult result = agent.validateTranslation(
            "embarazada", "es", goodTranslations, "en"
        );

        assertTrue(result.isPassed());
        assertTrue(result.getScore() >= 0.7);
    }
}
```

---

## Metrics & Success Criteria

### Key Metrics

```
# Quality scores distribution
agent_quality_score_distribution{content_type="translation",bucket="0.9-1.0"} 450
agent_quality_score_distribution{content_type="translation",bucket="0.7-0.9"} 120
agent_quality_score_distribution{content_type="translation",bucket="0.0-0.7"} 30

# Rejection rate
agent_quality_rejection_rate{content_type="sentence"} 0.15

# Average quality score
agent_quality_score_average{content_type="mnemonic"} 0.82
```

### Success Criteria

- ✅ Average quality score >0.80 across all content types
- ✅ Rejection rate <20% (indicates AI is mostly good, QA catches edge cases)
- ✅ Zero unsafe content reaching users
- ✅ User-reported quality issues decrease by >80%

---

[Next: Multi-Modal Coherence Agent →](05-agent-multimodal-coherence.md)

[← Back to Main Document](../../AGENTIC_AI_INTEGRATION.md)
