package com.fristagent.webhook.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fristagent.webhook.model.MergeRequestEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubWebhookAdapter implements WebhookAdapter {

    private static final Set<String> TRIGGER_ACTIONS = Set.of("opened", "reopened", "synchronize");

    private final ObjectMapper objectMapper;

    @Override
    public MergeRequestEvent.Platform platform() {
        return MergeRequestEvent.Platform.GITHUB;
    }

    @Override
    public boolean verify(HttpServletRequest request, String body, String secret) {
        String signature = request.getHeader("X-Hub-Signature-256");
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            return computed.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("GitHub signature verification failed", e);
            return false;
        }
    }

    @Override
    public Optional<MergeRequestEvent> parse(Long repoId, String repoUrl, String body, HttpServletRequest request) {
        String event = request.getHeader("X-GitHub-Event");
        if (!"pull_request".equals(event)) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String action = root.path("action").asText();
            if (!TRIGGER_ACTIONS.contains(action)) {
                return Optional.empty();
            }
            JsonNode pr = root.path("pull_request");
            JsonNode repo = root.path("repository");

            MergeRequestEvent.Action mrAction = switch (action) {
                case "opened" -> MergeRequestEvent.Action.OPENED;
                case "reopened" -> MergeRequestEvent.Action.REOPENED;
                default -> MergeRequestEvent.Action.SYNCHRONIZED;
            };

            return Optional.of(new MergeRequestEvent(
                    MergeRequestEvent.Platform.GITHUB,
                    repoId,
                    repo.path("html_url").asText(),
                    repo.path("full_name").asText(),
                    String.valueOf(pr.path("number").asLong()),
                    pr.path("title").asText(),
                    pr.path("user").path("login").asText(),
                    pr.path("html_url").asText(),
                    pr.path("diff_url").asText(),
                    pr.path("head").path("ref").asText(),
                    pr.path("base").path("ref").asText(),
                    mrAction
            ));
        } catch (Exception e) {
            log.error("Failed to parse GitHub webhook payload", e);
            return Optional.empty();
        }
    }
}
