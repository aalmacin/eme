# Word Processing Pipeline with Redis Streams

This document describes the asynchronous word processing pipeline that uses Redis Streams (Upstash Redis) for handling translation, audio generation, and image generation tasks.

## Overview

The system processes words through three stages:
1. **Translation** - Translates the word from source to target language
2. **Audio Generation** - Generates audio files for both source and target language
3. **Image Generation** - Generates mnemonic images for the word

Each stage has its own status tracked in the database and is processed asynchronously via Upstash Kafka topics.

## Architecture

### Components

1. **WordEntity** - Database entity with status fields:
   - `translationStatus` - Status of translation (PENDING, PROCESSING, COMPLETED, FAILED)
   - `audioGenerationStatus` - Status of audio generation
   - `imageGenerationStatus` - Status of image generation

2. **Redis Streams**:
   - `word_translation` - Handles translation requests
   - `word_audio_generation` - Handles audio generation requests
   - `word_image_generation` - Handles image generation requests

3. **Consumers**:
   - `RedisWordTranslationConsumer` - Processes translation messages
   - `RedisWordAudioGenerationConsumer` - Processes audio generation messages
   - `RedisWordImageGenerationConsumer` - Processes image generation messages

4. **Publisher**:
   - `RedisStreamPublisher` - Publishes messages to Redis Streams

### Processing Flow

```
POST /api/words
    ↓
Create Word (status: PENDING)
    ↓
Publish to word_translation stream
    ↓
RedisWordTranslationConsumer
    ├─ Update status: PROCESSING
    ├─ Translate word
    ├─ Update status: COMPLETED
    └─ Publish to word_audio_generation and word_image_generation streams
         ↓                              ↓
RedisWordAudioGenerationConsumer    RedisWordImageGenerationConsumer
    ├─ Update status: PROCESSING   ├─ Update status: PROCESSING
    ├─ Generate audio files        ├─ Generate mnemonic & prompt
    ├─ Save audio files            ├─ Generate image
    └─ Update status: COMPLETED    ├─ Upload to GCP
                                   └─ Update status: COMPLETED
```

## Configuration

### Environment Variables

Add these to your `.env` file or environment:

```bash
# Upstash Redis Configuration
REDIS_HOST=your-redis-endpoint.upstash.io
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
REDIS_SSL_ENABLED=true
```

For local development without Upstash Redis:
```bash
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_SSL_ENABLED=false
```

### application.properties

The following properties are configured in `src/main/resources/application.properties`:

```properties
# Redis Configuration for Upstash Redis
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.password=${REDIS_PASSWORD:}
spring.data.redis.ssl.enabled=${REDIS_SSL_ENABLED:false}

# Redis Streams Configuration
redis.stream.word-translation=word_translation
redis.stream.word-audio-generation=word_audio_generation
redis.stream.word-image-generation=word_image_generation
redis.consumer.group=eme-word-processor
```

## Viewing Processing Status in Sessions

When you view a translation session, the word processing statuses are automatically displayed for each word in the session. The session detail view shows:

1. **Processing Pipeline Status** - A status card showing the current state of:
   - Translation (PENDING, PROCESSING, COMPLETED, FAILED)
   - Audio Generation (PENDING, PROCESSING, COMPLETED, FAILED)
   - Image Generation (PENDING, PROCESSING, COMPLETED, FAILED)

2. **Color-coded badges**:
   - **Yellow (PENDING)** - Task not yet started
   - **Blue (PROCESSING)** - Task currently running
   - **Green (COMPLETED)** - Task completed successfully
   - **Red (FAILED)** - Task failed

The statuses are pulled from the WordEntity database record and automatically merged with the session data when you view a session.

## API Endpoints

### Create Word and Trigger Processing

**POST** `/api/words`

Creates a new word and triggers the asynchronous processing pipeline.

**Request Body:**
```json
{
  "word": "hello",
  "sourceLanguage": "en",
  "targetLanguage": "hi"
}
```

**Response:**
```json
{
  "success": true,
  "wordId": 123,
  "word": "hello",
  "sourceLanguage": "en",
  "targetLanguage": "hi",
  "translationStatus": "PENDING",
  "audioGenerationStatus": "PENDING",
  "imageGenerationStatus": "PENDING",
  "message": "Word created and processing started. Check status via GET /api/words/{wordId}"
}
```

### Get Word Details

**GET** `/api/words/{wordId}`

Retrieves the full word details including all data.

**Response:**
```json
{
  "id": 123,
  "word": "hello",
  "sourceLanguage": "en",
  "targetLanguage": "hi",
  "translation": "[\"नमस्ते\"]",
  "audioSourceFile": "hello_source.mp3",
  "audioTargetFile": "hello_target.mp3",
  "imageFile": "hello_mnemonic.jpg"
}
```

### Get Word Processing Status

**GET** `/api/words/{wordId}/status`

Retrieves the current processing status for a word.

