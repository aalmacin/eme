package com.raidrin.eme.messaging.consumer;

import com.raidrin.eme.config.RedisStreamConfig;
import com.raidrin.eme.messaging.RedisStreamPublisher;
import com.raidrin.eme.messaging.WordMessage;
import com.raidrin.eme.storage.entity.ProcessingStatus;
import com.raidrin.eme.storage.entity.WordEntity;
import com.raidrin.eme.storage.repository.WordRepository;
import com.raidrin.eme.translator.TranslationData;
import com.raidrin.eme.translator.TranslationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

@Component
@RequiredArgsConstructor
public class RedisWordTranslationConsumer {

    private static final Logger log = LoggerFactory.getLogger(RedisWordTranslationConsumer.class);

    private final RedisStreamConfig redisStreamConfig;
    private final RedisTemplate<String, String> redisTemplate;
    private final WordRepository wordRepository;
    private final RedisStreamPublisher messagePublisher;

    @Qualifier("google")
    private final TranslationService translationService;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    private volatile boolean running = false;

    @EventListener(ApplicationReadyEvent.class)
    public void startConsumer() {
        if (redisHost == null || redisHost.isEmpty() || "localhost".equals(redisHost)) {
            log.warn("Redis is not configured. Translation consumer will not start.");
            return;
        }

        // Create consumer group if it doesn't exist
        try {
            createConsumerGroupIfNotExists(
                redisStreamConfig.getWordTranslationStream(),
                redisStreamConfig.getConsumerGroup()
            );
        } catch (Exception e) {
            log.error("Failed to create consumer group: {}", e.getMessage());
            return;
        }

        running = true;
        Thread consumerThread = new Thread(this::consumeMessages);
        consumerThread.setDaemon(true);
        consumerThread.setName("redis-word-translation-consumer");
        consumerThread.start();
        log.info("Redis word translation consumer started");
    }

    private void createConsumerGroupIfNotExists(String streamKey, String groupName) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, groupName);
            log.info("Created consumer group {} for stream {}", groupName, streamKey);
        } catch (Exception e) {
            // Group might already exist, which is fine
            log.debug("Consumer group {} may already exist: {}", groupName, e.getMessage());
        }
    }

    private void consumeMessages() {
        String consumerName = "translation-consumer-" + UUID.randomUUID().toString().substring(0, 8);

        while (running) {
            try {
                // Read from stream
                List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                    Consumer.from(redisStreamConfig.getConsumerGroup(), consumerName),
                    StreamReadOptions.empty().count(10).block(Duration.ofSeconds(1)),
                    StreamOffset.create(redisStreamConfig.getWordTranslationStream(), ReadOffset.lastConsumed())
                );

                if (messages != null && !messages.isEmpty()) {
                    for (MapRecord<String, Object, Object> message : messages) {
                        try {
                            processMessage(message);
                            // Acknowledge message
                            redisTemplate.opsForStream().acknowledge(
                                redisStreamConfig.getWordTranslationStream(),
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
                    Thread.sleep(5000); // Wait before retrying
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

            log.info("Processing translation for word ID: {}", wordId);

            Optional<WordEntity> wordOpt = wordRepository.findById(wordId);
            if (wordOpt.isEmpty()) {
                log.error("Word not found with ID: {}", wordId);
                return;
            }

            WordEntity wordEntity = wordOpt.get();

            // Update status to PROCESSING
            wordEntity.setTranslationStatus(ProcessingStatus.PROCESSING);
            wordRepository.save(wordEntity);

            try {
                // Perform translation
                TranslationData translationData = translationService.translateText(
                    word,
                    sourceLanguage,
                    targetLanguage
                );

                // Update word with translation
                Set<String> translations = translationData.getTranslations();
                String translationJson = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(translations);

                wordEntity.setTranslation(translationJson);

                if (translationData.getTransliteration() != null) {
                    wordEntity.setSourceTransliteration(translationData.getTransliteration());
                }

                wordEntity.setTranslationStatus(ProcessingStatus.COMPLETED);
                wordRepository.save(wordEntity);

                log.info("Translation completed for word ID: {}", wordId);

                // After translation is complete, trigger audio and image generation
                WordMessage nextMessage = new WordMessage(
                    wordEntity.getId(),
                    wordEntity.getWord(),
                    wordEntity.getSourceLanguage(),
                    wordEntity.getTargetLanguage()
                );
                messagePublisher.publishAudioGenerationMessage(nextMessage);
                messagePublisher.publishImageGenerationMessage(nextMessage);

            } catch (Exception e) {
                log.error("Translation failed for word ID: {}", wordId, e);
                wordEntity.setTranslationStatus(ProcessingStatus.FAILED);
                wordRepository.save(wordEntity);
            }
        } catch (Exception e) {
            log.error("Error deserializing message: {}", e.getMessage(), e);
        }
    }

    public void stop() {
        running = false;
    }
}
