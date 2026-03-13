package com.fristagent.config;

import com.fristagent.common.exception.ResourceNotFoundException;
import com.fristagent.config.model.AdminUser;
import com.fristagent.config.model.RepoConfig;
import com.fristagent.config.repository.AdminUserRepository;
import com.fristagent.config.repository.RepoConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class RepoController {

    private final RepoConfigRepository repoConfigRepository;
    private final AdminUserRepository adminUserRepository;

    // ---- Request DTOs ----

    record AdminRequest(Long id, String name, String email, String feishuOpenId) {}

    record RepoRequest(
            String name, String platform, String repoUrl,
            String webhookSecret, Boolean enabled,
            List<AdminRequest> admins) {}

    // ---- Endpoints ----

    @GetMapping
    public List<RepoConfig> list() {
        return repoConfigRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<RepoConfig> create(@RequestBody RepoRequest req) {
        RepoConfig repo = new RepoConfig();
        applyRequest(repo, req);
        repo.setCreatedAt(LocalDateTime.now());
        repo.setUpdatedAt(LocalDateTime.now());
        RepoConfig saved = repoConfigRepository.save(repo);
        log.info("Repo created: id={}, name={}", saved.getId(), saved.getName());
        return ResponseEntity.created(URI.create("/api/repos/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public RepoConfig update(@PathVariable("id") Long id, @RequestBody RepoRequest req) {
        RepoConfig existing = repoConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Repo", id));
        applyRequest(existing, req);
        existing.setUpdatedAt(LocalDateTime.now());
        RepoConfig saved = repoConfigRepository.save(existing);
        log.info("Repo updated: id={}, name={}", id, saved.getName());
        return saved;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        if (!repoConfigRepository.existsById(id)) {
            throw new ResourceNotFoundException("Repo", id);
        }
        repoConfigRepository.deleteById(id);
        log.info("Repo deleted: id={}", id);
        return ResponseEntity.noContent().build();
    }

    // ---- Helpers ----

    private void applyRequest(RepoConfig repo, RepoRequest req) {
        repo.setName(req.name());
        repo.setPlatform(req.platform());
        repo.setRepoUrl(req.repoUrl());
        repo.setWebhookSecret(req.webhookSecret());
        repo.setEnabled(req.enabled() != null ? req.enabled() : true);
        repo.setAdmins(resolveAdmins(req.admins()));
    }

    /**
     * 对每个 AdminRequest：
     *  - 有 id → 更新现有记录
     *  - 无 id → 先按 email 查找，避免重复；没有则新建
     */
    private List<AdminUser> resolveAdmins(List<AdminRequest> requests) {
        if (requests == null || requests.isEmpty()) return new ArrayList<>();

        List<AdminUser> result = new ArrayList<>();
        for (AdminRequest a : requests) {
            AdminUser user;
            if (a.id() != null) {
                user = adminUserRepository.findById(a.id()).orElse(new AdminUser());
            } else if (a.email() != null && !a.email().isBlank()) {
                user = adminUserRepository.findByEmail(a.email()).orElse(new AdminUser());
            } else {
                user = new AdminUser();
            }
            user.setName(a.name() != null ? a.name() : "");
            user.setEmail(a.email() != null ? a.email() : "");
            user.setFeishuOpenId(a.feishuOpenId());
            result.add(adminUserRepository.save(user));
        }
        return result;
    }
}
