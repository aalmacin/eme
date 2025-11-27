# New Feature: Image Generation for Flashcards

## Overview
Add Leonardo API integration for generating mnemonic images for flashcards with character-based visual associations.

## Requirements

### 1. Leonardo API Integration
- Use Leonardo API for image generation
- Image quality: medium
- Model: leonardo-diffusion
- Style: cinematic
- Dimensions: 1152 x 768

### 2. Character Guide Page
- Allow users to customize characters and context for sound combinations
- Each language (Target/Source) has its own character mappings
- Format: (start sound, character, context)
- Example mappings:
  - Hindi "sh" -> "shanks" from "One Piece"
  - English "si" -> "Cece" from "Pretty Little Liars"

### 3. OpenAI Integration for Mnemonic Generation
- OpenAI generates mnemonic data before image generation
- Input: word translation + character mappings
- Output JSON format:
```json
[
  {
    "mnemonic_keyword": "...",
    "mnemonic_sentence": "...",
    "image_prompt": "..."
  }
]
```

### 4. Example
**Word**: shahar (Hindi) -> city (English)

**Mnemonic Keyword**: Shaker

**Mnemonic Sentence**: Shanks (One Piece) and Cece (Pretty Little Liars) meet in a vibrant city where a giant cocktail shaker monument stands in the town square.

**Image Prompt**: A dynamic 3D animated modern city plaza during golden hour. At the center, a bustling cityscape with skyscrapers, shops, pedestrian walkways, and urban architecture represents "city." On the left, Shanks from the anime One Piece walks confidently with his signature red hair and cape flowing, exuding his pirate captain charisma. On the right, Cece from the show Pretty Little Liars strolls elegantly in fashionable city attire, carrying a designer bag. In the city plaza stands a massive artistic cocktail shaker sculpture made of polished steel, serving as a landmark fountain. People walk by, trees line the streets, and city lights begin to glow. The atmosphere is energetic, cosmopolitan, and full of life. No text, speech bubbles, or visible words appear anywhere in the image.

### 5. Translate Page Updates
- Add checkbox to toggle image generation (similar to existing feature toggles)

### 6. Anki Card Generation
- Support new placeholders:
  - `[image]` -> replaced with `<img src="image-filename" />`
  - `[mnemonic_keyword]` -> replaced with mnemonic keyword
  - `[mnemonic_sentence]` -> replaced with mnemonic sentence

### 7. Image File Management
- Filename derived from mnemonic sentence
- Sanitization rules:
  - Remove periods and special characters
  - Replace spaces with underscores
  - Make file-safe
  - Example: "shanks_and_cece_meet_in_a_continued.jpg"
- Store in GCP Cloud Storage as backup
- **Do not reference from cloud storage directly**
- Provide downloadable zip file after processing

### 8. Audio File Handling Update
- Update audio generation to match new async pattern
- Include in downloadable zip instead of waiting for process to finish

### 9. Translation Sessions (New Entity)
- Make translation process async
- Create new "Translation Sessions" entity to track:
  - Translation status
  - Generated assets (images, audio, cards)
  - Download links
- Add Translation Sessions page where users can:
  - View all translation sessions
  - Check status
  - Download assets (zip files)
- Anki card creation proceeds as normal (synchronously or as before)

## Technical Implementation Notes
- Each object in OpenAI's JSON response triggers one Leonardo API image generation
- Images generated before Anki card creation
- All assets (images, audio, cards) bundled into downloadable zip
- GCP storage used for backup/archival purposes only
