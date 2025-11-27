# Agent #5: Personalized Learning Path Agent

[← Back to Main Document](../../AGENTIC_AI_INTEGRATION.md) | [← Previous: Cost Optimization](06-agent-cost-optimization.md) | [Next: Implementation Roadmap →](08-implementation-roadmap.md)

---

## Executive Summary

The **Personalized Learning Path Agent** analyzes learner performance from Anki data, identifies weak areas, and adapts content difficulty and recommendations to each user's unique learning patterns—accelerating language acquisition and improving retention.

**Priority:** LOW (Phase 4 - future enhancement)
**Estimated Effort:** 80 hours
**Expected ROI:** Premium tier revenue opportunity ($15/month premium × 20% conversion = $3,000/month on 1,000 users)

---

## Problem Statement

### Current State: One-Size-Fits-All Content

**Issues:**
- Same sentence complexity for beginners and advanced learners
- No tracking of individual learner progress
- Can't identify which phonetic patterns a user struggles with
- No personalized word recommendations

**Example:**
```
User A (Beginner):
- Struggles with Hindi consonant clusters
- Gets same complex sentence as User B

User B (Advanced):
- Finds simple sentences boring
- Gets same basic content as User A

Result: Both users suboptimally served
```

---

## Agent Capabilities

### 1. Learner Profile Construction

**Tracks:**
- Vocabulary mastery levels per word
- Weak phonetic patterns
- Preferred learning modalities (visual, auditory, mnemonic)
- Session frequency and engagement
- Performance trends over time

**Implementation:**
```java
@Entity
@Table(name = "learner_profiles")
public class LearnerProfileEntity {
    private Long userId;
    private String languagePair;  // "en-hi", "en-es"
    private String difficultyLevel;  // "beginner", "intermediate", "advanced"

    @Column(columnDefinition = "jsonb")
    private String weakPhonemes;  // {"ṇ": 0.45, "ṭ": 0.52, "r̥": 0.38}

    @Column(columnDefinition = "jsonb")
    private String preferredCharacters;  // ["Goku", "Naruto", "Messi"]

    @Column(columnDefinition = "jsonb")
    private String performanceMetrics;  // {
        // "words_learned": 150,
        // "average_retention": 0.75,
        // "session_frequency_days": 3.5,
        // "average_session_duration_mins": 12
    // }

    @Column(columnDefinition = "jsonb")
    private String masteryLevels;  // {
        // "namaste": {"level": "mastered", "reviews": 15, "success_rate": 0.95},
        // "samosa": {"level": "learning", "reviews": 3, "success_rate": 0.67}
    // }
}

@Service
@RequiredArgsConstructor
public class LearnerProfileService {
    public LearnerProfile buildProfile(Long userId, String languagePair) {
        // Fetch Anki performance data
        List<AnkiReview> reviews = ankiService.getReviewHistory(userId, languagePair);

        // Analyze performance
        Map<String, Double> phonemePerformance = analyzePhonemePerformance(reviews);
        Map<String, MasteryLevel> wordMastery = calculateWordMastery(reviews);
        DifficultyLevel difficulty = inferDifficultyLevel(wordMastery);

        return LearnerProfile.builder()
            .userId(userId)
            .languagePair(languagePair)
            .difficultyLevel(difficulty)
            .weakPhonemes(identifyWeakPhonemes(phonemePerformance))
            .masteryLevels(wordMastery)
            .build();
    }

    private Map<String, Double> analyzePhonemePerformance(List<AnkiReview> reviews) {
        Map<String, List<Double>> phonemeScores = new HashMap<>();

        for (AnkiReview review : reviews) {
            String word = review.getWord();
            List<String> phonemes = extractPhonemes(word);

            for (String phoneme : phonemes) {
                phonemeScores.computeIfAbsent(phoneme, k -> new ArrayList<>())
                    .add(review.getSuccessRate());
            }
        }

        // Calculate average performance per phoneme
        return phonemeScores.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.5)
            ));
    }

    private List<String> identifyWeakPhonemes(Map<String, Double> phonemePerformance) {
        return phonemePerformance.entrySet().stream()
            .filter(e -> e.getValue() < 0.6)  // <60% success rate
            .sorted(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .limit(5)
            .collect(Collectors.toList());
    }
}
```

---

