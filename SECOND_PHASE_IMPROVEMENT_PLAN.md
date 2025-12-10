# Second Phase Improvement Plan

## Overview

This document outlines the second phase of improvements for the language learning application. The focus is on refining the regeneration workflow, improving per-word control, streamlining Anki card creation with custom note types, and removing unnecessary session-level complexity.

## Key Goals

1. **Granular Regeneration Control**: Split session-level regeneration into separate, targeted operations
2. **Enhanced Word Overrides**: Provide per-word override UI with clear regeneration options
3. **Simplified Session Model**: Remove session-level status tracking and feature flags
4. **Custom Anki Integration**: Generate custom note types via AnkiConnect with proper templates
5. **Improved UX**: Add session naming and words pagination

## Current State Analysis

### Existing Architecture
- Session orchestration via `SessionOrchestrationService` (3-phase batch processing)
- Regeneration endpoints at session level (`/sessions/{id}/regenerate-all-*`)
- Word variant system for storing multiple versions of content
- Override tracking via timestamps (`translationOverrideAt`, `mnemonicKeywordUpdatedAt`)
- Session status enum: `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `CANCELLED`
- Feature flags: `imageGenerationEnabled`, `audioGenerationEnabled`, `sentenceGenerationEnabled`
- Session data stored as JSON blob with progress tracking

### Current Limitations
- All regeneration is batch-only (per-word regeneration marked as TODO)
- No clear UI for per-word override management
- Session status tracking adds complexity
- Anki cards use basic note type instead of custom templates
- Target audio generation still referenced in code
- No pagination for words within session view
- Session naming defaults to first word

---

## Phase 2 Requirements

### 1. Regeneration Actions Restructuring

**New Regeneration Operations** (Session-Level, Selective):

| Action | Description | Generation Rules |
|--------|-------------|------------------|
| **Generate Translations** | Generate translations for all words | Skip words with translation overrides |
| **Generate Transliterations** | Generate transliterations for all words | Skip words with transliteration overrides (if applicable) |
| **Generate Mnemonic Sentences** | Generate mnemonic sentences for all words | Skip if mnemonic keyword is overridden; requires translation |
| **Generate Mnemonic Keywords** | Generate mnemonic keywords for all words | Skip words with translation overrides |
| **Generate Images** | Generate images for all words | Requires: translation, mnemonic keyword, mnemonic sentence. Uses selected image style per word. Skip words missing prerequisites. |
| **Generate Audio** | Generate source audio only | Never generate target audio |
| **Generate Example Sentences** | Generate example sentences with audio | Requires translation |

**Key Changes**:
- Remove combined operations (e.g., "regenerate-all-mnemonics-and-images")
- Each operation is independent and selective
- Operations respect per-word overrides
- Clear prerequisite validation before generation

### 2. Per-Word Override Functionality

**Override UI Requirements**:
- Each word card has a "Regen" button
- Clicking "Regen" shows override options:
  - **Translation Override**: Manual translation input
  - **Transliteration Override**: Manual transliteration input
  - **Mnemonic Keyword Override**: Manual keyword input
- When override is set, global regeneration operations skip that field for that word
- Clear visual indicator when a field is overridden (e.g., badge, icon)
- Option to clear override and allow regeneration

**Backend Changes**:
- Add `transliterationOverrideAt` timestamp to `WordEntity`
- Extend override tracking to all regeneration operations
- Add endpoints:
  - `POST /words/{wordId}/override-translation`
  - `POST /words/{wordId}/override-transliteration`
  - `POST /words/{wordId}/override-mnemonic-keyword`
  - `DELETE /words/{wordId}/clear-override/{type}` (where type = translation, transliteration, mnemonic)

### 3. Image Generation Rules

**Prerequisites Validation**:
- Before generating image for a word:
  1. Verify translation exists
  2. Verify mnemonic keyword exists
  3. Verify mnemonic sentence exists
- If any prerequisite is missing, skip the word

**Image Style Management**:
- Use the selected image style for that specific word (stored in word variants)
- If no style selected, use session default or system default

**Mnemonic Sentence Foundation**:
- Always use the mnemonic sentence as the foundation for image generation
- Pass mnemonic sentence to `MnemonicGenerationService.sanitizeImagePrompt()`

**Global "Generate Images" Behavior**:
- Iterate through all words in session
- Skip words missing prerequisites (show warning/info message)
- Only generate for words that pass validation

### 4. Audio Generation Rules

**Source Audio Only**:
- Remove all references to target audio generation
- Remove `TARGET_AUDIO` from `AnkiCardItem` enum
- Remove target audio code mappings from `LanguageUtil`
- Update UI to remove target audio toggles/displays
- Update `AsyncAudioGenerationService` to only generate source audio

**Files to Update**:
- `AnkiCardItem.java` - Remove `TARGET_AUDIO`, `SENTENCE_TARGET`, `SENTENCE_TARGET_AUDIO`
- `AnkiCardBuilderService.java` - Remove target audio handling
- `sessions/view.html` - Remove target audio UI elements
- `AsyncAudioGenerationService.java` - Remove target audio generation logic
- `LanguageUtil.java` - Remove target audio code mappings

### 5. Anki Card Creation Enhancement

**Custom Note Type via AnkiConnect**:
- Note type name: `"EME Card Format"` (or configurable)
- Before creating cards:
  1. Check if note type exists via `modelNames` action
  2. If not exists, create via `createModel` action
  3. Define fields based on `AnkiFormatEntity` card items

**Field Mapping**:
Each `AnkiCardItem` becomes a field in the note type:
- `SOURCE_TEXT` → field: "SourceText"
- `SOURCE_AUDIO` → field: "SourceAudio"
- `TRANSLATION` → field: "Translation"
- `TRANSLITERATION` → field: "Transliteration"
- `MNEMONIC_KEYWORD` → field: "MnemonicKeyword"
- `MNEMONIC_SENTENCE` → field: "MnemonicSentence"
- `IMAGE` → field: "Image"
- `SENTENCE_SOURCE` → field: "SentenceSource"
- `SENTENCE_SOURCE_AUDIO` → field: "SentenceSourceAudio"
- `SENTENCE_TRANSLATION` → field: "SentenceTranslation"

**Card Template (Front/Back)**:
- Use HTML/CSS from `AnkiFormatEntity.frontCardItems` and `backCardItems`
- Include toggle functionality in template
- Template structure:
  ```html
  <div class="card">
    {{#SourceText}}<div class="source">{{SourceText}}</div>{{/SourceText}}
    {{#SourceAudio}}{{SourceAudio}}{{/SourceAudio}}
    <!-- ... other fields ... -->
  </div>
  <script>
    // Toggle functionality for collapsible sections
  </script>
  ```

**Validation Before Creation**:
- Check all required fields exist for the selected format
- If any word is missing required fields, do not allow Anki card creation
- Show clear error message listing missing fields per word

**AnkiConnect Actions to Use**:
- `modelNames` - Check existing note types
- `createModel` - Create new note type with fields and template
- `addNote` - Add individual cards with new note type

### 6. Session Model Simplification

**Remove Session-Level Features**:
- Remove status enum (`PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `CANCELLED`)
- Remove completion tracking (`completedAt`, `cancelledAt`, cancellation reason)
- Remove feature flags:
  - `imageGenerationEnabled`
  - `audioGenerationEnabled`
  - `sentenceGenerationEnabled`
  - `overrideTranslationEnabled`
- Remove `process_summary` from session_data JSON
- Remove progress tracking (`total_words`, `processed_words`)

**Session Naming**:
- Add `sessionName` field to `TranslationSessionEntity`
- Add input field on generation page for custom session name
- If not provided, default to first source word (current behavior)
- Allow editing session name from session view page

**What to Keep**:
- `ankiEnabled` flag (still needed for Anki card creation toggle)
- `ankiDeck` and `ankiFormat` (required for Anki integration)
- Basic session metadata: `createdAt`, `updatedAt`, source/target languages
- Word data storage in session_data JSON (for Anki card generation)

### 7. Features to Remove

**Remove Endpoints/Functionality**:
1. "Generate with Same Preset" - Remove endpoint and UI button
   - Endpoint: `POST /sessions/{id}/regenerate-with-same-preset`
2. "Fix Stuck Sessions" - Remove endpoint and UI button
   - Endpoint: `POST /sessions/{id}/fix-stuck`
3. "Download Assets as ZIP" - Remove endpoint and UI button
   - Endpoint: `GET /sessions/{id}/download-assets`
4. "Full Session Data Section" - Remove from view template
   - Section in `sessions/view.html` showing raw JSON

**Files to Update**:
- `TranslationSessionController.java` - Remove endpoints
- `sessions/view.html` - Remove UI sections and buttons
- `TranslationSessionService.java` - Remove related service methods

### 8. Words Pagination

**Current State**:
- Session list is paginated (20 per page)
- Words within a session are NOT paginated (all loaded at once)

**Required Changes**:
- Add pagination to words display in `sessions/view.html`
- Pagination controls at top and bottom of word list
- Page size: 20 words per page (configurable)
- Use existing pagination fragment: `fragments/pagination`
- URL parameter: `?wordPage=0` (separate from session list pagination)

**Backend Support**:
- Extract words array from session_data JSON
- Implement in-memory pagination (since words are stored in JSON)
- Or: Store words in separate table with session_id FK (architectural decision)

**UI Considerations**:
- Show "Words X-Y of Z" indicator
- Quick navigation to first/last page
- Current page highlighting

---

## Technical Implementation Plan

### Phase 2.1: Foundation & Cleanup

#### 2.1.1: Remove Target Audio
- [ ] Remove `TARGET_AUDIO`, `SENTENCE_TARGET`, `SENTENCE_TARGET_AUDIO` from `AnkiCardItem` enum
- [ ] Update `AnkiCardBuilderService.buildCardSide()` to skip target audio items
- [ ] Remove target audio code mappings from `LanguageUtil`
- [ ] Remove target audio generation logic from `AsyncAudioGenerationService`
- [ ] Update `sessions/view.html` to remove target audio UI elements
- [ ] Update database migration to remove target audio references if needed

#### 2.1.2: Remove Obsolete Features
- [ ] Remove "Generate with Same Preset" endpoint and UI
- [ ] Remove "Fix Stuck Sessions" endpoint and UI
- [ ] Remove "Download Assets as ZIP" endpoint and UI
- [ ] Remove "Full Session Data Section" from view template
- [ ] Clean up unused service methods in `TranslationSessionService`
- [ ] Remove related tests

#### 2.1.3: Session Model Cleanup
- [ ] Create database migration to add `sessionName` column to `translation_sessions`
- [ ] Create database migration to remove:
  - `status` column
  - `completed_at` column
  - `cancelled_at` column
  - Feature flag columns (`image_generation_enabled`, etc.)
- [ ] Update `TranslationSessionEntity` to remove obsolete fields
- [ ] Keep: `anki_enabled`, `anki_deck`, `anki_format_id`, `created_at`, `updated_at`
- [ ] Update session service to handle simplified model
- [ ] Update session list queries to remove status filtering

### Phase 2.2: Enhanced Override System

#### 2.2.1: Backend Override Support
- [ ] Add `transliterationOverrideAt` column to `words` table
- [ ] Update `WordEntity` with new field
- [ ] Create endpoints in `WordController`:
  - `POST /words/{wordId}/override-translation`
  - `POST /words/{wordId}/override-transliteration`
  - `POST /words/{wordId}/override-mnemonic-keyword`
  - `DELETE /words/{wordId}/clear-override/{type}`
- [ ] Add DTOs: `OverrideRequest` with `value` field
- [ ] Update `WordService` to handle override operations
- [ ] Add override checking logic to all generation services

#### 2.2.2: Override UI
- [ ] Add "Regen" button to each word card in `sessions/view.html`
- [ ] Create override modal/popup with three input fields:
  - Translation override input
  - Transliteration override input
  - Mnemonic keyword override input
- [ ] Show current values and override status
- [ ] Add visual indicators (badges/icons) for overridden fields
- [ ] Add "Clear Override" buttons for each field
- [ ] Wire up AJAX calls to override endpoints
- [ ] Show success/error feedback

### Phase 2.3: Granular Regeneration Operations

#### 2.3.1: New Regeneration Endpoints
Create separate endpoints in `TranslationSessionController`:
- [ ] `POST /sessions/{id}/generate-translations` - Generate translations only
- [ ] `POST /sessions/{id}/generate-transliterations` - Generate transliterations only
- [ ] `POST /sessions/{id}/generate-mnemonic-sentences` - Generate mnemonic sentences
- [ ] `POST /sessions/{id}/generate-mnemonic-keywords` - Generate mnemonic keywords
- [ ] `POST /sessions/{id}/generate-images` - Generate images with validation
- [ ] `POST /sessions/{id}/generate-audio` - Generate source audio only
- [ ] `POST /sessions/{id}/generate-example-sentences` - Generate example sentences

#### 2.3.2: Service Layer Updates
- [ ] Update `SessionOrchestrationService` to support selective operations
- [ ] Create methods:
  - `generateTranslationsSelectively()` - Skip override words
  - `generateTransliterationsSelectively()` - Skip override words
  - `generateMnemonicKeywordsSelectively()` - Skip override words
  - `generateMnemonicSentencesSelectively()` - Check prerequisites
  - `generateImagesSelectively()` - Validate prerequisites
  - `generateAudioSelectively()` - Source only
  - `generateExampleSentencesSelectively()` - Check prerequisites
- [ ] Add prerequisite validation helper methods:
  - `hasTranslation(WordEntity word)`
  - `hasMnemonicKeyword(WordEntity word)`
  - `hasMnemonicSentence(WordEntity word)`
  - `hasImagePrerequisites(WordEntity word)` - All three above
- [ ] Add override checking helper methods:
  - `isTranslationOverridden(WordEntity word)`
  - `isTransliterationOverridden(WordEntity word)`
  - `isMnemonicKeywordOverridden(WordEntity word)`

#### 2.3.3: Image Generation Enhancements
- [ ] Update `MnemonicGenerationService.generateImage()` to validate prerequisites
- [ ] Add `validateImagePrerequisites(WordEntity word)` method
- [ ] Return clear error messages for missing prerequisites
- [ ] Update `OpenAiImageService` to handle prerequisite errors gracefully
- [ ] Add bulk validation for session-level image generation
- [ ] Provide summary of skipped words (e.g., "Skipped 3 words: word1, word2, word3")

#### 2.3.4: Regeneration UI
- [ ] Replace existing regeneration buttons in `sessions/view.html` with new actions:
  - "Generate Translations" button
  - "Generate Transliterations" button
  - "Generate Mnemonic Sentences" button
  - "Generate Mnemonic Keywords" button
  - "Generate Images" button
  - "Generate Audio" button
  - "Generate Example Sentences" button
- [ ] Add loading states for each operation
- [ ] Show progress/results for each operation
- [ ] Display skipped words with reasons
- [ ] Add confirmation dialogs for destructive operations

### Phase 2.4: Custom Anki Note Type Integration

#### 2.4.1: AnkiConnect Service Enhancement
- [ ] Create `AnkiConnectService` (if not exists) or update existing
- [ ] Add methods:
  - `checkModelExists(String modelName)` - Uses `modelNames` action
  - `createCustomModel(String modelName, List<String> fieldNames, String template)` - Uses `createModel` action
  - `getOrCreateCustomModel(String modelName, AnkiFormatEntity format)` - Combined operation
- [ ] Implement template generation from `AnkiFormatEntity`
- [ ] Add toggle functionality JavaScript in template
- [ ] Handle AnkiConnect errors gracefully

#### 2.4.2: Note Type Creation Logic
- [ ] Update `AnkiCardBuilderService` to use custom note type
- [ ] Add method `ensureCustomNoteTypeExists(AnkiFormatEntity format)`
  - Check if note type exists
  - Create if not exists
  - Return note type name
- [ ] Map `AnkiCardItem` enum values to field names:
  - Create `getFieldName(AnkiCardItem item)` method
  - Return standardized field names (e.g., "SourceText", "Translation")
- [ ] Update `buildCardSide()` to generate field references for Anki template
  - Instead of building HTML content, build field placeholders
  - Example: `{{SourceText}}` instead of actual text

#### 2.4.3: Card Creation with Custom Note Type
- [ ] Update `createAnkiCards()` in `TranslationSessionController`
  - Call `ensureCustomNoteTypeExists()` before creating cards
  - Use returned note type name in `addNote` action
  - Map word data to field values
- [ ] Add validation: check all required fields exist for each word
  - Create `validateAnkiCardData(WordEntity word, AnkiFormatEntity format)` method
  - Return list of missing fields
  - Block card creation if any word has missing required fields
- [ ] Update Anki card preview to show how data maps to custom note type
- [ ] Add error handling for note type creation failures

#### 2.4.4: Template Generation
- [ ] Create `AnkiTemplateBuilder` utility class
- [ ] Generate card template HTML from `AnkiFormatEntity`:
  ```java
  String frontTemplate = buildTemplate(format.getFrontCardItems());
  String backTemplate = buildTemplate(format.getBackCardItems());
  ```
- [ ] Include CSS styling in template
- [ ] Add JavaScript for toggle functionality:
  ```javascript
  <script>
  function toggle(className) {
    var elements = document.getElementsByClassName(className);
    for (var i = 0; i < elements.length; i++) {
      elements[i].style.display =
        elements[i].style.display === 'none' ? 'block' : 'none';
    }
  }
  </script>
  ```
- [ ] Test template rendering in Anki desktop app

### Phase 2.5: Session Naming

#### 2.5.1: Backend Support
- [ ] Database migration already included in 2.1.3
- [ ] Update `TranslationSessionEntity` with `sessionName` field
- [ ] Add validation: max 200 characters, not empty
- [ ] Update session creation to accept `sessionName` parameter
- [ ] If not provided, default to first source word
- [ ] Add endpoint: `PUT /sessions/{id}/update-name` - Allow renaming

#### 2.5.2: UI Updates
- [ ] Add "Session Name" input field to generation page
- [ ] Placeholder: "Enter session name or leave blank to use first word"
- [ ] Display session name in session list
- [ ] Display session name in session view header
- [ ] Add "Edit" button next to session name in view page
- [ ] Create inline editing or modal for renaming
- [ ] Wire up to rename endpoint

### Phase 2.6: Words Pagination

#### 2.6.1: Backend Pagination Support
- [ ] Decide: In-memory pagination vs separate words table
  - **Option A**: Keep words in JSON, paginate in-memory (simple, no schema change)
  - **Option B**: Move words to separate table (better performance, more complex)
- [ ] If Option A (recommended for now):
  - Add pagination parameters to session view endpoint: `?wordPage=0&wordSize=20`
  - Extract words array from session_data JSON
  - Slice array based on page/size
  - Return paginated data + total count
- [ ] If Option B:
  - Create `session_words` table with FK to `translation_sessions`
  - Migrate existing words from JSON to table
  - Use Spring Data pagination

#### 2.6.2: UI Pagination
- [ ] Update `sessions/view.html` to accept pagination parameters
- [ ] Add pagination controls at top and bottom of word list
- [ ] Use existing `fragments/pagination` fragment
- [ ] Display "Words X-Y of Z" indicator
- [ ] Ensure word operations (override, regen) work with paginated view
- [ ] Preserve page number in URL when performing operations
- [ ] Test with sessions of varying sizes (1, 20, 50, 100+ words)

### Phase 2.7: Testing & Validation

#### 2.7.1: Unit Tests
- [ ] Test override logic in services
- [ ] Test prerequisite validation for image generation
- [ ] Test selective regeneration operations
- [ ] Test custom note type creation logic
- [ ] Test session name validation
- [ ] Test words pagination logic

#### 2.7.2: Integration Tests
- [ ] Test end-to-end regeneration flows
- [ ] Test override + regeneration interaction
- [ ] Test Anki card creation with custom note type
- [ ] Test session creation with custom name
- [ ] Test paginated word display
- [ ] Test error handling for missing prerequisites

#### 2.7.3: Manual Testing
- [ ] Create test session with 50+ words
- [ ] Test all 7 regeneration operations
- [ ] Set overrides on various words, verify skipping
- [ ] Generate images without prerequisites, verify error messages
- [ ] Create Anki cards, verify custom note type in Anki app
- [ ] Test session naming and renaming
- [ ] Navigate through paginated words
- [ ] Test responsive design on mobile

---

## Task List

### Milestone 1: Foundation & Cleanup (Week 1)

**Database Migrations**
- [ ] Create migration: Add `session_name` column to `translation_sessions`
- [ ] Create migration: Add `transliteration_override_at` column to `words`
- [ ] Create migration: Remove obsolete columns from `translation_sessions`:
  - `status`, `completed_at`, `cancelled_at`
  - `image_generation_enabled`, `audio_generation_enabled`, `sentence_generation_enabled`, `override_translation_enabled`
- [ ] Run migrations on dev database
- [ ] Test rollback procedures

**Target Audio Removal**
- [ ] Remove `TARGET_AUDIO`, `SENTENCE_TARGET`, `SENTENCE_TARGET_AUDIO` from `AnkiCardItem.java`:55
- [ ] Update `AnkiCardBuilderService.java:buildCardSide()` to skip removed items
- [ ] Remove target audio code mappings from `LanguageUtil.java` (if exists)
- [ ] Remove target audio generation from `AsyncAudioGenerationService.java`
- [ ] Search codebase for "target audio" references and remove
- [ ] Update `sessions/view.html` to remove target audio UI elements
- [ ] Test audio generation still works for source language

**Obsolete Feature Removal**
- [ ] Remove `POST /sessions/{id}/regenerate-with-same-preset` endpoint from `TranslationSessionController.java`
- [ ] Remove `POST /sessions/{id}/fix-stuck` endpoint from `TranslationSessionController.java`
- [ ] Remove `GET /sessions/{id}/download-assets` endpoint from `TranslationSessionController.java`
- [ ] Remove corresponding service methods from `TranslationSessionService.java`
- [ ] Remove "Generate with Same Preset" button from `sessions/view.html`
- [ ] Remove "Fix Stuck Sessions" button from `sessions/view.html`
- [ ] Remove "Download Assets" button from `sessions/view.html`
- [ ] Remove "Full Session Data Section" from `sessions/view.html`
- [ ] Clean up related JavaScript/CSS

**Session Model Simplification**
- [ ] Update `TranslationSessionEntity.java` - remove obsolete fields
- [ ] Add `sessionName` field to `TranslationSessionEntity.java`
- [ ] Update `TranslationSessionService.java` to handle simplified model
- [ ] Remove status filtering from session list queries
- [ ] Update session creation logic to use `sessionName`
- [ ] Add fallback: use first word if no name provided
- [ ] Test session CRUD operations with new model

### Milestone 2: Enhanced Override System (Week 2)

**Backend Override Infrastructure**
- [ ] Update `WordEntity.java` - add `transliterationOverrideAt` field
- [ ] Create `OverrideRequest` DTO with `value` field
- [ ] Create `POST /words/{wordId}/override-translation` endpoint in `WordController.java`
- [ ] Create `POST /words/{wordId}/override-transliteration` endpoint in `WordController.java`
- [ ] Create `POST /words/{wordId}/override-mnemonic-keyword` endpoint in `WordController.java`
- [ ] Create `DELETE /words/{wordId}/clear-override/{type}` endpoint in `WordController.java`
- [ ] Update `WordService.java` with override methods:
  - `setTranslationOverride()`
  - `setTransliterationOverride()`
  - `setMnemonicKeywordOverride()`
  - `clearOverride()`
- [ ] Add validation for override inputs
- [ ] Test override endpoints

**Override Checking in Generation Services**
- [ ] Create `OverrideCheckService.java` with methods:
  - `isTranslationOverridden(WordEntity word)`
  - `isTransliterationOverridden(WordEntity word)`
  - `isMnemonicKeywordOverridden(WordEntity word)`
- [ ] Update `TranslationService.java` to check translation overrides before generating
- [ ] Update `MnemonicGenerationService.java` to check keyword overrides
- [ ] Add logging when words are skipped due to overrides
- [ ] Test override checking logic

**Override UI Implementation**
- [ ] Add "Regen" button to each word card in `sessions/view.html`
- [ ] Create override modal HTML with three sections:
  - Translation override form
  - Transliteration override form
  - Mnemonic keyword override form
- [ ] Add JavaScript for modal open/close
- [ ] Show current values in modal
- [ ] Add visual indicators (badges) for overridden fields on word cards
- [ ] Implement AJAX calls to override endpoints
- [ ] Handle success/error responses
- [ ] Refresh word card display after override
- [ ] Add "Clear Override" functionality
- [ ] Test override UI flow end-to-end

### Milestone 3: Granular Regeneration (Week 3-4)

**Prerequisite Validation Helpers**
- [ ] Create `PrerequisiteValidationService.java` with methods:
  - `hasTranslation(WordEntity word)`
  - `hasTransliteration(WordEntity word)`
  - `hasMnemonicKeyword(WordEntity word)`
  - `hasMnemonicSentence(WordEntity word)`
  - `hasImagePrerequisites(WordEntity word)` - checks all three
  - `validateForImageGeneration(List<WordEntity> words)` - bulk check
- [ ] Add detailed error messages for missing prerequisites
- [ ] Test prerequisite validation logic

**New Regeneration Endpoints**
- [ ] Create `POST /sessions/{id}/generate-translations` in `TranslationSessionController.java`
- [ ] Create `POST /sessions/{id}/generate-transliterations` in `TranslationSessionController.java`
- [ ] Create `POST /sessions/{id}/generate-mnemonic-keywords` in `TranslationSessionController.java`
- [ ] Create `POST /sessions/{id}/generate-mnemonic-sentences` in `TranslationSessionController.java`
- [ ] Create `POST /sessions/{id}/generate-images` in `TranslationSessionController.java`
- [ ] Create `POST /sessions/{id}/generate-audio` in `TranslationSessionController.java`
- [ ] Create `POST /sessions/{id}/generate-example-sentences` in `TranslationSessionController.java`
- [ ] Add request validation and error handling
- [ ] Test each endpoint individually

**Selective Generation Service Methods**
- [ ] Create `SelectiveGenerationService.java` (or update `SessionOrchestrationService.java`)
- [ ] Implement `generateTranslationsSelectively()`
  - Load session words
  - Filter out override words
  - Call translation service for remaining words
  - Return summary (generated, skipped)
- [ ] Implement `generateTransliterationsSelectively()`
- [ ] Implement `generateMnemonicKeywordsSelectively()`
  - Skip if translation overridden
- [ ] Implement `generateMnemonicSentencesSelectively()`
  - Validate mnemonic keyword exists
  - Skip if keyword overridden
- [ ] Implement `generateImagesSelectively()`
  - Validate prerequisites for each word
  - Skip words missing prerequisites
  - Use word-specific image style
  - Return list of skipped words with reasons
- [ ] Implement `generateAudioSelectively()`
  - Only generate source audio
  - Use existing batch generation logic
- [ ] Implement `generateExampleSentencesSelectively()`
  - Validate translation exists
  - Generate sentence and audio
- [ ] Add comprehensive logging for each operation
- [ ] Test selective generation logic

**Image Generation Enhancement**
- [ ] Update `MnemonicGenerationService.generateImage()` to call prerequisite validation
- [ ] Return clear error if prerequisites missing
- [ ] Update `OpenAiImageService` to handle validation errors
- [ ] Add batch validation for session-level generation
- [ ] Return summary: "Generated X images, skipped Y words (reasons)"
- [ ] Test image generation with missing prerequisites

**Regeneration UI**
- [ ] Update `sessions/view.html` - replace old regeneration buttons
- [ ] Add new buttons:
  - "Generate Translations"
  - "Generate Transliterations"
  - "Generate Mnemonic Sentences"
  - "Generate Mnemonic Keywords"
  - "Generate Images"
  - "Generate Audio"
  - "Generate Example Sentences"
- [ ] Add loading spinners for each operation
- [ ] Implement AJAX calls to new endpoints
- [ ] Display operation results (success count, skipped count)
- [ ] Show detailed list of skipped words with reasons (expandable section)
- [ ] Add confirmation dialogs for potentially destructive operations
- [ ] Style buttons with icons for clarity
- [ ] Test UI responsiveness and error handling

### Milestone 4: Custom Anki Note Type (Week 5)

**AnkiConnect Service**
- [ ] Create `AnkiConnectService.java` (or enhance existing)
- [ ] Implement `checkModelExists(String modelName)` using `modelNames` action
- [ ] Implement `createCustomModel(String modelName, List<String> fieldNames, CardTemplate template)` using `createModel` action
- [ ] Create `CardTemplate` class to hold front/back HTML
- [ ] Add error handling for AnkiConnect connection failures
- [ ] Test AnkiConnect integration with local Anki instance

**Note Type Field Mapping**
- [ ] Create `AnkiFieldMapper.java` utility
- [ ] Implement `getFieldName(AnkiCardItem item)` method:
  - `SOURCE_TEXT` → "SourceText"
  - `SOURCE_AUDIO` → "SourceAudio"
  - `TRANSLATION` → "Translation"
  - `TRANSLITERATION` → "Transliteration"
  - `MNEMONIC_KEYWORD` → "MnemonicKeyword"
  - `MNEMONIC_SENTENCE` → "MnemonicSentence"
  - `IMAGE` → "Image"
  - `SENTENCE_SOURCE` → "SentenceSource"
  - `SENTENCE_SOURCE_AUDIO` → "SentenceSourceAudio"
  - `SENTENCE_TRANSLATION` → "SentenceTranslation"
- [ ] Create `getAllFieldsForFormat(AnkiFormatEntity format)` method
- [ ] Test field mapping logic

**Template Generation**
- [ ] Create `AnkiTemplateBuilder.java`
- [ ] Implement `buildTemplate(List<AnkiCardItem> items)` method
  - Generate HTML with Anki field placeholders: `{{FieldName}}`
  - Add conditional rendering: `{{#FieldName}}...{{/FieldName}}`
  - Include CSS styling
- [ ] Add toggle functionality JavaScript:
  ```java
  private String getToggleScript() {
    return "<script>" +
           "function toggle(className) {" +
           "  var elements = document.getElementsByClassName(className);" +
           "  for (var i = 0; i < elements.length; i++) {" +
           "    elements[i].style.display = " +
           "      elements[i].style.display === 'none' ? 'block' : 'none';" +
           "  }" +
           "}" +
           "</script>";
  }
  ```
- [ ] Implement `buildCardTemplate(AnkiFormatEntity format)` - generates full template
- [ ] Test template generation with various formats

**Note Type Creation Logic**
- [ ] Create `ensureCustomNoteTypeExists(AnkiFormatEntity format)` in `AnkiCardBuilderService.java`
  - Check if note type exists
  - If not, create with all fields and template
  - Return note type name
- [ ] Add caching to avoid repeated existence checks
- [ ] Handle errors if note type creation fails
- [ ] Test note type creation in Anki

**Card Creation with Validation**
- [ ] Create `validateWordForAnki(WordEntity word, AnkiFormatEntity format)` method
  - Check each required field exists
  - Return list of missing fields
- [ ] Create `validateAllWordsForAnki(List<WordEntity> words, AnkiFormatEntity format)` method
  - Aggregate missing fields per word
  - Return validation result with details
- [ ] Update `POST /sessions/{id}/create-anki-cards` endpoint:
  - Call validation before creation
  - Block creation if any word has missing fields
  - Return clear error message listing words and missing fields
- [ ] Update card creation to use custom note type:
  - Call `ensureCustomNoteTypeExists()`
  - Use returned note type name in `addNote` action
  - Map word data to field values
- [ ] Test end-to-end card creation

**Anki Preview Update**
- [ ] Update `GET /sessions/{id}/anki-cards-preview` endpoint
  - Show how data maps to custom note type fields
  - Display field names and values
- [ ] Update preview UI in `sessions/view.html`
  - Show note type name
  - Display field-by-field preview
- [ ] Test preview functionality

### Milestone 5: Session Naming & Words Pagination (Week 6)

**Session Naming Backend**
- [ ] Confirm `sessionName` field exists in `TranslationSessionEntity.java` (from Milestone 1)
- [ ] Add validation: max 200 characters
- [ ] Update session creation endpoint to accept `sessionName` parameter
- [ ] Implement default: use first source word if name not provided
- [ ] Create `PUT /sessions/{id}/update-name` endpoint
  - Validate new name
  - Update session
  - Return updated session
- [ ] Test session naming logic

**Session Naming UI**
- [ ] Add "Session Name" input field to generation page (`templates/generate.html` or equivalent)
- [ ] Add placeholder: "Enter session name or leave blank to use first word"
- [ ] Pass session name on form submission
- [ ] Display session name in `sessions/list.html` table
- [ ] Display session name as header in `sessions/view.html`
- [ ] Add "Edit" icon/button next to session name in view page
- [ ] Implement inline editing or modal for renaming
- [ ] Wire up to `PUT /sessions/{id}/update-name` endpoint
- [ ] Test session naming flow end-to-end

**Words Pagination Backend**
- [ ] Decide on pagination approach: in-memory (recommended)
- [ ] Update `GET /sessions/{id}` endpoint to accept parameters:
  - `wordPage` (default 0)
  - `wordSize` (default 20)
- [ ] Extract words array from session_data JSON
- [ ] Implement pagination logic:
  - Calculate start index: `wordPage * wordSize`
  - Calculate end index: `min(start + wordSize, total)`
  - Slice words array
- [ ] Return paginated data:
  - `words`: paginated list
  - `totalWords`: total count
  - `currentPage`: wordPage
  - `totalPages`: calculated
- [ ] Test pagination logic with various session sizes

**Words Pagination UI**
- [ ] Update `sessions/view.html` to accept pagination data
- [ ] Add pagination controls at top of word list
- [ ] Add pagination controls at bottom of word list
- [ ] Use `fragments/pagination` fragment
- [ ] Update pagination links to use `?wordPage=N` parameter
- [ ] Display "Words X-Y of Z" indicator
- [ ] Ensure override/regen operations preserve page number:
  - Add `wordPage` hidden input to forms
  - Redirect back to same page after operation
- [ ] Test pagination UI with various page sizes
- [ ] Test with sessions of 1, 20, 50, 100+ words

### Milestone 6: Testing & Quality Assurance (Week 7)

**Unit Tests**
- [ ] Test `OverrideCheckService` methods
- [ ] Test `PrerequisiteValidationService` methods
- [ ] Test `SelectiveGenerationService` methods
- [ ] Test `AnkiFieldMapper` field name generation
- [ ] Test `AnkiTemplateBuilder` template generation
- [ ] Test session name validation
- [ ] Test words pagination logic
- [ ] Achieve 80%+ code coverage for new code

**Integration Tests**
- [ ] Test override endpoint → regeneration skipping flow
- [ ] Test prerequisite validation → image generation flow
- [ ] Test custom note type creation → card creation flow
- [ ] Test session creation with custom name
- [ ] Test words pagination with operations (override, regen)
- [ ] Test error handling scenarios:
  - AnkiConnect unavailable
  - Missing prerequisites
  - Invalid overrides

**Manual Testing**
- [ ] Create test session with 50 words
- [ ] Test each of 7 regeneration operations individually
- [ ] Set overrides on 10 words, verify they're skipped in regeneration
- [ ] Test image generation with missing prerequisites
  - Verify clear error messages
  - Verify skipped words list
- [ ] Create Anki cards with custom note type
  - Verify note type created in Anki
  - Verify cards display correctly
  - Test toggle functionality
- [ ] Test session naming:
  - Create with custom name
  - Create without name (verify default)
  - Rename existing session
- [ ] Test words pagination:
  - Navigate all pages
  - Perform override on page 2, verify stays on page 2
  - Test with 1-word session
  - Test with 100-word session
- [ ] Test responsive design on mobile/tablet
- [ ] Test with multiple browsers (Chrome, Firefox, Safari)

**Performance Testing**
- [ ] Benchmark session load time with 100 words paginated vs unpaginated
- [ ] Benchmark regeneration operations with 50+ words
- [ ] Test concurrent regeneration operations
- [ ] Verify no memory leaks with large sessions

**Documentation**
- [ ] Update API documentation with new endpoints
- [ ] Document override system behavior
- [ ] Document prerequisite validation rules
- [ ] Document Anki note type structure
- [ ] Update user guide with new features
- [ ] Add inline code comments for complex logic

---

## Risk Assessment & Mitigation

### High Risk Areas

**1. Anki Integration Complexity**
- **Risk**: Custom note type creation may fail or behave unexpectedly
- **Mitigation**:
  - Thorough testing with various Anki versions
  - Fallback to basic note type if custom creation fails
  - Clear error messages for users
  - Document Anki version requirements

**2. Data Migration for Session Model**
- **Risk**: Removing columns may cause data loss or application errors
- **Mitigation**:
  - Create backup before migration
  - Test migration on copy of production database
  - Add rollback script
  - Consider soft-delete (keep columns, deprecate in code) initially

**3. Override Logic Complexity**
- **Risk**: Incorrectly skipping or generating overridden content
- **Mitigation**:
  - Comprehensive unit tests for override checking
  - Clear logging for skipped words
  - User-visible confirmation of skipped words
  - Manual testing with various override combinations

### Medium Risk Areas

**4. Words Pagination Performance**
- **Risk**: In-memory pagination may be slow for very large sessions (500+ words)
- **Mitigation**:
  - Monitor performance with large sessions
  - Consider moving to separate table if performance degrades
  - Add indexes if using separate table
  - Set reasonable session size limits

**5. UI Complexity Increase**
- **Risk**: Seven regeneration buttons may overwhelm users
- **Mitigation**:
  - Group related operations (dropdown menus)
  - Add help text/tooltips
  - Consider a "wizard" for guided regeneration
  - Collect user feedback early

---

## Success Criteria

### Functional Requirements
- [ ] All 7 regeneration operations work independently
- [ ] Override system correctly skips overridden fields
- [ ] Image generation validates prerequisites and skips appropriately
- [ ] Custom Anki note type created successfully in Anki
- [ ] Anki cards use custom note type with all fields
- [ ] Session naming works on creation and editing
- [ ] Words pagination displays correctly and preserves state during operations
- [ ] No target audio references remain in codebase
- [ ] All obsolete features removed

### Quality Requirements
- [ ] 80%+ unit test coverage for new code
- [ ] All integration tests passing
- [ ] No critical bugs in manual testing
- [ ] Page load time <2 seconds for sessions with 100 words
- [ ] Regeneration operations complete within reasonable time (e.g., <30s for 50 words)
- [ ] Mobile responsive design maintained

### User Experience Requirements
- [ ] Clear visual feedback for all operations
- [ ] Error messages are actionable and specific
- [ ] Override system is intuitive and easy to use
- [ ] Regeneration results clearly show successes and skips
- [ ] Session naming is discoverable and easy to edit
- [ ] Pagination doesn't disrupt workflow

---

## Post-Launch

### Monitoring
- Monitor error logs for Anki integration failures
- Track regeneration operation usage (which are most popular)
- Monitor session sizes to inform pagination performance
- Collect user feedback on new override UI

### Future Enhancements (Phase 3)
- Bulk override operations (apply override to multiple words at once)
- Override templates (save common overrides for reuse)
- Advanced prerequisite rules (configurable dependencies)
- Custom note type templates (user-defined Anki templates)
- Separate words table for better performance and querying
- Export/import sessions with overrides
- Undo/redo for override operations
- History of regenerated content (version control for words)

---

## Timeline Summary

| Milestone | Duration | Key Deliverables |
|-----------|----------|------------------|
| **M1: Foundation & Cleanup** | Week 1 | Database migrations, target audio removed, obsolete features removed, session model simplified |
| **M2: Enhanced Override System** | Week 2 | Override endpoints, override checking in services, override UI |
| **M3: Granular Regeneration** | Week 3-4 | 7 new regeneration endpoints, selective generation logic, prerequisite validation, regeneration UI |
| **M4: Custom Anki Note Type** | Week 5 | AnkiConnect service, template generation, note type creation, card validation |
| **M5: Session Naming & Pagination** | Week 6 | Session naming UI/backend, words pagination UI/backend |
| **M6: Testing & QA** | Week 7 | Unit tests, integration tests, manual testing, performance testing, documentation |

**Total Duration**: 7 weeks

---

## Conclusion

Phase 2 represents a significant refinement of the language learning application, focusing on:
1. **User Control**: Granular regeneration and per-word overrides
2. **Simplicity**: Removing unnecessary complexity (session status, obsolete features)
3. **Integration**: Professional Anki integration with custom note types
4. **Usability**: Session naming and paginated word management

By following this plan, the application will be more intuitive, maintainable, and powerful for users creating language learning content.
