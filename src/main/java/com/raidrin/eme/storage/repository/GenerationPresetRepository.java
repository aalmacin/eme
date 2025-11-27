package com.raidrin.eme.storage.repository;

import com.raidrin.eme.storage.entity.GenerationPresetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GenerationPresetRepository extends JpaRepository<GenerationPresetEntity, Long> {

    Optional<GenerationPresetEntity> findByPresetName(String presetName);

    List<GenerationPresetEntity> findByIsDefaultTrue();

    @Query("SELECT p FROM GenerationPresetEntity p ORDER BY p.usageCount DESC, p.updatedAt DESC")
    List<GenerationPresetEntity> findAllOrderByUsageCountAndUpdatedAt();

    @Query("SELECT p FROM GenerationPresetEntity p ORDER BY p.createdAt DESC")
    List<GenerationPresetEntity> findAllOrderByCreatedAtDesc();

    boolean existsByPresetName(String presetName);
}
