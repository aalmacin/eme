package com.raidrin.eme.storage.repository;

import com.raidrin.eme.storage.entity.WordMnemonicEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WordMnemonicRepository extends JpaRepository<WordMnemonicEntity, Long> {

    List<WordMnemonicEntity> findByWordIdOrderByCreatedAtDesc(Long wordId);

    Optional<WordMnemonicEntity> findByWordIdAndIsCurrent(Long wordId, Boolean isCurrent);

    @Modifying
    @Query("UPDATE WordMnemonicEntity m SET m.isCurrent = false WHERE m.word.id = :wordId")
    void clearCurrentForWord(Long wordId);

    @Modifying
    @Query("UPDATE WordMnemonicEntity m SET m.isCurrent = true WHERE m.id = :mnemonicId")
    void setAsCurrent(Long mnemonicId);

    long countByWordId(Long wordId);
}
