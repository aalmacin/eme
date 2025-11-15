package com.raidrin.eme.messaging.consumer;

import com.raidrin.eme.config.RedisStreamConfig;
import com.raidrin.eme.image.OpenAiImageService;
import com.raidrin.eme.mnemonic.MnemonicGenerationService;
import com.raidrin.eme.storage.entity.ProcessingStatus;
import com.raidrin.eme.storage.entity.WordEntity;
import com.raidrin.eme.storage.repository.WordRepository;
import com.raidrin.eme.storage.service.GcpStorageService;
import com.raidrin.eme.storage.service.WordService;
import com.raidrin.eme.util.FileNameSanitizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

@Component
@RequiredArgsConstructor
public class RedisWordImageGenerationConsumer {

    private static final Logger log = LoggerFactory.getLogger(RedisWordImageGenerationConsumer.class);

    private final RedisStreamConfig redisStreamConfig;
    private final RedisTemplate<String, String> redisTemplate;
    private final WordRepository wordRepository;
    private final WordService wordService;
    private final MnemonicGenerationService mnemonicGenerationService;
    private final OpenAiImageService openAiImageService;
    private final GcpStorageService gcpStorageService;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${image.output.directory:./generated_images}")
    private String imageOutputDirectory;

    private volatile boolean running = false;

    @EventListener(ApplicationReadyEvent.class)
    public void startConsumer() {
        if (redisHost == null || redisHost.isEmpty() || "localhost".equals(redisHost)) {
            log.warn("Redis is not configured. Image generation consumer will not start.");
            return;
        }

        // Create consumer group if it doesn't exist
        try {
            createConsumerGroupIfNotExists(
                redisStreamConfig.getWordImageGenerationStream(),
                redisStreamConfig.getConsumerGroup()
            );
        } catch (Exception e) {
            log.error("Failed to create consumer group: {}", e.getMessage());
            return;
        }

        running = true;
        Thread consumerThread = new Thread(this::consumeMessages);
        consumerThread.setDaemon(true);
        consumerThread.setName("redis-word-image-generation-consumer");
        consumerThread.start();
        log.info("Redis word image generation consumer started");
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
        String consumerName = "image-consumer-" + UUID.randomUUID().toString().substring(0, 8);

        while (running) {
            try {
                List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                    Consumer.from(redisStreamConfig.getConsumerGroup(), consumerName),
                    StreamReadOptions.empty().count(10).block(Duration.ofSeconds(1)),
                    StreamOffset.create(redisStreamConfig.getWordImageGenerationStream(), ReadOffset.lastConsumed())
                );

                if (messages != null && !messages.isEmpty()) {
                    for (MapRecord<String, Object, Object> message : messages) {
                        try {
                            processMessage(message);
                            redisTemplate.opsForStream().acknowledge(
                                redisStreamConfig.getWordImageGenerationStream(),
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

            log.info("Processing image generation for word ID: {}", wordId);

            Optional<WordEntity> wordOpt = wordRepository.findById(wordId);
            if (wordOpt.isEmpty()) {
                log.error("Word not found with ID: {}", wordId);
                return;
            }

            WordEntity wordEntity = wordOpt.get();

            // Check if translation exists
            if (wordEntity.getTranslation() == null || wordEntity.getTranslation().isEmpty()) {
                log.warn("No translation available for word ID: {}, skipping image generation", wordId);
                wordEntity.setImageGenerationStatus(ProcessingStatus.FAILED);
                wordRepository.save(wordEntity);
                return;
            }

            // Update status to PROCESSING
            wordEntity.setImageGenerationStatus(ProcessingStatus.PROCESSING);
            wordRepository.save(wordEntity);

            try {
                // Get translation
                Set<String> translations = wordService.deserializeTranslations(wordEntity.getTranslation());
                String translation = translations.iterator().next();

                // Generate mnemonic and image prompt
                MnemonicGenerationService.MnemonicData mnemonicData = mnemonicGenerationService.generateMnemonic(
                    word,
                    translation,
                    sourceLanguage,
                    targetLanguage
                );

                String imagePrompt = mnemonicData.getImagePrompt();

                // Update mnemonic data
                wordService.updateMnemonic(
                    word,
                    sourceLanguage,
                    targetLanguage,
                    mnemonicData.getMnemonicKeyword(),
                    mnemonicData.getMnemonicSentence(),
                    imagePrompt
                );

                // Generate image using OpenAI
                log.info("Generating image with prompt: {}", imagePrompt);
                OpenAiImageService.GeneratedImage openAiImage = openAiImageService.generateImage(
                    imagePrompt,
                    "gpt-image-1-mini"
                );

                String imageUrl = openAiImage.getImageUrl();

                // Download image to local directory
                String imageFileName = FileNameSanitizer.fromMnemonicSentence(
                    mnemonicData.getMnemonicSentence() != null ? mnemonicData.getMnemonicSentence() : word,
                    "jpg"
                );
                downloadImageToLocal(imageUrl, imageFileName);

                // Upload to GCP
                String gcsUrl = gcpStorageService.downloadAndUpload(imageUrl, imageFileName);
                log.info("Image uploaded to GCP: {}", gcsUrl);

                // Update word with image
                wordService.updateImage(
                    word,
                    sourceLanguage,
                    targetLanguage,
                    imageFileName,
                    imagePrompt
                );

                wordEntity.setImageGenerationStatus(ProcessingStatus.COMPLETED);
                wordRepository.save(wordEntity);

                log.info("Image generation completed for word ID: {}", wordId);
            } catch (Exception e) {
                log.error("Image generation failed for word ID: {}", wordId, e);
                wordEntity.setImageGenerationStatus(ProcessingStatus.FAILED);
                wordRepository.save(wordEntity);
            }
        } catch (Exception e) {
            log.error("Error deserializing message: {}", e.getMessage(), e);
        }
    }

    private Path downloadImageToLocal(String imageUrl, String fileName) throws Exception {
        Files.createDirectories(Paths.get(imageOutputDirectory));
        Path outputPath = Paths.get(imageOutputDirectory, fileName);

        // Handle file:// URLs (local files from base64 conversion)
        if (imageUrl.startsWith("file://")) {
            Path sourcePath = Paths.get(java.net.URI.create(imageUrl));
            Files.copy(sourcePath, outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("Copied local file from: {} to: {}", sourcePath, outputPath);
            return outputPath;
        }

        // Handle remote URLs (http/https)
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            URL url = new URL(imageUrl);
            url.openStream().transferTo(fos);
        }

        return outputPath;
    }

    public void stop() {
        running = false;
    }
}
