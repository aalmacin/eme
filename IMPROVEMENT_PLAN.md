# Model & Structure Improvement Plan

## Executive Summary

This plan addresses redundant processing, data duplication, and inefficient patterns in the async processing architecture. Additionally, it introduces a new model structure to support multiple variants of generated content (images, mnemonics, character guides) with a "current" selection system.

---

## Implementation Status

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Create new variant tables (V19) | ✅ COMPLETED |
| 2 | Data migration script (V20) | ✅ COMPLETED |
| 3 | Create entity classes | ✅ COMPLETED |
| 4 | Create WordVariantService | ✅ COMPLETED |
| 5 | Remove session fallback lookup | ✅ COMPLETED |
| 6 | Batch progress updates | ✅ COMPLETED |
| 7 | Consolidate thread pools | ✅ COMPLETED |
| 8 | Update SessionOrchestrationService | ✅ COMPLETED |
| 9 | Update UI for variant selection | ✅ COMPLETED |
| 10 | Deprecate old entities | ✅ COMPLETED |

### Completed Work Details

**Phase 1-2: Database Migrations**
- Created `V19__create_word_variant_tables.sql` with tables: `word_translations`, `word_mnemonics`, `word_images`, `word_sentences`
- Created `V20__migrate_existing_word_data.sql` to migrate existing data

**Phase 3: Entity Classes Created**
- `WordTranslationEntity.java`
- `WordMnemonicEntity.java`
- `WordImageEntity.java`
- `WordSentenceEntity.java`
- Updated `WordEntity.java` with @OneToMany relationships and helper methods

**Phase 4: WordVariantService**
- Full CRUD operations for all variant types
- `setCurrentX()` methods to switch active variants
- `getXHistory()` methods to retrieve all variants
- `VariantCounts` record for statistics

**Phase 5: Session Fallback Lookup Removed**
- Simplified `TranslationSessionService.findExistingWordData()` from O(n×m) to O(1)
- WordEntity is now the single source of truth

**Phase 6: Batch Progress Updates**
- Added `processing.progress.update.interval` config (default: 5)
- Progress now updates every N words instead of after each word
- Reduces database writes by ~80% for typical batches

**Phase 7: Thread Pool Consolidation**
- Added `wordProcessingExecutor` bean (configurable pool size)
- Added `audioProcessingExecutor` bean (configurable pool size)
- Added `@PreDestroy` graceful shutdown
- `SessionOrchestrationService` now uses injected executor

**Phase 8: SessionOrchestrationService Updated**
- Integrated `WordVariantService` into `TranslationSessionService.saveWordDataToEntity()`
- New variants are now saved to variant tables when processing words
- Legacy fields still populated for backward compatibility

**Phase 9: UI for Variant Selection**
- Added variant history section to `words/detail.html`
- New API endpoints in `WordController`:
  - `GET /api/words/{id}/variants/images` - Get image history
  - `GET /api/words/{id}/variants/mnemonics` - Get mnemonic history
  - `GET /api/words/{id}/variants/translations` - Get translation history
  - `GET /api/words/{id}/variants/sentences` - Get sentence history
  - `POST /api/words/{id}/variants/{type}/{variantId}/set-current` - Switch variant
  - `GET /api/words/{id}/variants/counts` - Get variant counts
- Interactive UI to view and switch between variants
- Visual indicators for current variant and user-created entries

**Phase 10: Deprecate Old Entities**
- Marked `TranslationEntity` as `@Deprecated(since = "2.0", forRemoval = true)`
- Marked `SentenceEntity` as `@Deprecated(since = "2.0", forRemoval = true)`
- Marked `TranslationStorageService` as `@Deprecated(since = "2.0", forRemoval = true)`
- Marked `SentenceStorageService` as `@Deprecated(since = "2.0", forRemoval = true)`
- Added Javadoc deprecation notices pointing to replacement services
- Dual-write pattern implemented in `TranslationSessionService.saveWordDataToEntity()`:
  - Saves to legacy `WordEntity` fields for backward compatibility
  - Saves to new variant tables (`word_translations`, `word_mnemonics`, `word_images`, `word_sentences`)
