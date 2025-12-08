package com.raidrin.eme.storage.repository;

import com.raidrin.eme.storage.entity.WordTranslationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WordTranslationRepository extends JpaRepository<WordTranslationEntity, Long> {

    List<WordTranslationEntity> findByWordIdOrderByCreatedAtDesc(Long wordId);

    Optional<WordTranslationEntity> findByWordIdAndIsCurrent(Long wordId, Boolean isCurrent);

    @Modifying
    @Query("UPDATE WordTranslationEntity t SET t.isCurrent = false WHERE t.word.id = :wordId")
    void clearCurrentForWord(Long wordId);

    @Modifying
    @Query("UPDATE WordTranslationEntity t SET t.isCurrent = true WHERE t.id = :translationId")
    void setAsCurrent(Long translationId);

    long countByWordId(Long wordId);
}
