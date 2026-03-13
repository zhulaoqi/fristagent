package com.fristagent.skill.repository;

import com.fristagent.skill.model.SkillRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SkillRegistryRepository extends JpaRepository<SkillRegistry, Long> {

    Optional<SkillRegistry> findByIsActiveTrue();

    Optional<SkillRegistry> findByName(String name);

    boolean existsByName(String name);

    @Modifying
    @Query("UPDATE SkillRegistry s SET s.isActive = false")
    void deactivateAll();
}