### 2. Adaptive Difficulty Adjustment

**Strategy:** Adjust sentence complexity based on current mastery level

**Implementation:**
```java
public SentenceComplexityLevel determineSentenceComplexity(LearnerProfile profile, String word) {
    // Factors influencing complexity
    double overallMastery = profile.getAverageMasteryLevel();
    int wordCount = profile.getMasteryLevels().size();
    double wordSpecificMastery = profile.getMasteryLevel(word);

    // Beginners: simple sentences (5-8 words, present tense, common vocab)
    if (overallMastery < 0.5 || wordCount < 20) {
        return SentenceComplexityLevel.SIMPLE;
    }

    // Intermediate: medium complexity (8-12 words, past/future tense, some idioms)
    if (overallMastery < 0.75 || wordCount < 100) {
        return SentenceComplexityLevel.MEDIUM;
    }

    // Advanced: complex sentences (12+ words, subjunctive, idioms, cultural references)
    return SentenceComplexityLevel.COMPLEX;
}

public String generatePersonalizedSentence(String word, String sourceLang,
                                           LearnerProfile profile) {
    SentenceComplexityLevel complexity = determineSentenceComplexity(profile, word);

    String prompt = buildSentencePrompt(word, sourceLang, complexity);

    // Include user's weak phonemes in sentence for practice
    if (!profile.getWeakPhonemes().isEmpty()) {
        prompt += String.format(
            "\nIf possible, include words with these sounds: %s",
            String.join(", ", profile.getWeakPhonemes())
        );
    }

    return sentenceGenerationService.generate(prompt);
}
```

---

### 3. Personalized Word Recommendations

**Strategy:** Suggest next words based on learning patterns

**Implementation:**
```java
public List<WordRecommendation> recommendNextWords(LearnerProfile profile, int count) {
    List<WordRecommendation> recommendations = new ArrayList<>();

    // 1. Words with weak phonemes for targeted practice
    List<String> weakPhonemeWords = findWordsWithPhonemes(
        profile.getWeakPhonemes(),
        profile.getLanguagePair()
    );
    recommendations.addAll(weakPhonemeWords.stream()
        .limit(count / 3)
        .map(w -> WordRecommendation.builder()
            .word(w)
            .reason("Practice weak sound patterns")
            .priority(Priority.HIGH)
            .build())
        .collect(Collectors.toList()));

    // 2. Vocabulary clusters (related words)
    List<String> masteredWords = profile.getMasteredWords();
    if (!masteredWords.isEmpty()) {
        String seedWord = masteredWords.get(new Random().nextInt(masteredWords.size()));
        List<String> relatedWords = findRelatedWords(seedWord, profile.getLanguagePair());

        recommendations.addAll(relatedWords.stream()
            .limit(count / 3)
            .map(w -> WordRecommendation.builder()
                .word(w)
                .reason(String.format("Related to '%s' you already know", seedWord))
                .priority(Priority.MEDIUM)
                .build())
            .collect(Collectors.toList()));
    }

    // 3. Frequency-based (common useful words)
    List<String> commonWords = getCommonWords(
        profile.getLanguagePair(),
        profile.getDifficultyLevel()
    );
    recommendations.addAll(commonWords.stream()
        .filter(w -> !profile.hasLearnedWord(w))
        .limit(count / 3)
        .map(w -> WordRecommendation.builder()
            .word(w)
            .reason("High-frequency useful word")
            .priority(Priority.LOW)
            .build())
        .collect(Collectors.toList()));

    return recommendations;
}
```

---

### 4. Character Preference Learning

**Strategy:** Track which mnemonic characters lead to best retention

