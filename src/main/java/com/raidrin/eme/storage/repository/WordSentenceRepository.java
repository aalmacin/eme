package com.raidrin.eme.storage.repository;

import com.raidrin.eme.storage.entity.WordSentenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WordSentenceRepository extends JpaRepository<WordSentenceEntity, Long> {

    List<WordSentenceEntity> findByWordIdOrderByCreatedAtDesc(Long wordId);

    Optional<WordSentenceEntity> findByWordIdAndIsCurrent(Long wordId, Boolean isCurrent);

    @Modifying
    @Query("UPDATE WordSentenceEntity s SET s.isCurrent = false WHERE s.word.id = :wordId")
    void clearCurrentForWord(Long wordId);

    @Modifying
    @Query("UPDATE WordSentenceEntity s SET s.isCurrent = true WHERE s.id = :sentenceId")
    void setAsCurrent(Long sentenceId);

    long countByWordId(Long wordId);
}
