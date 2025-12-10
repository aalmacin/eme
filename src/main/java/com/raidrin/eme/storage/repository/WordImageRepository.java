package com.raidrin.eme.storage.repository;

import com.raidrin.eme.storage.entity.WordImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WordImageRepository extends JpaRepository<WordImageEntity, Long> {

    List<WordImageEntity> findByWordIdOrderByCreatedAtDesc(Long wordId);

    Optional<WordImageEntity> findByWordIdAndIsCurrent(Long wordId, Boolean isCurrent);

    @Modifying
    @Query("UPDATE WordImageEntity i SET i.isCurrent = false WHERE i.word.id = :wordId")
    void clearCurrentForWord(Long wordId);

    @Modifying
    @Query("UPDATE WordImageEntity i SET i.isCurrent = true WHERE i.id = :imageId")
    void setAsCurrent(Long imageId);

    long countByWordId(Long wordId);
}
