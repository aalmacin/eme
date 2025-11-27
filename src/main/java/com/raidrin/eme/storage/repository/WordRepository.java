package com.raidrin.eme.storage.repository;

import com.raidrin.eme.storage.entity.WordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WordRepository extends JpaRepository<WordEntity, Long> {

    Optional<WordEntity> findByWordAndSourceLanguageAndTargetLanguage(String word, String sourceLanguage, String targetLanguage);

    @Query("SELECT COUNT(w) FROM WordEntity w")
    long countEntries();

    @Query("SELECT w FROM WordEntity w WHERE w.updatedAt >= :since ORDER BY w.updatedAt DESC")
    List<WordEntity> findRecentEntries(LocalDateTime since);

    @Query("SELECT w FROM WordEntity w WHERE w.sourceLanguage = :sourceLanguage ORDER BY w.updatedAt DESC")
    List<WordEntity> findBySourceLanguage(String sourceLanguage);

    void deleteByWordAndSourceLanguageAndTargetLanguage(String word, String sourceLanguage, String targetLanguage);

    boolean existsByWordAndSourceLanguageAndTargetLanguage(String word, String sourceLanguage, String targetLanguage);

    @Query("SELECT w FROM WordEntity w WHERE w.imageFile IS NOT NULL ORDER BY w.updatedAt DESC")
    List<WordEntity> findAllWithImages();
}
