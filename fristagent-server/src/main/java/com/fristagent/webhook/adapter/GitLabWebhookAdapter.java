package com.fristagent.webhook.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fristagent.webhook.model.MergeRequestEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitLabWebhookAdapter implements WebhookAdapter {

    private static final Set<String> TRIGGER_ACTIONS = Set.of("open", "reopen", "update");

    private final ObjectMapper objectMapper;

    @Override
    public MergeRequestEvent.Platform platform() {
        return MergeRequestEvent.Platform.GITLAB;
    }

    @Override
    public boolean verify(HttpServletRequest request, String body, String secret) {
        String token = request.getHeader("X-Gitlab-Token");
        return secret.equals(token);
    }

    @Override
    public Optional<MergeRequestEvent> parse(Long repoId, String repoUrl, String body, HttpServletRequest request) {
        String event = request.getHeader("X-Gitlab-Event");
        if (!"Merge Request Hook".equals(event)) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode attrs = root.path("object_attributes");
            String action = attrs.path("action").asText();
            if (!TRIGGER_ACTIONS.contains(action)) {
                return Optional.empty();
            }
            JsonNode project = root.path("project");
            long prId = attrs.path("iid").asLong();
            String projectUrl = project.path("web_url").asText();

            MergeRequestEvent.Action mrAction = switch (action) {
                case "open" -> MergeRequestEvent.Action.OPENED;
                case "reopen" -> MergeRequestEvent.Action.REOPENED;
                default -> MergeRequestEvent.Action.SYNCHRONIZED;
            };

            // GitLab diff URL 格式
            String diffUrl = projectUrl + "/merge_requests/" + prId + ".diff";

            return Optional.of(new MergeRequestEvent(
                    MergeRequestEvent.Platform.GITLAB,
                    repoId,
                    projectUrl,
                    project.path("path_with_namespace").asText(),
                    String.valueOf(prId),
                    attrs.path("title").asText(),
                    root.path("user").path("username").asText(),
                    projectUrl + "/merge_requests/" + prId,
                    diffUrl,
                    attrs.path("source_branch").asText(),
                    attrs.path("target_branch").asText(),
                    mrAction
            ));
        } catch (Exception e) {
            log.error("Failed to parse GitLab webhook payload", e);
            return Optional.empty();
        }
    }
}
