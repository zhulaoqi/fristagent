package com.fristagent.scan.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "scan_task")
public class ScanTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    @Column(nullable = false, length = 16)
    private String platform;

    @Column(name = "pr_number", nullable = false, length = 32)
    private String prNumber;

    @Column(name = "pr_title", length = 512)
    private String prTitle;

    @Column(name = "pr_author", length = 128)
    private String prAuthor;

    @Column(name = "pr_url", length = 512)
    private String prUrl;

    @Column(name = "source_ref", length = 256)
    private String sourceRef;

    @Column(name = "target_branch", length = 256)
    private String targetBranch;

    @Column(name = "skill_name", length = 128)
    private String skillName;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}