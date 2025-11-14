package com.raidrin.eme.storage.repository;

import com.raidrin.eme.storage.entity.CharacterGuideEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterGuideRepository extends JpaRepository<CharacterGuideEntity, Long> {

    Optional<CharacterGuideEntity> findByLanguageAndStartSound(String language, String startSound);

    List<CharacterGuideEntity> findByLanguageOrderByStartSound(String language);

    @Query("SELECT c FROM CharacterGuideEntity c ORDER BY c.language, c.startSound")
    List<CharacterGuideEntity> findAllOrderedByLanguageAndSound();

    void deleteByLanguageAndStartSound(String language, String startSound);

    boolean existsByLanguageAndStartSound(String language, String startSound);
}