- Deprecated services act as caches during processing, final data saved to variant tables
- Deprecation warnings now appear during compilation (expected behavior during transition)

---

## Part A: Model Enhancement - Multi-Variant Support

### Requirement

Instead of storing a single image/mnemonic/character guide per word, support:
- **Multiple variants** (keep history of all generated options)
- **Current selection** (mark which variant is active)
- Easy switching between variants without regeneration

### Proposed New Entities

#### 1. WordImageEntity (New)

```java
@Entity
@Table(name = "word_images")
public class WordImageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private WordEntity word;

    @Column(name = "image_file", columnDefinition = "TEXT")
    private String imageFile;

    @Column(name = "image_gcs_url", columnDefinition = "TEXT")
    private String imageGcsUrl;

    @Column(name = "image_prompt", columnDefinition = "TEXT")
    private String imagePrompt;

    @Column(name = "image_style", length = 50)
    private String imageStyle;  // REALISTIC_CINEMATIC, CARTOON, etc.

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

#### 2. WordMnemonicEntity (New)

```java
@Entity
@Table(name = "word_mnemonics")
public class WordMnemonicEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private WordEntity word;

    @Column(name = "mnemonic_keyword", columnDefinition = "TEXT")
    private String mnemonicKeyword;

    @Column(name = "mnemonic_sentence", columnDefinition = "TEXT")
    private String mnemonicSentence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_guide_id")
    private CharacterGuideEntity characterGuide;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    @Column(name = "is_user_created", nullable = false)
    private Boolean isUserCreated = false;  // true if manually entered by user

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

#### 3. WordTranslationEntity (New)

```java
@Entity
@Table(name = "word_translations")
public class WordTranslationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private WordEntity word;

    @Column(name = "translation", columnDefinition = "TEXT", nullable = false)
    private String translation;

    @Column(name = "transliteration", columnDefinition = "TEXT")
    private String transliteration;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    @Column(name = "is_user_created", nullable = false)
    private Boolean isUserCreated = false;

    @Column(name = "source", length = 50)
    private String source;  // "openai", "google", "user"

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

#### 4. WordSentenceEntity (New - replaces SentenceEntity relationship)

```java
@Entity
@Table(name = "word_sentences")
public class WordSentenceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private WordEntity word;

    @Column(name = "sentence_source", columnDefinition = "TEXT")
    private String sentenceSource;

    @Column(name = "sentence_transliteration", columnDefinition = "TEXT")
    private String sentenceTransliteration;

    @Column(name = "sentence_target", columnDefinition = "TEXT")
    private String sentenceTarget;

    @Column(name = "word_structure", columnDefinition = "TEXT")
    private String wordStructure;

    @Column(name = "audio_file", columnDefinition = "TEXT")
    private String audioFile;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

### Updated WordEntity

```java
@Entity
@Table(name = "words")
public class WordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "word", nullable = false, columnDefinition = "TEXT")
    private String word;

    @Column(name = "source_language", nullable = false, length = 10)
    private String sourceLanguage;

    @Column(name = "target_language", nullable = false, length = 10)
    private String targetLanguage;

    // Audio files (these are deterministic based on text, no variants needed)
    @Column(name = "audio_source_file", columnDefinition = "TEXT")
    private String audioSourceFile;

    @Column(name = "audio_target_file", columnDefinition = "TEXT")
    private String audioTargetFile;

    // Relationships to variants
    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WordTranslationEntity> translations = new ArrayList<>();

    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WordMnemonicEntity> mnemonics = new ArrayList<>();

    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WordImageEntity> images = new ArrayList<>();

    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WordSentenceEntity> sentences = new ArrayList<>();

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Helper methods
    public WordTranslationEntity getCurrentTranslation() {
        return translations.stream()
            .filter(WordTranslationEntity::getIsCurrent)
            .findFirst()
            .orElse(null);
    }

    public WordMnemonicEntity getCurrentMnemonic() {
        return mnemonics.stream()
            .filter(WordMnemonicEntity::getIsCurrent)
            .findFirst()
            .orElse(null);
    }

    public WordImageEntity getCurrentImage() {
        return images.stream()
            .filter(WordImageEntity::getIsCurrent)
            .findFirst()
            .orElse(null);
    }

    public WordSentenceEntity getCurrentSentence() {
        return sentences.stream()
            .filter(WordSentenceEntity::getIsCurrent)
            .findFirst()
            .orElse(null);
    }
}
```

