package com.raidrin.eme.storage.repository;

import com.raidrin.eme.storage.entity.AnkiFormatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnkiFormatRepository extends JpaRepository<AnkiFormatEntity, Long> {

    Optional<AnkiFormatEntity> findByName(String name);

    @Query("SELECT a FROM AnkiFormatEntity a ORDER BY a.isDefault DESC, a.name ASC")
    List<AnkiFormatEntity> findAllOrderedByDefaultAndName();

    Optional<AnkiFormatEntity> findByIsDefaultTrue();
}
