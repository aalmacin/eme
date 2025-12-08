package com.raidrin.eme.config;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for async task execution.
 * Centralizes all thread pool management to avoid resource fragmentation.
 */
@Configuration
@EnableAsync
public class AsyncConfiguration {

    @Value("${processing.word.pool.size:8}")
    private int wordPoolSize;

    @Value("${processing.audio.pool.size:5}")
    private int audioPoolSize;

    private ExecutorService wordProcessingExecutor;
    private ExecutorService audioProcessingExecutor;

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }

    /**
     * Executor for word processing operations.
     * Uses a fixed thread pool to prevent unbounded growth.
     */
    @Bean(name = "wordProcessingExecutor")
    public ExecutorService wordProcessingExecutor() {
        wordProcessingExecutor = Executors.newFixedThreadPool(
            wordPoolSize,
            new CustomizableThreadFactory("word-processing-")
        );
        return wordProcessingExecutor;
    }

    /**
     * Executor for audio generation operations.
     * Separate pool to prevent audio tasks from blocking word processing.
     */
    @Bean(name = "audioProcessingExecutor")
    public ExecutorService audioProcessingExecutor() {
        audioProcessingExecutor = Executors.newFixedThreadPool(
            audioPoolSize,
            new CustomizableThreadFactory("audio-processing-")
        );
        return audioProcessingExecutor;
    }

    @PreDestroy
    public void shutdownExecutors() {
        shutdownExecutor(wordProcessingExecutor, "wordProcessingExecutor");
        shutdownExecutor(audioProcessingExecutor, "audioProcessingExecutor");
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    System.out.println(name + " forced shutdown after timeout");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