### Database Migration (Flyway)

```sql
-- V19__add_word_variants.sql

-- Create word_translations table
CREATE TABLE word_translations (
    id BIGSERIAL PRIMARY KEY,
    word_id BIGINT NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    translation TEXT NOT NULL,
    transliteration TEXT,
    is_current BOOLEAN NOT NULL DEFAULT false,
    is_user_created BOOLEAN NOT NULL DEFAULT false,
    source VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_word_translations_word_id ON word_translations(word_id);
CREATE INDEX idx_word_translations_is_current ON word_translations(word_id, is_current);

-- Create word_mnemonics table
CREATE TABLE word_mnemonics (
    id BIGSERIAL PRIMARY KEY,
    word_id BIGINT NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    mnemonic_keyword TEXT,
    mnemonic_sentence TEXT,
    character_guide_id BIGINT REFERENCES character_guide(id),
    is_current BOOLEAN NOT NULL DEFAULT false,
    is_user_created BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_word_mnemonics_word_id ON word_mnemonics(word_id);
CREATE INDEX idx_word_mnemonics_is_current ON word_mnemonics(word_id, is_current);

-- Create word_images table
CREATE TABLE word_images (
    id BIGSERIAL PRIMARY KEY,
    word_id BIGINT NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    image_file TEXT,
    image_gcs_url TEXT,
    image_prompt TEXT,
    image_style VARCHAR(50),
    is_current BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_word_images_word_id ON word_images(word_id);
CREATE INDEX idx_word_images_is_current ON word_images(word_id, is_current);

-- Create word_sentences table
CREATE TABLE word_sentences (
    id BIGSERIAL PRIMARY KEY,
    word_id BIGINT NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    sentence_source TEXT,
    sentence_transliteration TEXT,
    sentence_target TEXT,
    word_structure TEXT,
    audio_file TEXT,
    is_current BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_word_sentences_word_id ON word_sentences(word_id);
CREATE INDEX idx_word_sentences_is_current ON word_sentences(word_id, is_current);
```

### Data Migration Script

```sql
-- V20__migrate_existing_word_data.sql

-- Migrate existing translations from words table
INSERT INTO word_translations (word_id, translation, transliteration, is_current, source, created_at)
SELECT
    id,
    translation,
    source_transliteration,
    true,  -- Mark as current
    'legacy',
    COALESCE(created_at, CURRENT_TIMESTAMP)
FROM words
WHERE translation IS NOT NULL AND translation != '';

-- Migrate existing mnemonics from words table
INSERT INTO word_mnemonics (word_id, mnemonic_keyword, mnemonic_sentence, character_guide_id, is_current, created_at)
SELECT
    id,
    mnemonic_keyword,
    mnemonic_sentence,
    character_guide_id,
    true,  -- Mark as current
    COALESCE(created_at, CURRENT_TIMESTAMP)
FROM words
WHERE mnemonic_keyword IS NOT NULL OR mnemonic_sentence IS NOT NULL;

-- Migrate existing images from words table
INSERT INTO word_images (word_id, image_file, image_prompt, is_current, created_at)
SELECT
    id,
    image_file,
    image_prompt,
    true,  -- Mark as current
    COALESCE(created_at, CURRENT_TIMESTAMP)
FROM words
WHERE image_file IS NOT NULL AND image_file != '';

-- Migrate existing sentences from sentences table
INSERT INTO word_sentences (word_id, sentence_source, sentence_transliteration, sentence_target, word_structure, audio_file, is_current, created_at)
SELECT
    w.id,
    s.sentence_source,
    s.sentence_transliteration,
    s.sentence_target,
    s.word_structure,
    s.audio_file,
    true,  -- Mark as current
    COALESCE(s.created_at, CURRENT_TIMESTAMP)
FROM sentences s
JOIN words w ON s.word = w.word
    AND s.source_language = w.source_language
    AND s.target_language = w.target_language;
```