**Response:**
```json
{
  "success": true,
  "wordId": 123,
  "word": "hello",
  "sourceLanguage": "en",
  "targetLanguage": "hi",
  "translationStatus": "COMPLETED",
  "audioGenerationStatus": "COMPLETED",
  "imageGenerationStatus": "COMPLETED",
  "overallStatus": "COMPLETED",
  "hasTranslation": true,
  "hasAudio": true,
  "hasImage": true,
  "translation": "[\"नमस्ते\"]",
  "audioSourceFile": "hello_source.mp3",
  "audioTargetFile": "hello_target.mp3",
  "imageFile": "hello_mnemonic.jpg",
  "imagePrompt": "A person waving hello..."
}
```

### Get Multiple Word Statuses

**GET** `/api/words/status?wordIds=123,124,125`

Retrieves processing statuses for multiple words (useful for polling).

**Response:**
```json
{
  "success": true,
  "statuses": [
    {
      "wordId": 123,
      "word": "hello",
      "translationStatus": "COMPLETED",
      "audioGenerationStatus": "COMPLETED",
      "imageGenerationStatus": "COMPLETED",
      "hasTranslation": true,
      "hasAudio": true,
      "hasImage": true
    },
    {
      "wordId": 124,
      "word": "world",
      "translationStatus": "PROCESSING",
      "audioGenerationStatus": "PENDING",
      "imageGenerationStatus": "PENDING",
      "hasTranslation": false,
      "hasAudio": false,
      "hasImage": false
    }
  ]
}
```

## Database Migration

The database migration adds three new status columns to the `words` table:

```sql
ALTER TABLE words
ADD COLUMN translation_status VARCHAR(20) DEFAULT 'PENDING',
ADD COLUMN audio_generation_status VARCHAR(20) DEFAULT 'PENDING',
ADD COLUMN image_generation_status VARCHAR(20) DEFAULT 'PENDING';
```

Migration file: `src/main/resources/db/migration/V13__add_word_processing_status_fields.sql`

## Running the Application

1. **Set up Upstash Redis**:
   - Create a Redis database on [Upstash](https://upstash.com)
   - Copy the connection details (endpoint, port, password)
   - The streams will be created automatically when first message is published

2. **Configure Environment**:
   ```bash
   export REDIS_HOST=your-endpoint.upstash.io
   export REDIS_PORT=6379
   export REDIS_PASSWORD=your-password
   export REDIS_SSL_ENABLED=true
   ```

3. **Run Database Migration**:
   ```bash
   ./gradlew flywayMigrate
   ```

4. **Start the Application**:
   ```bash
   ./gradlew bootRun
   ```

5. **Test the Pipeline**:
   ```bash
   curl -X POST http://localhost:8082/api/words \
     -H "Content-Type: application/json" \
     -d '{
       "word": "hello",
       "sourceLanguage": "en",
       "targetLanguage": "hi"
     }'
   ```

6. **Check Status**:
   ```bash
   curl http://localhost:8082/api/words/{wordId}
   ```

## Processing Status Values

- **PENDING** - Task has not started yet
- **PROCESSING** - Task is currently being processed
- **COMPLETED** - Task completed successfully
- **FAILED** - Task failed (check logs for details)

## Monitoring

- Redis Stream consumers log their activity at INFO level
- Check application logs for processing status
- Each consumer logs when it starts processing and completes/fails
- You can monitor Redis Streams using Redis CLI or Upstash console:
  ```bash
  # View stream length
  XLEN word_translation

  # View pending messages
  XPENDING word_translation eme-word-processor

  # View consumer group info
  XINFO GROUPS word_translation
  ```

## Error Handling

- If a consumer fails to process a message, the status is set to FAILED
- Failed messages are logged but not retried automatically
- You can manually retry by creating a new word or updating the status

## Local Development (Without Upstash Redis)

If Redis is not configured (localhost without Redis running):
- The consumers will not start (logged as warnings)
- The publisher will skip message publishing
- The application will still function for manual operations
- Use the existing endpoints for synchronous processing

To run locally with Redis:
```bash
# Using Docker
docker run -d --name redis -p 6379:6379 redis:latest

# Then start the application
./gradlew bootRun
```

## Notes

- The translation consumer automatically triggers audio and image generation after completion
- Audio and image generation run in parallel
- All consumers are daemon threads and will shut down with the application
- Processing is idempotent - if a word already exists, it returns the existing record
- Redis Streams provides message persistence and consumer group support
- Messages are acknowledged after successful processing
- Failed messages remain in the pending list and can be retried

## Why Redis Streams?

Redis Streams was chosen over Kafka for several reasons:
- **Simpler Setup**: No need for separate Kafka cluster, just use Upstash Redis
- **Lower Cost**: Included with Upstash Redis, no separate Kafka service needed
- **Message Persistence**: Messages are stored in Redis and survive restarts
- **Consumer Groups**: Multiple consumers can process messages in parallel
- **Acknowledgment**: Built-in message acknowledgment mechanism
- **Automatic Retry**: Failed messages remain in pending list for retry
- **Perfect for this use case**: Lightweight message queuing for async processing
