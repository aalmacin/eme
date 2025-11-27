# Image Prompt Creation Rules

This document outlines the rules and requirements for generating mnemonic keywords, sentences, and image prompts for language learning.

## Overview

The system creates memory aids (mnemonics) to help learners remember word translations using:
1. **Character Guide** - A familiar character whose name sounds like the source word
2. **Mnemonic Keyword** - A visualizable English word that sounds like the source word
3. **Mnemonic Sentence** - A memorable sentence connecting all elements
4. **Image Prompt** - A detailed description for generating a visual representation

## 1. Character Guide Lookup

### Lookup Strategy
After receiving the source word and its transliteration, the system looks for a character guide entry:

1. **Strip accents** from transliteration (e.g., `mā` → `ma`, `liye` → `liye`)
2. **Try 3 characters**: Look for character guide matching first 3 chars (e.g., `liy`)
3. **Try 2 characters**: If not found, try first 2 chars (e.g., `li`)
4. **Try 1 character**: If not found, try first 1 char (e.g., `l`)
5. **Fail if not found**: If no match found, the generation fails with an error

### Error Handling
If no character guide is found, the system throws an error:
```
No character guide found for 'l' (from word: लिए, transliteration: liye).
Please add a character guide for this sound.
```

This prompts the user to add a character guide entry before proceeding.

### Character Guide Database
- **Fields**: `language`, `start_sound`, `character_name`, `character_context`
- **Example**: `hi`, `li`, `Lisa Soberano`, `Filipino Celebrity`

## 2. Mnemonic Keyword Selection

### Phonetic Matching Priority
The keyword must sound like the **SOURCE word** (not translation):

1. **First syllable SOUND match** (highest priority)
   - Example: `liye` → `leaf` (matches "li" sound)
   - Example: `ma` → `mat` (matches "ma" sound)

2. **First syllable TEXT match**
   - Example: `ma` → `mat` (matches "ma" text)

3. **First LETTER match** (lowest priority)
   - Example: `m` → `moon` (matches "m" letter)

### Keyword Requirements
- ✅ **MUST** be phonetically similar to SOURCE word
- ✅ **MUST** be a concrete, visualizable object (noun) OR visualizable/gesturable action (verb/adjective)
- ✅ **MUST** be a real English word
- ❌ **CANNOT** be abstract concepts (unless gestures can convey it)
- ❌ **CANNOT** be the source word itself
- ❌ **CANNOT** be the translation word itself

### Examples

**Good Keywords:**
- `leaf` - concrete object, matches "li" sound from "liye"
- `hoping` - visualizable gesture (hands together, hopeful expression)
- `mat` - concrete object, matches "ma" sound
- `mall` - concrete place, matches "ma" sound

**Bad Keywords:**
- `hope` - too abstract
- `love` - abstract concept (unless shown through actions)
- Words that don't match source phonetically
- Non-English words

## 3. Mnemonic Sentence Structure

### Required Elements
A simple, memorable sentence that includes ALL of these:

1. **Character** from character guide + context
   - Example: "Lisa Soberano"

2. **Mnemonic keyword** incorporated as object/action
   - Example: "4 leaf clover"

3. **2-10 additional items** that sound like OR start with same letter as source OR translation
   - Example: For `liye` → `for`: "foreman", "ford", "4"

4. **Setting** that sounds like source or translation
   - Example: "leafy forest" (sounds like "liye")

5. **Facial and body expressions**
   - Example: "smiling while holding"
   - Example: "put their index finger horizontally under her nostrils to cover smell"

### Example

**Source:** लिए (liye)
**Translation:** for
**Mnemonic Keyword:** leaf
**Mnemonic Sentence:**
```
Lisa Soberano gave 4 leaf clover for the foreman who has a ford,
in a leafy forest.
```

**Elements breakdown:**
- Character: Lisa Soberano (matches "li")
- Keyword: leaf
- Additional items: 4, foreman, ford (sound like "for")
- Setting: leafy forest (sounds like "liye")

## 4. Image Prompt Structure

### Primary Focus
**The image MUST focus on the TRANSLATION meaning.**

The character should be performing an action or in a scene that demonstrates what the translation means.

**Example:** For "to become", show the character becoming something (e.g., becoming a leader, transformation scene)

### Required Elements

1. **CHARACTER**
   - Use the character from the character guide
   - Example: Lisa Soberano from Filipino Celebrity

2. **MNEMONIC KEYWORD**
   - Represented visually through objects in the scene
   - Example: If keyword is "leaf", include leaves or leafy elements

3. **ADDITIONAL ITEMS**
   - 2-10 items matching phonetic sound or starting letter of source OR translation
   - Example: For `liye` → `for`, include "foreman", "ford", "4"

4. **SETTING**
   - Environment that sounds like source or translation
   - Example: "leafy forest" for "liye"

5. **FACIAL & BODY EXPRESSIONS**
   - Show clear emotions and body language
   - Examples:
     - "smiling while holding"
     - "index finger under nostrils"
     - "hopeful gesture with hands together"

