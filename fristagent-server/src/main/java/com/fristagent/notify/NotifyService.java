package com.fristagent.notify;

import com.fristagent.agent.model.ReviewResult;
import com.fristagent.config.model.AdminUser;
import com.fristagent.config.model.RepoConfig;
import com.fristagent.config.repository.RepoConfigRepository;
import com.fristagent.scan.model.ScanTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    private final RepoConfigRepository repoConfigRepository;
    private final FeishuNotifier feishuNotifier;
    private final EmailNotifier emailNotifier;

    public void notifyAdmins(ScanTask task, ReviewResult result) {
        RepoConfig repo = repoConfigRepository.findById(task.getRepoId()).orElse(null);
        if (repo == null || repo.getAdmins() == null || repo.getAdmins().isEmpty()) {
            log.warn("No admins configured for repoId={}, skipping notification", task.getRepoId());
            return;
        }

        List<AdminUser> admins = repo.getAdmins();
        for (AdminUser admin : admins) {
            try {
                if (admin.getFeishuOpenId() != null && !admin.getFeishuOpenId().isBlank()) {
                    feishuNotifier.sendToUser(admin.getFeishuOpenId(), task, result);
                }
            } catch (Exception e) {
                log.error("Feishu notify failed for admin={}", admin.getName(), e);
            }
            try {
                if (admin.getEmail() != null && !admin.getEmail().isBlank()) {
                    emailNotifier.send(admin.getEmail(), admin.getName(), task, result);
                }
            } catch (Exception e) {
                log.error("Email notify failed for admin={}", admin.getName(), e);
            }
        }
    }
}