---

## Part B: Eliminate Redundant Processing

### 1. Remove Double Sentence Save

**Location**: `SessionOrchestrationService.java:344-350`

**Current Issue**: `SentenceGenerationService.generateSentence()` already saves the sentence, but `SessionOrchestrationService` saves it again.

**Fix**: With the new model, add a new sentence variant instead of saving twice.

```java
// AFTER (new pattern):
if (sentenceData.getSourceLanguageSentence() != null) {
    String sentenceAudioFileName = Codec.encodeForAudioFileName(sentenceData.getSourceLanguageSentence());
    String sentenceAudioFileNameWithExt = sentenceAudioFileName + ".mp3";

    // Add as new sentence variant (handled by service layer)
    wordSentenceService.addSentenceVariant(
        wordId,
        sentenceData,
        sentenceAudioFileNameWithExt,
        true  // setAsCurrent
    );
}
```

### 2. Remove Session Fallback Lookup

**Location**: `TranslationSessionService.findExistingWordData()` (lines 180-232)

**Current Issue**: Falls back to scanning ALL completed sessions - O(n × m) complexity.

**Fix**: Use only WordEntity as source of truth.

```java
// AFTER (simplified):
public Map<String, Object> findExistingWordData(String sourceWord, String sourceLanguage, String targetLanguage) {
    return wordService.findWord(sourceWord, sourceLanguage, targetLanguage)
        .filter(word -> word.getCurrentTranslation() != null)
        .map(this::convertWordEntityToMap)
        .orElse(null);
}
```

### 3. Batch Progress Updates

**Location**: `SessionOrchestrationService.java:538`

**Current Issue**: Updates progress after EVERY word (100 words = 100 DB writes).

**Fix**: Update every N words.

```java
private static final int PROGRESS_UPDATE_INTERVAL = 5;

// In processing loop:
int currentCount = processedWordCount.incrementAndGet();
if (currentCount % PROGRESS_UPDATE_INTERVAL == 0 ||
    currentCount == request.getSourceWords().size()) {
    updateProgressData(sessionId, request, wordResults, currentCount);
}
```

### 4. Consolidate Thread Pools

**Current Issue**: Multiple unmanaged thread pools across services.

**Fix**: Centralize in `AsyncConfiguration.java`:

```java
@Configuration
public class AsyncConfiguration {

    @Bean("wordProcessingExecutor")
    public ExecutorService wordProcessingExecutor() {
        return new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new CustomizableThreadFactory("word-")
        );
    }

    @Bean("audioProcessingExecutor")
    public ExecutorService audioProcessingExecutor() {
        return Executors.newFixedThreadPool(5,
            new CustomizableThreadFactory("audio-"));
    }

    @PreDestroy
    public void shutdownExecutors() {
        // Graceful shutdown
    }
}
```

---

## Part C: Entities to Deprecate

### 1. TranslationEntity

**Reason**: Duplicates data now stored in `WordTranslationEntity`.

**Migration Path**:
1. Create `WordTranslationEntity` (Part A)
2. Migrate data from `TranslationEntity`
3. Update services to use `WordTranslationEntity`
4. Keep `TranslationEntity` read-only for 1-2 releases
5. Remove `TranslationEntity`

### 2. SentenceEntity

**Reason**: Replaced by `WordSentenceEntity` with proper foreign key relationship.

