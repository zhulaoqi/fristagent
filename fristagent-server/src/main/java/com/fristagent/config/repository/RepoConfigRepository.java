package com.fristagent.config.repository;

import com.fristagent.config.model.RepoConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepoConfigRepository extends JpaRepository<RepoConfig, Long> {

    List<RepoConfig> findByEnabledTrue();

    Optional<RepoConfig> findByRepoUrlAndPlatform(String repoUrl, String platform);
}