**Implementation:**
```java
public CharacterGuideEntity selectOptimalCharacter(String word, LearnerProfile profile) {
    String startSound = extractStartSound(word);

    // Get all characters for this sound
    List<CharacterGuideEntity> candidates =
        characterGuideRepository.findByLanguageAndStartSound(
            profile.getSourceLanguage(),
            startSound
        );

    if (candidates.isEmpty()) {
        return null;  // No character available
    }

    // If user has character preferences based on past success
    Map<String, Double> characterEffectiveness = profile.getCharacterEffectiveness();
    if (characterEffectiveness != null && !characterEffectiveness.isEmpty()) {
        return candidates.stream()
            .max(Comparator.comparing(c ->
                characterEffectiveness.getOrDefault(c.getCharacterName(), 0.5)))
            .orElse(candidates.get(0));
    }

    // Default: random selection, will learn preference over time
    return candidates.get(new Random().nextInt(candidates.size()));
}

public void updateCharacterEffectiveness(Long userId, String character,
                                        double retentionScore) {
    LearnerProfileEntity profile = profileRepository.findByUserId(userId);

    Map<String, Double> effectiveness = profile.getCharacterEffectiveness();
    double currentScore = effectiveness.getOrDefault(character, 0.5);

    // Moving average (70% historical, 30% new)
    double updatedScore = currentScore * 0.7 + retentionScore * 0.3;

    effectiveness.put(character, updatedScore);
    profile.setCharacterEffectiveness(effectiveness);
    profileRepository.save(profile);
}
```

---

### 5. Learning Velocity Prediction

**Strategy:** Predict when user will master a word, optimize review scheduling

**Implementation:**
```java
public LocalDateTime predictMasteryDate(Long userId, String word) {
    LearnerProfile profile = profileService.getProfile(userId);
    List<AnkiReview> reviews = ankiService.getReviewHistory(userId, word);

    if (reviews.size() < 2) {
        // Not enough data, use average
        return LocalDateTime.now().plusDays(14);
    }

    // Calculate learning velocity (improvement per review)
    double initialScore = reviews.get(0).getSuccessRate();
    double latestScore = reviews.get(reviews.size() - 1).getSuccessRate();
    double improvement = (latestScore - initialScore) / reviews.size();

    // Mastery threshold: 0.95 (95% success rate)
    double remaining = 0.95 - latestScore;
    int reviewsNeeded = (int) Math.ceil(remaining / improvement);

    // Average days between reviews
    double avgInterval = calculateAverageReviewInterval(reviews);

    int daysToMastery = (int) (reviewsNeeded * avgInterval);

    return LocalDateTime.now().plusDays(daysToMastery);
}
```

---

## Technical Architecture

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalizedLearningPathAgent implements AgentInterface {
    private final LearnerProfileService profileService;
    private final AnkiIntegrationService ankiService;
    private final WordRecommendationEngine recommendationEngine;
    private final AdaptiveDifficultyService difficultyService;

    @Override
    public String getName() {
        return "PersonalizedLearningPathAgent";
    }

    /**
     * Generate personalized content for user
     */
    public PersonalizedContent generatePersonalizedContent(Long userId, String word,
                                                          String sourceLanguage,
                                                          String targetLanguage) {
        // Build/update learner profile
        LearnerProfile profile = profileService.getOrCreateProfile(userId,
            sourceLanguage + "-" + targetLanguage);

        // Determine sentence complexity
        SentenceComplexityLevel complexity =
            difficultyService.determineSentenceComplexity(profile, word);

        // Generate sentence with appropriate complexity
        SentenceData sentence = generateAdaptiveSentence(word, sourceLanguage, complexity);

        // Select optimal character for mnemonic
        CharacterGuideEntity character = selectOptimalCharacter(word, profile);

        // Generate mnemonic using preferred character
        MnemonicData mnemonic = generatePersonalizedMnemonic(
            word, sentence.getTranslation(), character, profile
        );

        return PersonalizedContent.builder()
            .sentence(sentence)
            .mnemonic(mnemonic)
            .recommendedCharacter(character)
            .complexity(complexity)
            .reasoning(String.format(
                "Complexity: %s based on %d mastered words and %.2f average mastery",
                complexity, profile.getMasteredWordsCount(), profile.getAverageMasteryLevel()
            ))
            .build();
    }

    /**
     * Recommend next words for user to learn
     */
    public List<WordRecommendation> getPersonalizedRecommendations(Long userId,
                                                                   String languagePair,
                                                                   int count) {
        LearnerProfile profile = profileService.getOrCreateProfile(userId, languagePair);

        return recommendationEngine.recommendNextWords(profile, count);
    }

    /**
     * Update profile based on Anki performance
     */
    @Scheduled(cron = "0 0 2 * * *")  // Daily at 2 AM
    public void syncAnkiPerformance() {
        List<LearnerProfileEntity> profiles = profileRepository.findAll();

        for (LearnerProfileEntity profile : profiles) {
            try {
                // Fetch latest Anki data
                List<AnkiReview> reviews = ankiService.getReviewHistory(
                    profile.getUserId(),
                    profile.getLanguagePair()
                );

                // Update profile
                profileService.updateFromReviews(profile, reviews);

            } catch (Exception e) {
                log.error("Failed to sync Anki performance",
                    "user_id", profile.getUserId(), "error", e.getMessage());
            }
        }
    }
}
```

---

## Database Schema

### V14__create_learner_profiles_table.sql

```sql
CREATE TABLE learner_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    language_pair VARCHAR(50) NOT NULL,  -- e.g., "en-hi", "en-es"
    difficulty_level VARCHAR(20) NOT NULL,  -- "beginner", "intermediate", "advanced"

    weak_phonemes JSONB,  -- {"ṇ": 0.45, "ṭ": 0.52}
    preferred_characters JSONB,  -- ["Goku", "Naruto"]
    performance_metrics JSONB,  -- {words_learned: 150, avg_retention: 0.75}
    mastery_levels JSONB,  -- {"namaste": {level: "mastered", reviews: 15}}
    character_effectiveness JSONB,  -- {"Goku": 0.85, "Naruto": 0.72}

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(user_id, language_pair)
);

