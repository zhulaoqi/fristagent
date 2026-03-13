package com.fristagent.webhook;

import com.fristagent.config.model.RepoConfig;
import com.fristagent.config.repository.RepoConfigRepository;
import com.fristagent.scan.ScanService;
import com.fristagent.webhook.adapter.WebhookAdapter;
import com.fristagent.webhook.model.MergeRequestEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private final List<WebhookAdapter> adapters;
    private final RepoConfigRepository repoConfigRepository;
    private final ScanService scanService;
    private final Map<MergeRequestEvent.Platform, WebhookAdapter> adapterMap;

    public WebhookController(List<WebhookAdapter> adapters,
                              RepoConfigRepository repoConfigRepository,
                              ScanService scanService) {
        this.adapters = adapters;
        this.repoConfigRepository = repoConfigRepository;
        this.scanService = scanService;
        this.adapterMap = adapters.stream()
                .collect(Collectors.toMap(WebhookAdapter::platform, Function.identity()));
    }

    @PostMapping("/github/{repoId}")
    public ResponseEntity<String> github(@PathVariable Long repoId,
                                          HttpServletRequest request) throws IOException {
        return handle(repoId, MergeRequestEvent.Platform.GITHUB, request);
    }

    @PostMapping("/gitlab/{repoId}")
    public ResponseEntity<String> gitlab(@PathVariable Long repoId,
                                          HttpServletRequest request) throws IOException {
        return handle(repoId, MergeRequestEvent.Platform.GITLAB, request);
    }

    private ResponseEntity<String> handle(Long repoId,
                                           MergeRequestEvent.Platform platform,
                                           HttpServletRequest request) throws IOException {
        String body = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);

        RepoConfig repo = repoConfigRepository.findById(repoId)
                .filter(RepoConfig::getEnabled)
                .orElse(null);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }

        WebhookAdapter adapter = adapterMap.get(platform);
        if (adapter == null) {
            return ResponseEntity.badRequest().body("Unsupported platform");
        }

        // 签名校验（secret 为空则跳过）
        if (repo.getWebhookSecret() != null && !repo.getWebhookSecret().isBlank()) {
            if (!adapter.verify(request, body, repo.getWebhookSecret())) {
                log.warn("Webhook signature verification failed for repo {}", repoId);
                return ResponseEntity.status(401).body("Invalid signature");
            }
        }

        Optional<MergeRequestEvent> event = adapter.parse(repoId, repo.getRepoUrl(), body, request);
        if (event.isEmpty()) {
            return ResponseEntity.ok("Ignored");
        }

        // 异步触发扫描
        scanService.triggerScan(event.get());
        return ResponseEntity.ok("Accepted");
    }
}
