package com.raidrin.eme.storage.repository;

import com.raidrin.eme.storage.entity.TranslationSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TranslationSessionRepository extends JpaRepository<TranslationSessionEntity, Long> {

    @Query("SELECT s FROM TranslationSessionEntity s ORDER BY s.createdAt DESC")
    List<TranslationSessionEntity> findAllOrderedByCreatedAtDesc();

    @Query("SELECT s FROM TranslationSessionEntity s WHERE s.createdAt >= :since ORDER BY s.createdAt DESC")
    List<TranslationSessionEntity> findRecentSessions(LocalDateTime since);

    List<TranslationSessionEntity> findByWordAndSourceLanguageAndTargetLanguage(
            String word, String sourceLanguage, String targetLanguage);
}