CREATE INDEX idx_learner_profiles_user_id ON learner_profiles(user_id);
CREATE INDEX idx_learner_profiles_language_pair ON learner_profiles(language_pair);

CREATE TABLE word_recommendations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    word VARCHAR(255) NOT NULL,
    language_pair VARCHAR(50) NOT NULL,
    reason TEXT,
    priority VARCHAR(20),  -- "high", "medium", "low"
    recommended_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    acted_upon BOOLEAN DEFAULT FALSE,
    acted_at TIMESTAMP
);

CREATE INDEX idx_word_recommendations_user_id ON word_recommendations(user_id);
CREATE INDEX idx_word_recommendations_acted_upon ON word_recommendations(acted_upon);
```

---

## Integration: Anki Connect

**Fetch review history:**
```java
@Service
@RequiredArgsConstructor
public class AnkiIntegrationService {
    private final RestTemplate restTemplate;

    @Value("${anki.connect.url}")
    private String ankiConnectUrl;

    public List<AnkiReview> getReviewHistory(Long userId, String deck) {
        // Query Anki Connect API
        Map<String, Object> request = Map.of(
            "action", "findCards",
            "params", Map.of("query", "deck:" + deck)
        );

        ResponseEntity<AnkiResponse> response = restTemplate.postForEntity(
            ankiConnectUrl, request, AnkiResponse.class
        );

        List<Long> cardIds = response.getBody().getResult();

        // Get review stats for each card
        return cardIds.stream()
            .map(this::getCardReviewStats)
            .collect(Collectors.toList());
    }

    private AnkiReview getCardReviewStats(Long cardId) {
        Map<String, Object> request = Map.of(
            "action", "getReviewsOfCard",
            "params", Map.of("card", cardId)
        );

        ResponseEntity<AnkiReviewResponse> response = restTemplate.postForEntity(
            ankiConnectUrl, request, AnkiReviewResponse.class
        );

        List<Review> reviews = response.getBody().getResult();

        // Calculate success rate
        long successfulReviews = reviews.stream()
            .filter(r -> r.getEase() >= 3)  // "Good" or "Easy"
            .count();

        double successRate = (double) successfulReviews / reviews.size();

        return AnkiReview.builder()
            .cardId(cardId)
            .word(extractWordFromCard(cardId))
            .reviewCount(reviews.size())
            .successRate(successRate)
            .lastReview(reviews.get(reviews.size() - 1).getTimestamp())
            .build();
    }
}
```

---

## Success Metrics

- ✅ Personalized sentence complexity matches user level 95% of the time
- ✅ User retention rate increases by 20%
- ✅ Learning velocity (words per week) increases by 30%
- ✅ Premium tier conversion >15% (personalization as premium feature)

**Premium Tier Revenue:**
```
1,000 users × 15% conversion × $15/month premium = $2,250/month
Annual recurring revenue: $27,000
```

---

[Next: Implementation Roadmap →](08-implementation-roadmap.md)

[← Back to Main Document](../../AGENTIC_AI_INTEGRATION.md)
