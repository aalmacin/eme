package com.raidrin.eme.storage.repository;

import com.raidrin.eme.storage.entity.SentenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SentenceRepository extends JpaRepository<SentenceEntity, Long> {
    
    Optional<SentenceEntity> findByWordAndSourceLanguageAndTargetLanguage(String word, String sourceLanguage, String targetLanguage);
    
    @Query("SELECT DISTINCT s.word FROM SentenceEntity s ORDER BY s.word")
    List<String> findAllWordSentences();
    
    @Query("SELECT COUNT(s) FROM SentenceEntity s")
    long countEntries();
    
    @Query("SELECT s FROM SentenceEntity s WHERE s.updatedAt >= :since ORDER BY s.updatedAt DESC")
    List<SentenceEntity> findRecentEntries(LocalDateTime since);
    
    @Query("SELECT s FROM SentenceEntity s WHERE s.sourceLanguage = :sourceLanguage ORDER BY s.updatedAt DESC")
    List<SentenceEntity> findBySourceLanguage(String sourceLanguage);
    
    void deleteByWordAndSourceLanguageAndTargetLanguage(String word, String sourceLanguage, String targetLanguage);
    
    boolean existsByWordAndSourceLanguageAndTargetLanguage(String word, String sourceLanguage, String targetLanguage);
}
