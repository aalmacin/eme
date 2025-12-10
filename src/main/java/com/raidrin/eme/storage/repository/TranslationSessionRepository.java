package com.raidrin.eme.storage.repository;

import com.raidrin.eme.storage.entity.TranslationSessionEntity;
import com.raidrin.eme.storage.entity.TranslationSessionEntity.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TranslationSessionRepository extends JpaRepository<TranslationSessionEntity, Long> {

    List<TranslationSessionEntity> findByStatusOrderByCreatedAtDesc(SessionStatus status);

    Page<TranslationSessionEntity> findByStatus(SessionStatus status, Pageable pageable);

    @Query("SELECT s FROM TranslationSessionEntity s ORDER BY s.createdAt DESC")
    List<TranslationSessionEntity> findAllOrderedByCreatedAtDesc();

    @Query("SELECT s FROM TranslationSessionEntity s WHERE s.createdAt >= :since ORDER BY s.createdAt DESC")
    List<TranslationSessionEntity> findRecentSessions(LocalDateTime since);

    List<TranslationSessionEntity> findByWordAndSourceLanguageAndTargetLanguage(
            String word, String sourceLanguage, String targetLanguage);

    @Query("SELECT COUNT(s) FROM TranslationSessionEntity s WHERE s.status = :status")
    long countByStatus(SessionStatus status);
}
