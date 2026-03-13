package com.fristagent.config;

import com.fristagent.common.exception.ResourceNotFoundException;
import com.fristagent.config.model.AdminUser;
import com.fristagent.config.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admins")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserRepository adminUserRepository;

    @GetMapping
    public List<AdminUser> list() {
        return adminUserRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<AdminUser> create(@RequestBody AdminUser body) {
        body.setId(null);
        body.setCreatedAt(LocalDateTime.now());
        AdminUser saved = adminUserRepository.save(body);
        log.info("Admin created: id={}, email={}", saved.getId(), saved.getEmail());
        return ResponseEntity.created(URI.create("/api/admins/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public AdminUser update(@PathVariable Long id, @RequestBody AdminUser body) {
        AdminUser existing = adminUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", id));
        body.setId(id);
        body.setCreatedAt(existing.getCreatedAt());
        AdminUser saved = adminUserRepository.save(body);
        log.info("Admin updated: id={}, email={}", id, saved.getEmail());
        return saved;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!adminUserRepository.existsById(id)) {
            throw new ResourceNotFoundException("AdminUser", id);
        }
        adminUserRepository.deleteById(id);
        log.info("Admin deleted: id={}", id);
        return ResponseEntity.noContent().build();
    }
}
