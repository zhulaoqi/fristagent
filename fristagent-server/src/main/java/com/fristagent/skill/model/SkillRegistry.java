package com.fristagent.skill.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "skill_registry")
public class SkillRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String name;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(length = 32)
    private String version;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;

    /** BUILTIN / CUSTOM */
    @Column(name = "skill_type", nullable = false, length = 16)
    private String skillType = "BUILTIN";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @Column(name = "installed_at", nullable = false)
    private LocalDateTime installedAt = LocalDateTime.now();
}