### Composition Guidelines
- Character is the main focus performing the translation action/meaning
- Include mnemonic keyword object visible in the scene
- Include 2-10 additional items for memory anchoring
- Dynamic, vibrant atmosphere
- NO additional people or characters beyond the one specified

### Critical Restrictions

**ABSOLUTELY NO TEXT in the image:**
- ❌ NO words, labels, signs, or captions anywhere
- ❌ Source word MUST NEVER appear in the image
- ❌ NO speech bubbles or written words of any kind
- ❌ NO book titles (use "book" instead of "book titled X")
- ❌ Describe objects WITHOUT mentioning any text that would appear on them

### Safety Requirements
- Keep scene family-friendly and appropriate for all ages
- NO violence, weapons, fighting, blood, or injuries
- NO suggestive poses, romantic scenarios, or intimate situations
- Focus on safe, positive, everyday activities and interactions
- Character should be engaged in wholesome, educational activities

## Example: Complete Workflow

### Input
- **Source Word:** लिए
- **Transliteration:** liye
- **Translation:** for
- **Language:** Hindi (hi)

### Step 1: Character Guide Lookup
1. Strip accents: `liye` (no accents)
2. Try "liy" - not found
3. Try "li" - **FOUND**: Lisa Soberano (Filipino Celebrity)

### Step 2: Generate Mnemonic Keyword
- Phonetic match: `liye` → `leaf` (matches "li" sound)
- Visualizable: ✅ Yes (concrete object)

### Step 3: Generate Mnemonic Sentence
```
Lisa Soberano gave 4 leaf clover for the foreman who has a ford,
in a leafy forest.
```

**Elements:**
- Character: Lisa Soberano ✅
- Keyword: leaf ✅
- Additional items: 4, foreman, ford ✅
- Setting: leafy forest ✅
- Expression: (could add: "smiling warmly")

### Step 4: Generate Image Prompt
```
In a vibrant leafy forest during golden hour, Lisa Soberano (Filipino Celebrity)
is smiling warmly while giving a 4-leaf clover to a foreman who is standing next
to a Ford vehicle. The forest is filled with lush green leaves, and there are
multiple 4-leaf clovers scattered on the ground. The scene emphasizes the act of
giving 'for' someone, with Lisa's generous expression and outstretched hand.
Realistic cinematic style with soft natural lighting filtering through the trees.
```

**Elements:**
- Character: Lisa Soberano ✅
- Keyword: leaf (4-leaf clover, leafy forest) ✅
- Additional items: 4, foreman, ford ✅
- Setting: leafy forest ✅
- Expression: smiling warmly, generous expression, outstretched hand ✅
- Focus: act of giving "for" someone ✅
- No text ✅

## Common Mistakes to Avoid

### 1. Wrong Character Guide
❌ **Wrong:** Using "Naruto" when character guide is "Lisa Soberano"
✅ **Correct:** Always use the character from the database lookup

### 2. Keyword Not Phonetic to Source
❌ **Wrong:** For `liye`, using "gift" (matches translation "for", not source)
✅ **Correct:** For `liye`, using "leaf" (matches source "li" sound)

### 3. Abstract Keywords
❌ **Wrong:** "hope", "love", "freedom" (too abstract)
✅ **Correct:** "hoping" (gesture), "heart" (object), "bird" (object)

### 4. Missing Sentence Elements
❌ **Wrong:** "Naruto is searching for a leaf."
✅ **Correct:** "Lisa Soberano gave 4 leaf clover for the foreman who has a ford, in a leafy forest."

### 5. Image Not Focused on Translation
❌ **Wrong:** Image focused on the leaf (keyword)
✅ **Correct:** Image focused on the act of giving "for" someone, with leaf present

### 6. Text in Image
❌ **Wrong:** "Lisa holding a sign that says 'for'"
✅ **Correct:** "Lisa giving something to someone" (no text)

## Implementation Notes

### Code Files
- `CharacterGuideService.java` - Handles character guide lookup with 3→2→1 fallback
- `MnemonicGenerationService.java` - Generates keywords, sentences, and image prompts
- `OpenAiImageService.java` - Creates images from prompts

### Error Handling
If character guide not found, the system throws:
```java
throw new RuntimeException("No character guide found for '" + prefix +
    "' (from word: " + sourceWord + ", transliteration: " + transliteration +
    "). Please add a character guide for this sound.");
```

The UI should display this error and provide an "Add character guide" modal.

### Accent Stripping
The system strips all diacritical marks before lookup:
- `mā` → `ma`
- `é` → `e`
- `ñ` → `n`
- `लिए` → use transliteration `liye`

## Summary

1. **Character Guide**: Must exist in database (3→2→1 char lookup)
2. **Mnemonic Keyword**: Phonetically matches SOURCE word, must be visualizable
3. **Mnemonic Sentence**: Simple structure with character, keyword, 2-10 items, setting, expressions
4. **Image Prompt**: Focuses on TRANSLATION meaning, includes all elements, NO TEXT

Following these rules ensures consistent, high-quality mnemonics that help learners remember word translations effectively.
