package com.raidrin.eme.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raidrin.eme.config.RedisStreamConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RedisStreamPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamPublisher.class);

    private final RedisStreamConfig redisStreamConfig;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    public void publishTranslationMessage(WordMessage message) {
        publishMessage(redisStreamConfig.getWordTranslationStream(), message);
    }

    public void publishAudioGenerationMessage(WordMessage message) {
        publishMessage(redisStreamConfig.getWordAudioGenerationStream(), message);
    }

    public void publishImageGenerationMessage(WordMessage message) {
        publishMessage(redisStreamConfig.getWordImageGenerationStream(), message);
    }

    private void publishMessage(String stream, WordMessage message) {
        if (redisHost == null || redisHost.isEmpty() || "localhost".equals(redisHost)) {
            log.debug("Redis not configured. Skipping message publishing to stream: {}", stream);
            return;
        }

        try {
            // Convert message to map
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put("wordId", String.valueOf(message.getWordId()));
            messageMap.put("word", message.getWord());
            messageMap.put("sourceLanguage", message.getSourceLanguage());
            messageMap.put("targetLanguage", message.getTargetLanguage());

            // Create stream record
            ObjectRecord<String, Map<String, String>> record = StreamRecords
                .newRecord()
                .ofObject(messageMap)
                .withStreamKey(stream);

            // Add to stream
            redisTemplate.opsForStream().add(record);

            log.info("Message published to stream {} for word ID: {}", stream, message.getWordId());
        } catch (Exception e) {
            log.error("Failed to publish message to stream {}: {}", stream, e.getMessage(), e);
        }
    }
}