**Migration Path**:
1. Create `WordSentenceEntity` (Part A)
2. Migrate data from `SentenceEntity`
3. Update services to use `WordSentenceEntity`
4. Remove `SentenceEntity`

### 3. Flatten WordEntity

**Remove these columns** (data moved to variant tables):
- `translation` → `word_translations`
- `source_transliteration` → `word_translations.transliteration`
- `mnemonic_keyword` → `word_mnemonics`
- `mnemonic_sentence` → `word_mnemonics`
- `character_guide_id` → `word_mnemonics`
- `image_file` → `word_images`
- `image_prompt` → `word_images`
- `image_status` → computed from `word_images`
- `translation_override_at` → `word_translations.is_user_created`
- `mnemonic_keyword_updated_at` → `word_mnemonics.is_user_created`

---

## Part D: New Service Methods

### WordVariantService (New)

```java
@Service
public class WordVariantService {

    // Translation variants
    public WordTranslationEntity addTranslation(Long wordId, String translation,
            String transliteration, String source, boolean setAsCurrent);
    public void setCurrentTranslation(Long wordId, Long translationId);
    public List<WordTranslationEntity> getTranslationHistory(Long wordId);

    // Mnemonic variants
    public WordMnemonicEntity addMnemonic(Long wordId, String keyword,
            String sentence, Long characterGuideId, boolean setAsCurrent);
    public void setCurrentMnemonic(Long wordId, Long mnemonicId);
    public List<WordMnemonicEntity> getMnemonicHistory(Long wordId);

    // Image variants
    public WordImageEntity addImage(Long wordId, String imageFile,
            String gcsUrl, String prompt, String style, boolean setAsCurrent);
    public void setCurrentImage(Long wordId, Long imageId);
    public List<WordImageEntity> getImageHistory(Long wordId);

    // Sentence variants
    public WordSentenceEntity addSentence(Long wordId, SentenceData data,
            String audioFile, boolean setAsCurrent);
    public void setCurrentSentence(Long wordId, Long sentenceId);
    public List<WordSentenceEntity> getSentenceHistory(Long wordId);
}
```

---

## Implementation Order

| Phase | Description | Effort | Dependencies |
|-------|-------------|--------|--------------|
| 1 | Create new variant tables (migration V19) | Medium | None |
| 2 | Migrate existing data (migration V20) | Medium | Phase 1 |
| 3 | Create new entity classes | Medium | Phase 1 |
| 4 | Create WordVariantService | Medium | Phase 3 |
| 5 | Update SessionOrchestrationService | High | Phase 4 |
| 6 | Update UI to show variant selection | Medium | Phase 5 |
| 7 | Remove session fallback lookup | Low | Phase 5 |
| 8 | Batch progress updates | Low | None |
| 9 | Consolidate thread pools | Medium | None |
| 10 | Deprecate old entities | Low | Phase 5 |

---

## Entity Relationship Diagram (New)

```
WordEntity (core word data only)
    ├── OneToMany: WordTranslationEntity (multiple translations, one current)
    ├── OneToMany: WordMnemonicEntity (multiple mnemonics, one current)
    │       └── ManyToOne: CharacterGuideEntity
    ├── OneToMany: WordImageEntity (multiple images, one current)
    └── OneToMany: WordSentenceEntity (multiple sentences, one current)

TranslationSessionEntity
    ├── sessionData (JSON with word references + processing stats)
    └── ManyToOne: AnkiFormatEntity

CharacterGuideEntity (language, start_sound, character_name, context)

GenerationPresetEntity (saved configuration)
    └── ManyToOne: AnkiFormatEntity

[DEPRECATED: TranslationEntity, SentenceEntity]
```

---

## Benefits Summary

1. **No data loss**: All generated content is preserved as variants
2. **User choice**: Can switch between any previously generated option
3. **Clean model**: WordEntity is a hub, variants are normalized
4. **Reduced duplication**: Single source of truth per data type
5. **Better performance**: Eliminated O(n×m) session scanning
6. **Simpler async flow**: Less redundant saves and updates
