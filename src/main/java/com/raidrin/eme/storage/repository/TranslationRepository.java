package com.raidrin.eme.storage.repository;

import com.raidrin.eme.storage.entity.TranslationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TranslationRepository extends JpaRepository<TranslationEntity, Long> {
    
    Optional<TranslationEntity> findByWordAndSourceLanguageAndTargetLanguage(String word, String sourceLanguage, String targetLanguage);
    
    @Query("SELECT DISTINCT t.word FROM TranslationEntity t ORDER BY t.word")
    List<String> findAllWordTranslations();
    
    @Query("SELECT COUNT(t) FROM TranslationEntity t")
    long countEntries();
    
    @Query("SELECT t FROM TranslationEntity t WHERE t.updatedAt >= :since ORDER BY t.updatedAt DESC")
    List<TranslationEntity> findRecentEntries(LocalDateTime since);
    
    @Query("SELECT t FROM TranslationEntity t WHERE t.sourceLanguage = :sourceLanguage ORDER BY t.updatedAt DESC")
    List<TranslationEntity> findBySourceLanguage(String sourceLanguage);
    
    void deleteByWordAndSourceLanguageAndTargetLanguage(String word, String sourceLanguage, String targetLanguage);
    
    boolean existsByWordAndSourceLanguageAndTargetLanguage(String word, String sourceLanguage, String targetLanguage);
}
