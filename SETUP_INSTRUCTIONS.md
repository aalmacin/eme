# Setup Instructions for Image Generation Feature

## Step 1: Update build.gradle

Add the Google Cloud Storage dependency to your `build.gradle` file:

```gradle
dependencies {
    // ... existing dependencies ...

    // Google Cloud Storage (required for image backup)
    implementation 'com.google.cloud:google-cloud-storage:2.29.1'

    // Ensure you have Spring Web (likely already present)
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

## Step 2: Set Environment Variables

Create or update your environment variables:

```bash
# Leonardo API
export LEONARDO_API_KEY="your-leonardo-api-key-here"

# GCP Storage
export GCP_STORAGE_BUCKET_NAME="eme-flashcard-images"

# Image Output Directory (optional, defaults to ./generated_images)
export IMAGE_OUTPUT_DIR="./generated_images"

# Existing variables (keep these)
export OPENAI_API_KEY="your-openai-key"
export ANKI_CONNECT_API_URL="http://localhost:8765"
```

## Step 3: Google Cloud Setup

### 3.1 Authenticate with Google Cloud
```bash
gcloud auth application-default login
```

### 3.2 Create Storage Bucket
```bash
# Create bucket
gcloud storage buckets create gs://eme-flashcard-images --location=us-central1

# Set bucket permissions (make it private)
gcloud storage buckets update gs://eme-flashcard-images --uniform-bucket-level-access
```

## Step 4: Leonardo AI Setup

### 4.1 Get API Key
1. Go to https://leonardo.ai/
2. Sign up or log in
3. Navigate to API section
4. Generate API key
5. Copy the key and set it as `LEONARDO_API_KEY` environment variable

### 4.2 Verify Model ID (Optional)
The code uses model ID: `ac614f96-1082-45bf-be9d-757f2d31c174` (leonardo-diffusion-xl)

If you need to use a different model:
1. Update `LeonardoApiService.java` line ~72
2. Change the `modelId` value

## Step 5: Database Migration

The database migration will run automatically on startup if Flyway is enabled. Or run manually:

```bash
./gradlew flywayMigrate
```

This creates:
- `character_guide` table
- `translation_sessions` table

## Step 6: Build and Run

```bash
# Clean build
./gradlew clean build

# Run application
./gradlew bootRun
```

## Step 7: Setup Character Guide

1. Navigate to `http://localhost:8082/character-guide`
2. Click "Add New Character"
3. Add character associations:

### Example for Hindi:
| Language | Start Sound | Character Name | Context |
|----------|-------------|----------------|---------|
| hi       | sh          | Shanks         | One Piece |
| hi       | ka          | Kakashi        | Naruto |
| hi       | sa          | Sasuke         | Naruto |

### Example for English:
| Language | Start Sound | Character Name | Context |
|----------|-------------|----------------|---------|
| en       | si          | Cece           | Pretty Little Liars |
| en       | ci          | Cece           | Pretty Little Liars |
| en       | ma          | Mario          | Super Mario |

## Step 8: Test the Feature

1. Go to `http://localhost:8082/`
2. Enter a word to translate (e.g., "shahar" Hindi → English)
3. Check "Generate Mnemonic Images" checkbox
4. Submit
5. Anki cards will be created immediately
6. Go to `/sessions` to check image generation status
7. Download ZIP when complete

## Troubleshooting

### Compilation Errors
**Problem**: Cannot resolve `Storage`, `Blob`, `BlobId` classes

**Solution**: Add Google Cloud Storage dependency to `build.gradle` (see Step 1)

### Leonardo API Timeout
**Problem**: Image generation takes too long

**Solution**: Increase timeout in `LeonardoApiService.java` line ~94:
```java
int maxAttempts = 120; // Increase from 60 to 120 (4 minutes)
```

### GCP Authentication Error
**Problem**: Cannot access GCS bucket

**Solution**: Run `gcloud auth application-default login` again

### Image Generation Fails
**Problem**: Session status shows FAILED

**Solution**:
1. Check Leonardo API key is valid
2. Check Leonardo API credits
3. Review application logs for detailed error
4. Verify character guide has entries for the language

## File Structure

After setup, your project should have:

```
eme/
├── build.gradle (updated with GCS dependency)
├── src/main/
│   ├── java/com/raidrin/eme/
│   │   ├── config/AsyncConfiguration.java ✅
│   │   ├── controller/CharacterGuideController.java ✅
│   │   ├── image/
│   │   │   ├── LeonardoApiService.java ✅
│   │   │   └── AsyncImageGenerationService.java ✅
│   │   ├── mnemonic/MnemonicGenerationService.java ✅
│   │   ├── storage/
│   │   │   ├── entity/CharacterGuideEntity.java ✅
│   │   │   ├── entity/TranslationSessionEntity.java ✅
│   │   │   ├── repository/CharacterGuideRepository.java ✅
│   │   │   ├── repository/TranslationSessionRepository.java ✅
│   │   │   ├── service/CharacterGuideService.java ✅
│   │   │   ├── service/TranslationSessionService.java ✅
│   │   │   └── service/GcpStorageService.java ✅
│   │   └── util/FileNameSanitizer.java ✅
│   └── resources/
│       ├── application.properties (updated) ✅
│       ├── db/migration/V3__add_character_guide_and_sessions.sql ✅
│       └── templates/
│           └── character-guide/
│               ├── list.html ✅
│               ├── create.html ✅
│               └── edit.html ✅
└── generated_images/ (created at runtime)
```

## Next Steps

After completing the setup, implement the remaining features in this order:

1. ✅ Add GCS dependency and rebuild
2. ✅ Set environment variables
3. ✅ Setup GCP and Leonardo API
4. ⬜ Update main controller (`ConvertController.java`)
5. ⬜ Update main page (`index.html`)
6. ⬜ Create translation sessions page
7. ⬜ Implement ZIP download
8. ⬜ Update Anki card generation

See `IMPLEMENTATION_STATUS.md` for detailed remaining work and `NEW_FEATURE_UPDATES.md` for the original requirements.
