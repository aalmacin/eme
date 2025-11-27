# Current State Analysis: EME Application Architecture

[← Back to Main Document](../../AGENTIC_AI_INTEGRATION.md) | [← Previous: Executive Summary](01-executive-summary.md)

---

## Purpose of This Document

This document provides a comprehensive technical analysis of the EME application's current architecture, identifying strengths to leverage and gaps that agentic AI will address.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Service Layer Analysis](#service-layer-analysis)
3. [Data Model](#data-model)
4. [AI Integration Patterns](#ai-integration-patterns)
5. [Async Processing Architecture](#async-processing-architecture)
6. [Configuration Management](#configuration-management)
7. [Current Limitations](#current-limitations)
8. [Agent Integration Points](#agent-integration-points)

---

## Architecture Overview

### Technology Stack

**Backend Framework:**
```
Spring Boot 3.0.6
├── Spring Web MVC (REST + Controllers)
├── Spring Data JPA (Hibernate + PostgreSQL)
├── Spring Async (@Async with ThreadPoolTaskExecutor)
└── Flyway (Database migrations)
```

**Java Version:** 17 (LTS)
**Build Tool:** Gradle 7.x+ with Kotlin DSL

**Database:**
```
PostgreSQL 15
├── Local development: localhost:5432
├── Database: eme_cache
└── Migration tool: Flyway
```

**External APIs:**
```
AI/ML Services:
├── OpenAI API (gpt-4o-mini for translation, sentences, mnemonics)
├── Leonardo AI (image generation)
├── OpenAI GPT Image 1 3 (alternative image generation)
└── Google Cloud Text-to-Speech (audio generation)

Storage:
└── Google Cloud Storage (image hosting)

Integration:
└── Anki Connect API (flashcard creation)
```

**Frontend:**
```
Template Engine: Thymeleaf
JavaScript: Vue.js 2, jQuery 3.6.0
CSS: Bootstrap 5.1.3
```

---

### Layered Architecture

```
┌─────────────────────────────────────────┐
│          Web Layer                      │
│  (Controllers, REST endpoints)          │
│  - TranslatorController                 │
│  - SessionController                    │
│  - StorageController                    │
└────────────────┬────────────────────────┘
                 │
┌────────────────▼────────────────────────┐
│         Service Layer                   │
│  (Business logic, orchestration)        │
│  - SessionOrchestrationService          │
│  - OpenAITranslationService             │
│  - SentenceGenerationService            │
│  - MnemonicGenerationService            │
│  - AsyncImageGenerationService          │
│  - AsyncAudioGenerationService          │
└────────────────┬────────────────────────┘
                 │
┌────────────────▼────────────────────────┐
│      Repository Layer                   │
│  (Data access via JPA)                  │
│  - TranslationRepository                │
│  - SentenceRepository                   │
│  - WordRepository                       │
│  - TranslationSessionRepository         │
│  - CharacterGuideRepository             │
└────────────────┬────────────────────────┘
                 │
┌────────────────▼────────────────────────┐
│         Database Layer                  │
│       (PostgreSQL 15)                   │
│  Tables: translations, sentences,       │
│          words, translation_sessions,   │
│          character_guide                │
└─────────────────────────────────────────┘
```

---

## Service Layer Analysis

### 1. Translation Services

**File:** `src/main/java/com/raidrin/eme/translator/OpenAITranslationService.java`

**Purpose:** Translate text using OpenAI GPT-4o-mini

**Key Implementation Details:**
```java
@Service
@RequiredArgsConstructor
public class OpenAITranslationService implements TranslationService {
    private final TranslationStorageService translationStorageService;
    private final RestTemplate restTemplate;

    @Value("${openai.api.key}")
    private String openAiApiKey;

    public Set<String> translate(String text,
                                 String sourceLanguage,
                                 String targetLanguage) {
        // 1. Check cache first
        Optional<TranslationEntity> cached =
            translationStorageService.findTranslation(text, sourceLanguage, targetLanguage);
        if (cached.isPresent()) {
            return cached.get().getTranslations();
        }

        // 2. Call OpenAI API
        String prompt = buildTranslationPrompt(text, sourceLanguage, targetLanguage);
        OpenAiRequest request = OpenAiRequest.builder()
            .model("gpt-4o-mini")
            .messages(List.of(
                new Message("system", "You are a professional translator..."),
                new Message("user", prompt)
            ))
            .maxTokens(200)
            .temperature(0.3)
            .build();

        // 3. Execute HTTP request
        ResponseEntity<OpenAiResponse> response = restTemplate.exchange(
            "https://api.openai.com/v1/chat/completions",
            HttpMethod.POST,
            createHttpEntity(request),
            OpenAiResponse.class
        );

        // 4. Parse and cache
        Set<String> translations = parseTranslations(response);
        translationStorageService.saveTranslation(text, sourceLanguage, targetLanguage, translations);
        return translations;
    }
}
```

**Strengths:**
- ✅ Caching via `TranslationStorageService` reduces API calls
- ✅ Clean separation of concerns
- ✅ Dependency injection via constructor

**Weaknesses:**
- ❌ No retry logic on API failure
- ❌ No timeout handling
- ❌ No quality validation of translations
- ❌ Fixed model (can't dynamically choose based on task complexity)
- ❌ Basic error handling (throws `RuntimeException`)

---

### 2. Sentence Generation Service

**File:** `src/main/java/com/raidrin/eme/sentence/SentenceGenerationService.java`

**Purpose:** Generate example sentences using the target word

**Key Implementation Details:**
```java
@Service
@RequiredArgsConstructor
public class SentenceGenerationService {
    @Value("${openai.api.key}")
    private String openAiApiKey;

    public SentenceData generateSentence(String word,
                                         String sourceLanguage,
                                         String targetLanguage) {
        // Check cache
        Optional<SentenceEntity> cached = sentenceRepository.findByWordAndLanguages(...);
        if (cached.isPresent()) {
            return mapToSentenceData(cached.get());
        }

        // Build complex prompt
        String prompt = String.format(
            "Given the word '%s', create a simple sentence in %s using this word. " +
            "Provide the following 5 elements separated by newlines:\n" +
            "1. The word in Latin characters (romanized)\n" +
            "2. A simple sentence in %s using this word\n" +
            "3. The sentence transliteration in Latin characters\n" +
            "4. The sentence translated to %s\n" +
            "5. Word-by-word structure analysis...",
            word, sourceLangName, sourceLangName, targetLangName
        );

        // Call OpenAI with higher temperature for creativity
        OpenAiRequest request = OpenAiRequest.builder()
            .model("gpt-4o-mini")
            .temperature(0.7)  // More creative than translation
            .maxTokens(300)
            .messages(...)
            .build();

        // Parse 5-line response
        SentenceData result = parseFiveLineResponse(response);
        sentenceRepository.save(mapToEntity(result));
        return result;
    }
}
```

**Strengths:**
- ✅ Structured output format (5 distinct elements)
- ✅ Caching via database
- ✅ Higher temperature for creativity

**Weaknesses:**
- ❌ No validation that sentence actually uses the target word
- ❌ No grammar checking
- ❌ Parsing is brittle (relies on exact line count and format)
- ❌ No quality scoring

---

### 3. Mnemonic Generation Service

**File:** `src/main/java/com/raidrin/eme/mnemonic/MnemonicGenerationService.java`

**Purpose:** Generate memory aids using familiar characters and visual prompts

**Key Implementation Details:**
```java
@Service
@RequiredArgsConstructor
public class MnemonicGenerationService {
    private final CharacterGuideRepository characterGuideRepository;

    public MnemonicData generateMnemonic(String word,
                                        String translation,
                                        String sourceLanguage,
                                        String targetLanguage) {
        // Find character for phonetic match
        String startSound = extractStartSound(word);
        Optional<CharacterGuideEntity> character =
            characterGuideRepository.findByLanguageAndStartSound(sourceLanguage, startSound);

        // Build prompt with character context
        String characterContext = character.isPresent()
            ? character.get().getCharacterName() + " - " + character.get().getCharacterContext()
            : "a memorable character";

        String prompt = String.format(
            "Create a vivid mnemonic to remember that '%s' means '%s'. " +
            "Use %s in the mnemonic. Respond in JSON format with: " +
            "mnemonic_keyword, mnemonic_sentence, image_prompt",
            word, translation, characterContext
        );

        // Request JSON response
        OpenAiRequest request = OpenAiRequest.builder()
            .model("gpt-4o-mini")
            .temperature(0.7)
            .maxTokens(500)
            .responseFormat(Map.of("type", "json_object"))  // Force JSON
            .messages(...)
            .build();

        // Parse JSON response
        String jsonContent = extractContent(response);
        MnemonicData data = objectMapper.readValue(jsonContent, MnemonicData.class);
        return data;
    }
}
```

**Strengths:**
- ✅ Character guide integration for consistency
- ✅ JSON structured output
- ✅ Phonetic matching

**Weaknesses:**
- ❌ No validation that image prompt matches mnemonic
- ❌ Character selection is basic (just first sound)
- ❌ No user preference tracking (always uses same characters)

---

### 4. Session Orchestration Service

**File:** `src/main/java/com/raidrin/eme/session/SessionOrchestrationService.java`

**Purpose:** Coordinate batch processing of multiple words with all features

**Key Implementation Details:**
```java
@Service
@RequiredArgsConstructor
public class SessionOrchestrationService {
    @Async("taskExecutor")
    public CompletableFuture<Void> processTranslationBatchAsync(
        Long sessionId,
        BatchProcessingRequest request
    ) {
        TranslationSessionEntity session = sessionRepository.findById(sessionId)
            .orElseThrow();

        // Update status
        session.setStatus(SessionStatus.IN_PROGRESS);
        sessionRepository.save(session);

        List<Map<String, Object>> wordResults = new ArrayList<>();
        Map<String, Integer> processSummary = new HashMap<>();

        for (String sourceWord : request.getWords()) {
            try {
                // Check for existing data (deduplication)
                Map<String, Object> existingData =
                    findExistingWordData(sourceWord, sourceLanguage, targetLanguage);
                if (existingData != null) {
                    wordResults.add(existingData);
                    continue;
                }

                // Process word
                Map<String, Object> wordData = new HashMap<>();

                // 1. Translation
                Set<String> translations = translationService.translate(...);
                wordData.put("translations", translations);

                // 2. Sentence generation (if enabled)
                if (request.isSentenceGenerationEnabled()) {
                    SentenceData sentence = sentenceService.generate(...);
                    wordData.put("sentence", sentence);
                }

                // 3. Mnemonic + Image (if enabled)
                if (request.isImageGenerationEnabled()) {
                    MnemonicData mnemonic = mnemonicService.generate(...);
                    wordData.put("mnemonic", mnemonic);

                    // Generate image asynchronously
                    CompletableFuture<GeneratedImage> imageFuture =
                        imageService.generateImageAsync(mnemonic.getImagePrompt());
                    GeneratedImage image = imageFuture.get(); // Block for result
                    wordData.put("image", image);
                }

                // 4. Audio (if enabled)
                if (request.isAudioGenerationEnabled()) {
                    List<AudioResult> audio = audioService.generateAudio(...).get();
                    wordData.put("audio", audio);
                }

                wordResults.add(wordData);

            } catch (Exception e) {
                // Log error and continue
                System.err.println("Error processing word: " + sourceWord);
                e.printStackTrace();
                processSummary.put("error_count",
                    processSummary.getOrDefault("error_count", 0) + 1);
            }
        }

        // Save session data
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("words", wordResults);
        sessionData.put("process_summary", processSummary);
        session.setSessionData(new ObjectMapper().writeValueAsString(sessionData));
        session.setStatus(SessionStatus.COMPLETED);
        sessionRepository.save(session);

        return CompletableFuture.completedFuture(null);
    }
}
```

**Strengths:**
- ✅ Async processing with `@Async`
- ✅ Deduplication (reuses existing data)
- ✅ Comprehensive session tracking
- ✅ Error containment (one word failure doesn't stop batch)

**Weaknesses:**
- ❌ Errors are caught but not intelligently handled
- ❌ No retry logic
- ❌ Blocking `.get()` calls reduce parallelism
- ❌ No progress reporting during execution
- ❌ Session can get stuck if exception occurs outside try/catch

---

### 5. Async Image Generation Service

**File:** `src/main/java/com/raidrin/eme/image/AsyncImageGenerationService.java`

**Purpose:** Generate images via Leonardo AI with polling

**Key Implementation Details:**
```java
@Service
@RequiredArgsConstructor
public class AsyncImageGenerationService {
    @Value("${leonardo.api.key}")
    private String apiKey;

    @Value("${leonardo.model.id}")
    private String modelId;

    @Async("taskExecutor")
    public CompletableFuture<GeneratedImage> generateImageAsync(String prompt) {
        // 1. Submit generation request
        LeonardoGenerationRequest request = LeonardoGenerationRequest.builder()
            .modelId(modelId)
            .prompt(prompt)
            .width(1152)
            .height(768)
            .numImages(1)
            .build();

        ResponseEntity<LeonardoGenerationResponse> response =
            restTemplate.postForEntity(LEONARDO_API_URL, request, ...);

        String generationId = response.getBody().getSdGenerationJob().getGenerationId();

        // 2. Poll for completion
        int maxAttempts = 60;
        int attemptCount = 0;
        while (attemptCount < maxAttempts) {
            Thread.sleep(2000); // 2 second polling interval

            LeonardoStatusResponse status = checkStatus(generationId);
            if ("COMPLETE".equals(status.getStatus())) {
                GeneratedImage image = status.getGeneratedImages().get(0);

                // 3. Upload to Google Cloud Storage
                String gsUrl = gcpStorageService.uploadImage(image.getUrl(), generationId);
                image.setGcsUrl(gsUrl);

                return CompletableFuture.completedFuture(image);
            }

            attemptCount++;
        }

        throw new RuntimeException("Image generation timed out after 2 minutes");
    }
}
```

**Strengths:**
- ✅ Async execution
- ✅ Polling pattern for long-running tasks
- ✅ Automatic GCS upload

**Weaknesses:**
- ❌ Fixed polling interval (not exponential backoff)
- ❌ Fixed timeout (2 minutes max)
- ❌ No error recovery (timeout = failure)
- ❌ No alternative provider fallback

---

## Data Model

### Entity Relationship Diagram

```
┌──────────────────────┐
│  TranslationEntity   │
├──────────────────────┤
│ id: Long (PK)        │
│ word: String         │
│ sourceLanguage       │
│ targetLanguage       │
│ translations: Set    │ (JSON serialized)
│ createdAt, updatedAt │
└──────────────────────┘
        │
        │ referenced by
        ▼
┌──────────────────────┐       ┌──────────────────────┐
│   SentenceEntity     │       │  CharacterGuideEntity│
├──────────────────────┤       ├──────────────────────┤
│ id: Long (PK)        │       │ id: Long (PK)        │
│ word: String         │       │ language: String     │
│ sourceLanguage       │       │ startSound: String   │
│ targetLanguage       │       │ characterName        │
│ wordRomanized        │       │ characterContext     │
│ sentenceSource       │       └──────────────────────┘
│ sentenceTranslit...  │                │
│ sentenceTarget       │                │ used by
│ wordStructure        │                │
└──────────────────────┘                │
        │                               │
        │ referenced by                 │
        ▼                               ▼
┌──────────────────────────────────────────────┐
│           WordEntity                         │
├──────────────────────────────────────────────┤
│ id: Long (PK)                                │
│ word: String                                 │
│ sourceLanguage, targetLanguage               │
│ translation: String (JSON)                   │
│ audioSourceFile, audioTargetFile             │
│ imageFile, imagePrompt                       │
│ mnemonicKeyword, mnemonicSentence            │
│ sourceTransliteration                        │
│ createdAt, updatedAt                         │
└──────────────────────────────────────────────┘
        ▲
        │ referenced by
        │
┌──────────────────────────────────────────────┐
│      TranslationSessionEntity                │
├──────────────────────────────────────────────┤
│ id: Long (PK)                                │
│ word: String (CSV of words)                  │
│ sourceLanguage, targetLanguage               │
│ status: SessionStatus (enum)                 │
│ imageGenerationEnabled: Boolean              │
│ audioGenerationEnabled: Boolean              │
│ ankiEnabled, ankiDeck, anki*Template         │
│ sentenceGenerationEnabled: Boolean           │
│ sessionData: String (JSON)                   │
│ zipFilePath: String                          │
│ createdAt, updatedAt, completedAt            │
└──────────────────────────────────────────────┘
```

### Key Tables

#### 1. `translations`
**Purpose:** Cache translation results to avoid redundant API calls

**Schema:**
```sql
CREATE TABLE translations (
    id BIGSERIAL PRIMARY KEY,
    word TEXT NOT NULL,
    source_language VARCHAR(10) NOT NULL,
    target_language VARCHAR(10) NOT NULL,
    translations TEXT NOT NULL,  -- JSON serialized Set<String>
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(word, source_language, target_language)
);

CREATE INDEX idx_translations_word_langs
    ON translations(word, source_language, target_language);
CREATE INDEX idx_translations_created_at
    ON translations(created_at);
```

**Strengths:**
- ✅ Composite unique constraint prevents duplicates
- ✅ Efficient lookup via composite index
- ✅ Timestamp tracking for cache aging

**Potential Improvements for Agents:**
- Quality score column for QA Agent
- Confidence score for translation alternatives
- Provider column (OpenAI vs. Google Translate) for cost tracking

---

#### 2. `sentences`
**Purpose:** Cache generated example sentences

**Schema:**
```sql
CREATE TABLE sentences (
    id BIGSERIAL PRIMARY KEY,
    word TEXT NOT NULL,
    source_language VARCHAR(10) NOT NULL,
    target_language VARCHAR(10) NOT NULL,
    word_romanized TEXT,
    sentence_source TEXT,
    sentence_transliteration TEXT,
    sentence_target TEXT,
    word_structure TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(word, source_language, target_language)
);

CREATE INDEX idx_sentences_word_langs
    ON sentences(word, source_language, target_language);
```

**Potential Improvements for Agents:**
- `quality_score` DECIMAL - QA Agent validation
- `grammar_validated` BOOLEAN - QA Agent check
- `uses_target_word` BOOLEAN - Coherence Agent verification
- `difficulty_level` VARCHAR - Personalization Agent metadata

---

#### 3. `words`
**Purpose:** Centralized cache for complete word data (V7 migration)

**Schema:**
```sql
CREATE TABLE words (
    id BIGSERIAL PRIMARY KEY,
    word TEXT NOT NULL,
    source_language VARCHAR(10) NOT NULL,
    target_language VARCHAR(10) NOT NULL,
    translation TEXT,  -- JSON serialized
    audio_source_file TEXT,
    audio_target_file TEXT,
    image_file TEXT,
    image_prompt TEXT,
    mnemonic_keyword TEXT,
    mnemonic_sentence TEXT,
    source_transliteration TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(word, source_language, target_language)
);

CREATE INDEX idx_words_lookup
    ON words(word, source_language, target_language);
CREATE INDEX idx_words_updated_at
    ON words(updated_at DESC);
```

**This is the primary deduplication mechanism** - before generating new content, the system checks if a `WordEntity` exists and reuses assets.

**Potential Improvements for Agents:**
- `quality_metadata` JSONB - Comprehensive quality scores from QA Agent
- `cost_metadata` JSONB - Cost per asset, provider used (Cost Optimization Agent)
- `usage_count` INT - Track popularity for Personalization Agent
- `last_reviewed_at` TIMESTAMP - Quality review timestamp

---

#### 4. `translation_sessions`
**Purpose:** Track batch processing sessions with full state

**Schema:**
```sql
CREATE TABLE translation_sessions (
    id BIGSERIAL PRIMARY KEY,
    word TEXT,  -- CSV of words being processed
    source_language VARCHAR(10),
    target_language VARCHAR(10),
    status VARCHAR(20),  -- PENDING, IN_PROGRESS, COMPLETED, FAILED
    image_generation_enabled BOOLEAN DEFAULT FALSE,
    audio_generation_enabled BOOLEAN DEFAULT FALSE,
    anki_enabled BOOLEAN DEFAULT FALSE,
    anki_deck TEXT,
    anki_front_template TEXT,
    anki_back_template TEXT,
    sentence_generation_enabled BOOLEAN DEFAULT FALSE,
    session_data TEXT,  -- JSON with all results and errors
    zip_file_path TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_translation_sessions_status
    ON translation_sessions(status);
CREATE INDEX idx_translation_sessions_created_at
    ON translation_sessions(created_at);
```

**Strengths:**
- ✅ Comprehensive session tracking
- ✅ JSON `session_data` field stores arbitrary results
- ✅ Status tracking for monitoring

**Potential Improvements for Agents:**
- `agent_execution_log` JSONB - Track all agent decisions
- `retry_count` INT - Error Recovery Agent tracking
- `quality_gate_status` VARCHAR - QA Agent approval status
- `cost_total` DECIMAL - Total cost for session

---

#### 5. `character_guide`
**Purpose:** Map phonetic sounds to memorable characters for mnemonics

**Schema:**
```sql
CREATE TABLE character_guide (
    id BIGSERIAL PRIMARY KEY,
    language VARCHAR(10) NOT NULL,
    start_sound VARCHAR(50) NOT NULL,
    character_name VARCHAR(255),
    character_context TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(language, start_sound)
);

CREATE INDEX idx_character_guide_lookup
    ON character_guide(language, start_sound);
```

**Potential Improvements for Agents:**
- `user_id` BIGINT - Personalization Agent can track per-user character preferences
- `effectiveness_score` DECIMAL - Track which characters lead to better retention
- `usage_count` INT - Popularity tracking

---

## AI Integration Patterns

### HTTP Client Configuration

**File:** `src/main/java/com/raidrin/eme/config/AppConfig.java`

```java
@Configuration
public class AppConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**Current Pattern:**
- Basic `RestTemplate` with no customization
- No timeout configuration
- No retry interceptors
- No connection pooling tuning

**Agent Enhancement Opportunities:**
```java
@Bean
public RestTemplate agentAwareRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(10000);  // 10s connect timeout
    factory.setReadTimeout(30000);     // 30s read timeout

    RestTemplate restTemplate = new RestTemplate(factory);

    // Add agent-aware interceptors
    restTemplate.getInterceptors().add(new AgentMetricsInterceptor());
    restTemplate.getInterceptors().add(new CostTrackingInterceptor());

    // Add error handler for agent recovery
    restTemplate.setErrorHandler(new AgentAwareResponseErrorHandler());

    return restTemplate;
}
```

---

### OpenAI API Request Pattern

**Current Implementation:**
```java
private HttpEntity<OpenAiRequest> createHttpEntity(OpenAiRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(openAiApiKey);
    return new HttpEntity<>(request, headers);
}

ResponseEntity<OpenAiResponse> response = restTemplate.exchange(
    "https://api.openai.com/v1/chat/completions",
    HttpMethod.POST,
    createHttpEntity(request),
    OpenAiResponse.class
);
```

**Weaknesses:**
- Hardcoded endpoint URL
- No error handling for HTTP errors
- No timeout handling
- No rate limit detection

**Agent-Enhanced Pattern:**
```java
public OpenAiResponse callWithAgentSupport(OpenAiRequest request, AgentContext context) {
    // Cost Optimization Agent: Select model based on task
    String model = costOptimizationAgent.selectOptimalModel(request, context);
    request.setModel(model);

    // Execute with agent retry wrapper
    return errorRecoveryAgent.executeWithRecovery(() -> {
        try {
            ResponseEntity<OpenAiResponse> response = restTemplate.exchange(...);

            // Quality Assurance Agent: Validate response
            qualityAgent.validateResponse(response.getBody(), context);

            return response.getBody();

        } catch (HttpClientErrorException.TooManyRequests e) {
            // Error Recovery Agent handles rate limits
            throw new RateLimitException(e);
        }
    }, context);
}
```

---

### Prompt Construction

**Current Pattern:** String concatenation

**Example:**
```java
String prompt = String.format(
    "Translate the following text from %s to %s. " +
    "Provide only the translation(s), separated by newlines...: %s",
    sourceLangName, targetLangName, text
);
```

**Weaknesses:**
- No prompt versioning
- No A/B testing capability
- Scattered across services (no centralization)
- No optimization based on results

**Agent-Enhanced Pattern:**
```java
@Service
public class PromptManagementService {
    private final Map<String, PromptTemplate> templates = new HashMap<>();

    public String buildPrompt(String templateKey, Map<String, Object> variables, AgentContext context) {
        PromptTemplate template = templates.get(templateKey);

        // Cost Optimization Agent: Select prompt variant
        PromptVariant variant = costOptimizationAgent.selectPromptVariant(
            template,
            context.getTaskComplexity()
        );

        // Build prompt
        String prompt = variant.render(variables);

        // Quality Assurance Agent: Validate prompt
        qualityAgent.validatePrompt(prompt, context);

        return prompt;
    }
}

// Usage:
String prompt = promptService.buildPrompt(
    "translation.basic",
    Map.of(
        "sourceLang", sourceLanguage,
        "targetLang", targetLanguage,
        "text", text
    ),
    agentContext
);
```

---

## Async Processing Architecture

### Thread Pool Configuration

**File:** `src/main/java/com/raidrin/eme/config/AsyncConfiguration.java`

```java
@Configuration
@EnableAsync
public class AsyncConfiguration {
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);           // 5 threads always running
        executor.setMaxPoolSize(10);           // Max 10 threads
        executor.setQueueCapacity(100);        // 100 tasks can queue
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

**Analysis:**
- **Core pool:** 5 threads (reasonable for low traffic)
- **Max pool:** 10 threads (adequate for current scale)
- **Queue:** 100 capacity (prevents unbounded growth)
- **Rejection policy:** Default (AbortPolicy - throws exception when queue full)

**Agent Considerations:**
- Agents will add overhead (reasoning, validation, logging)
- May need separate thread pool for agent tasks
- Monitor queue depth as agent complexity grows

**Recommended Agent-Aware Configuration:**
```java
@Bean(name = "agentTaskExecutor")
public Executor agentTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(3);            // Agents are more CPU-intensive
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("agent-");
    executor.setRejectionPolicy(new ThreadPoolExecutor.CallerRunsPolicy());  // Backpressure
    executor.initialize();
    return executor;
}
```

---

### Async Method Pattern

**Current Pattern:**
```java
@Async("taskExecutor")
public CompletableFuture<List<AudioResult>> generateAudioFilesAsync(List<AudioRequest> requests) {
    List<AudioResult> results = new ArrayList<>();
    for (AudioRequest request : requests) {
        try {
            AudioResult result = textToAudioGenerator.generateAudio(...);
            results.add(result);
        } catch (Exception e) {
            System.err.println("Audio generation failed: " + e.getMessage());
            // Continue processing other requests
        }
    }
    return CompletableFuture.completedFuture(results);
}
```

**Strengths:**
- ✅ Non-blocking async execution
- ✅ Error isolation (one failure doesn't stop batch)
- ✅ Returns `CompletableFuture` for composability

**Weaknesses:**
- ❌ Sequential processing (not parallelized within method)
- ❌ No progress reporting
- ❌ Errors are logged but not intelligently handled

---

## Configuration Management

### Application Properties

**File:** `src/main/resources/application.properties`

```properties
# Server
server.port=8082

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/eme_cache
spring.datasource.username=eme_user
spring.datasource.password=eme_password
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# Flyway
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true

# AI API Keys (from environment)
openai.api.key=${OPENAI_API_KEY}
leonardo.api.key=${LEONARDO_API_KEY}

# Leonardo Configuration
leonardo.model.id=1e60896f-3c26-4296-8ecc-53e2afecc132

# File Paths
image.output.directory=${IMAGE_OUTPUT_DIR:./generated_images}
zip.output.directory=${ZIP_OUTPUT_DIR:./session_zips}

# Anki
anki.connect.url=${ANKI_CONNECT_API_URL:http://localhost:8765}

# GCP
gcp.storage.bucket-name=${GCP_STORAGE_BUCKET_NAME:eme-flashcard-images}
```

**Strengths:**
- ✅ Environment variable externalization
- ✅ Sensible defaults for local development
- ✅ Clear separation of concerns

**Agent Configuration Additions Needed:**
```properties
# Agent Configuration
agents.enabled=true
agents.error-recovery.enabled=true
agents.error-recovery.max-retries=3
agents.error-recovery.backoff-multiplier=2

agents.quality-assurance.enabled=true
agents.quality-assurance.min-score=0.7
agents.quality-assurance.strict-mode=false

agents.cost-optimization.enabled=true
agents.cost-optimization.budget-daily-usd=50.00
agents.cost-optimization.prefer-cheap-models=true

agents.multimodal-coherence.enabled=false  # Phase 2
agents.personalization.enabled=false       # Phase 4

# Agent LLM (for agent reasoning)
agents.llm.provider=anthropic  # or openai
agents.llm.model=claude-3-5-sonnet-20241022
agents.llm.api-key=${ANTHROPIC_API_KEY}
```

---

## Current Limitations

### 1. Error Handling

**Problem:** Basic try-catch with console logging

**Impact:**
- Manual debugging required for failures
- No automatic recovery
- Session state can become inconsistent
- Users see generic error messages

**Example of Problematic Code:**
```java
try {
    translations = openAIService.translate(word, source, target);
} catch (Exception e) {
    System.err.println("Translation failed: " + e.getMessage());
    e.printStackTrace();
    throw new RuntimeException("Failed to translate", e);  // Session fails
}
```

**Agent Solution:** Error Recovery Agent intercepts exceptions, diagnoses root cause, and attempts intelligent recovery before surfacing to user.

---

### 2. Quality Validation

**Problem:** No verification of AI output quality

**Impact:**
- Incorrect translations may be cached
- Grammatically wrong sentences may be presented
- Mnemonics may not match images
- Cultural inappropriateness not detected

**Current State:** Trust AI completely, no validation layer

**Agent Solution:** Quality Assurance Agent validates all AI outputs before caching/presenting to users.

---

### 3. Cost Tracking

**Problem:** No comprehensive cost monitoring

**Current Tracking:**
- Leonardo AI credit cost stored in session data
- No OpenAI token usage tracking
- No cost aggregation or budgets

**Impact:**
- Unknown monthly AI spend
- Can't optimize for cost
- No alerts for cost spikes

**Agent Solution:** Cost Optimization Agent tracks all API calls, estimates costs, enforces budgets, and optimizes model selection.

---

### 4. Logging Infrastructure

**Problem:** Basic `System.out.println` and `System.err.println`

**Impact:**
- No structured logging
- Can't aggregate/query logs
- No log levels (DEBUG, INFO, WARN, ERROR)
- No correlation IDs for tracing requests

**Current Pattern:**
```java
System.out.println("Translating with OpenAI API: " + text);
System.err.println("OpenAI API error: " + e.getMessage());
e.printStackTrace();
```

**Agent Needs:** Structured logging with context

**Solution:** Add SLF4J + Logback
```java
@Slf4j
@Service
public class OpenAITranslationService {
    public Set<String> translate(...) {
        log.info("Starting translation",
            "word", text,
            "source", sourceLanguage,
            "target", targetLanguage
        );

        try {
            // ...
            log.debug("OpenAI API request", "model", "gpt-4o-mini", "tokens", 200);
            // ...
            log.info("Translation completed", "results", translations.size());
            return translations;
        } catch (Exception e) {
            log.error("Translation failed", e,
                "word", text,
                "error_type", e.getClass().getSimpleName()
            );
            throw e;
        }
    }
}
```

---

### 5. Metrics and Observability

**Problem:** No application metrics

**Impact:**
- Can't monitor performance trends
- No SLO/SLA tracking
- No alerting on degradation

**Current State:** No metrics collection

**Agent Needs:** Metrics for agent behavior

**Solution:** Add Micrometer + Spring Boot Actuator
```java
@Service
@RequiredArgsConstructor
public class MetricsService {
    private final MeterRegistry meterRegistry;

    public void recordAgentExecution(String agentName, Duration duration, String outcome) {
        Timer.builder("agent.execution.duration")
            .tag("agent", agentName)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .record(duration);

        Counter.builder("agent.execution.count")
            .tag("agent", agentName)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
    }
}
```

---

## Agent Integration Points

### Where Agents Will Plug In

#### 1. Service Layer Interceptors

Agents can wrap existing services with aspect-oriented programming:

```java
@Aspect
@Component
@RequiredArgsConstructor
public class AgentAspect {
    private final SmartErrorRecoveryAgent errorAgent;
    private final QualityAssuranceAgent qaAgent;

    @Around("@annotation(AgentManaged)")
    public Object manageWithAgents(ProceedingJoinPoint joinPoint) throws Throwable {
        AgentContext context = AgentContext.from(joinPoint);

        try {
            // Execute original method with error recovery
            Object result = errorAgent.executeWithRecovery(() -> {
                return joinPoint.proceed();
            }, context);

            // Validate result with QA agent
            qaAgent.validate(result, context);

            return result;

        } catch (Exception e) {
            // Final fallback if agent can't recover
            throw e;
        }
    }
}

// Usage:
@Service
public class OpenAITranslationService {
    @AgentManaged  // <-- Agent aspect applies here
    public Set<String> translate(String text, String source, String target) {
        // Existing code unchanged
    }
}
```

---

#### 2. New Service Layer for Agents

Create dedicated agent services:

```
src/main/java/com/raidrin/eme/agent/
├── AgentOrchestrator.java              # Coordinates all agents
├── config/
│   ├── AgentConfiguration.java         # Agent beans
│   └── AgentProperties.java            # Configuration binding
├── core/
│   ├── AgentContext.java               # Execution context
│   ├── AgentInterface.java             # Common interface
│   ├── BaseAgent.java                  # Abstract base class
│   └── AgentResult.java                # Standardized result
├── recovery/
│   ├── SmartErrorRecoveryAgent.java
│   └── RetryStrategy.java
├── quality/
│   ├── QualityAssuranceAgent.java
│   └── QualityScore.java
├── coherence/
│   └── MultiModalCoherenceAgent.java
├── cost/
│   ├── CostOptimizationAgent.java
│   └── CostTracker.java
├── personalization/
│   └── PersonalizedLearningPathAgent.java
└── metrics/
    └── AgentMetricsService.java
```

---

#### 3. Database Schema Extensions

New tables for agent data:

```sql
-- Agent execution tracking
CREATE TABLE agent_executions (
    id BIGSERIAL PRIMARY KEY,
    agent_name VARCHAR(100),
    session_id BIGINT,
    context JSONB,
    decisions JSONB,
    outcome VARCHAR(50),
    duration_ms INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Quality scores
CREATE TABLE quality_scores (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50),  -- 'translation', 'sentence', 'mnemonic'
    entity_id BIGINT,
    score DECIMAL(3,2),       -- 0.00 to 1.00
    dimensions JSONB,          -- {accuracy: 0.95, fluency: 0.88, ...}
    validated_by VARCHAR(100), -- 'qa_agent' or 'human'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Cost tracking
CREATE TABLE api_calls (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT,
    provider VARCHAR(50),      -- 'openai', 'leonardo', 'google_tts'
    model VARCHAR(100),        -- 'gpt-4o-mini', 'dall-e-3'
    operation VARCHAR(100),    -- 'translation', 'image_generation'
    tokens_used INT,
    estimated_cost_usd DECIMAL(10,6),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Learner profiles (for Personalization Agent)
CREATE TABLE learner_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    language_pair VARCHAR(50),
    difficulty_level VARCHAR(20),
    weak_phonemes JSONB,
    preferred_characters JSONB,
    performance_metrics JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## Summary: Current State Strengths & Gaps

### Strengths to Leverage

✅ **Well-structured codebase** - Clean service layer, good separation of concerns
✅ **Async foundation** - `@Async` with `CompletableFuture` ready for agents
✅ **Database caching** - Reduces redundant API calls
✅ **Flexible configuration** - Environment variables for easy agent config addition
✅ **Modern tech stack** - Spring Boot 3, Java 17, PostgreSQL 15

### Gaps That Agents Will Address

❌ **No error recovery** → Smart Error Recovery Agent
❌ **No quality validation** → Quality Assurance Agent
❌ **No cost optimization** → Cost Optimization Agent
❌ **No multi-modal coordination** → Multi-Modal Coherence Agent
❌ **No personalization** → Personalized Learning Path Agent
❌ **Basic logging** → Structured logging with agent context
❌ **No metrics** → Micrometer with agent-specific metrics
❌ **No observability** → Agent execution dashboards

---

[Continue to Error Recovery Agent Specification →](03-agent-error-recovery.md)

[← Back to Main Document](../../AGENTIC_AI_INTEGRATION.md)
