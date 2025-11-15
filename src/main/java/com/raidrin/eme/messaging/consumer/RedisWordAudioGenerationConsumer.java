package com.raidrin.eme.messaging.consumer;

import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.raidrin.eme.audio.AsyncAudioGenerationService;
import com.raidrin.eme.audio.LanguageAudioCodes;
import com.raidrin.eme.config.RedisStreamConfig;
import com.raidrin.eme.storage.entity.ProcessingStatus;
import com.raidrin.eme.storage.entity.WordEntity;
import com.raidrin.eme.storage.repository.WordRepository;
import com.raidrin.eme.storage.service.WordService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class RedisWordAudioGenerationConsumer {

    private static final Logger log = LoggerFactory.getLogger(RedisWordAudioGenerationConsumer.class);

    private final RedisStreamConfig redisStreamConfig;
    private final RedisTemplate<String, String> redisTemplate;
    private final WordRepository wordRepository;
    private final WordService wordService;
    private final AsyncAudioGenerationService audioGenerationService;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    private volatile boolean running = false;

    @EventListener(ApplicationReadyEvent.class)
    public void startConsumer() {
        if (redisHost == null || redisHost.isEmpty() || "localhost".equals(redisHost)) {
            log.warn("Redis is not configured. Audio generation consumer will not start.");
            return;
        }

        // Create consumer group if it doesn't exist
        try {
            createConsumerGroupIfNotExists(
                redisStreamConfig.getWordAudioGenerationStream(),
                redisStreamConfig.getConsumerGroup()
            );
        } catch (Exception e) {
            log.error("Failed to create consumer group: {}", e.getMessage());
            return;
        }

        running = true;
        Thread consumerThread = new Thread(this::consumeMessages);
        consumerThread.setDaemon(true);
        consumerThread.setName("redis-word-audio-generation-consumer");
        consumerThread.start();
        log.info("Redis word audio generation consumer started");
    }

    private void createConsumerGroupIfNotExists(String streamKey, String groupName) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, groupName);
            log.info("Created consumer group {} for stream {}", groupName, streamKey);
        } catch (Exception e) {
            log.debug("Consumer group {} may already exist: {}", groupName, e.getMessage());
        }
    }

    private void consumeMessages() {
        String consumerName = "audio-consumer-" + UUID.randomUUID().toString().substring(0, 8);

        while (running) {
            try {
                List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                    Consumer.from(redisStreamConfig.getConsumerGroup(), consumerName),
                    StreamReadOptions.empty().count(10).block(Duration.ofSeconds(1)),
                    StreamOffset.create(redisStreamConfig.getWordAudioGenerationStream(), ReadOffset.lastConsumed())
                );

                if (messages != null && !messages.isEmpty()) {
                    for (MapRecord<String, Object, Object> message : messages) {
                        try {
                            processMessage(message);
                            redisTemplate.opsForStream().acknowledge(
                                redisStreamConfig.getWordAudioGenerationStream(),
                                redisStreamConfig.getConsumerGroup(),
                                message.getId()
                            );
                        } catch (Exception e) {
                            log.error("Error processing message: {}", e.getMessage(), e);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error reading from stream: {}", e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void processMessage(MapRecord<String, Object, Object> record) {
        try {
            Map<Object, Object> messageMap = record.getValue();

            Long wordId = Long.parseLong(messageMap.get("wordId").toString());
            String word = messageMap.get("word").toString();
            String sourceLanguage = messageMap.get("sourceLanguage").toString();
            String targetLanguage = messageMap.get("targetLanguage").toString();

            log.info("Processing audio generation for word ID: {}", wordId);

            Optional<WordEntity> wordOpt = wordRepository.findById(wordId);
            if (wordOpt.isEmpty()) {
                log.error("Word not found with ID: {}", wordId);
                return;
            }

            WordEntity wordEntity = wordOpt.get();

            // Update status to PROCESSING
            wordEntity.setAudioGenerationStatus(ProcessingStatus.PROCESSING);
            wordRepository.save(wordEntity);

            try {
                // Get audio configuration for source and target languages
                AudioConfig sourceAudioConfig = getAudioConfig(sourceLanguage);
                AudioConfig targetAudioConfig = getAudioConfig(targetLanguage);

                List<AsyncAudioGenerationService.AudioRequest> audioRequests = new ArrayList<>();

                // Create source audio request
                String sourceFileName = sanitizeFileName(word) + "_source.mp3";
                audioRequests.add(new AsyncAudioGenerationService.AudioRequest(
                    word,
                    sourceAudioConfig.languageAudioCode,
                    SsmlVoiceGender.NEUTRAL,
                    sourceAudioConfig.voiceName,
                    sourceFileName
                ));

                // Create target audio request if translation exists
                if (wordEntity.getTranslation() != null && !wordEntity.getTranslation().isEmpty()) {
                    Set<String> translations = wordService.deserializeTranslations(wordEntity.getTranslation());
                    if (!translations.isEmpty()) {
                        String translation = translations.iterator().next();
                        String targetFileName = sanitizeFileName(word) + "_target.mp3";
                        audioRequests.add(new AsyncAudioGenerationService.AudioRequest(
                            translation,
                            targetAudioConfig.languageAudioCode,
                            SsmlVoiceGender.NEUTRAL,
                            targetAudioConfig.voiceName,
                            targetFileName
                        ));
                    }
                }

                // Generate audio files
                CompletableFuture<List<AsyncAudioGenerationService.AudioResult>> audioFuture =
                    audioGenerationService.generateAudioFilesAsync(audioRequests);

                List<AsyncAudioGenerationService.AudioResult> audioResults = audioFuture.get();

                // Update word with audio file names
                String sourceAudioFile = null;
                String targetAudioFile = null;

                for (AsyncAudioGenerationService.AudioResult result : audioResults) {
                    if (result.getFileName().contains("_source")) {
                        sourceAudioFile = result.getFileName();
                    } else if (result.getFileName().contains("_target")) {
                        targetAudioFile = result.getFileName();
                    }
                }

                wordService.updateAudio(
                    word,
                    sourceLanguage,
                    targetLanguage,
                    sourceAudioFile,
                    targetAudioFile
                );

                wordEntity.setAudioGenerationStatus(ProcessingStatus.COMPLETED);
                wordRepository.save(wordEntity);

                log.info("Audio generation completed for word ID: {}", wordId);
            } catch (Exception e) {
                log.error("Audio generation failed for word ID: {}", wordId, e);
                wordEntity.setAudioGenerationStatus(ProcessingStatus.FAILED);
                wordRepository.save(wordEntity);
            }
        } catch (Exception e) {
            log.error("Error deserializing message: {}", e.getMessage(), e);
        }
    }

    private AudioConfig getAudioConfig(String languageCode) {
        switch (languageCode.toLowerCase()) {
            case "en":
                return new AudioConfig(LanguageAudioCodes.English, "en-US-Neural2-A");
            case "es":
                return new AudioConfig(LanguageAudioCodes.Spanish, "es-US-Neural2-B");
            case "fr":
                return new AudioConfig(LanguageAudioCodes.French, "fr-FR-Neural2-B");
            case "ko":
                return new AudioConfig(LanguageAudioCodes.Korean, "ko-KR-Standard-A");
            case "ja":
                return new AudioConfig(LanguageAudioCodes.Japanese, "ja-JP-Neural2-C");
            case "hi":
                return new AudioConfig(LanguageAudioCodes.Hindi, "hi-IN-Neural2-A");
            case "pa":
                return new AudioConfig(LanguageAudioCodes.Punjabi, "pa-IN-Standard-A");
            case "tl":
                return new AudioConfig(LanguageAudioCodes.Tagalog, "tl-PH-Standard-A");
            default:
                log.warn("Unknown language code: {}, defaulting to English", languageCode);
                return new AudioConfig(LanguageAudioCodes.English, "en-US-Neural2-A");
        }
    }

    private String sanitizeFileName(String text) {
        return text.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public void stop() {
        running = false;
    }

    private static class AudioConfig {
        final LanguageAudioCodes languageAudioCode;
        final String voiceName;

        AudioConfig(LanguageAudioCodes languageAudioCode, String voiceName) {
            this.languageAudioCode = languageAudioCode;
            this.voiceName = voiceName;
        }
    }
}
