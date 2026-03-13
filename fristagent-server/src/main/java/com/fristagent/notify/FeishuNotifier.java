package com.fristagent.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fristagent.agent.model.ReviewResult;
import com.fristagent.scan.model.ScanTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 飞书开放平台：发送个人消息（卡片消息）。
 * 凭据优先从 Redis (fristagent:notify:*) 读取，支持页面热更新，fallback 到 application.yml。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuNotifier {

    private static final String FEISHU_BASE   = "https://open.feishu.cn";
    private static final String TOKEN_KEY     = "fristagent:feishu:tenant_access_token";
    private static final String NOTIFY_PREFIX = "fristagent:notify:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${fristagent.feishu.app-id:}")
    private String defaultAppId;
    @Value("${fristagent.feishu.app-secret:}")
    private String defaultAppSecret;

    public void sendToUser(String openId, ScanTask task, ReviewResult result) {
        String appId     = notifyConfig("feishuAppId",     defaultAppId);
        String appSecret = notifyConfig("feishuAppSecret", defaultAppSecret);

        if (appId.isBlank() || appSecret.isBlank()) {
            log.warn("Feishu app-id/app-secret not configured, skipping");
            return;
        }
        String token      = getTenantAccessToken(appId, appSecret);
        String cardContent = buildCard(task, result);

        Map<String, Object> body = Map.of(
                "receive_id", openId,
                "msg_type",  "interactive",
                "content",   cardContent
        );

        RestClient.create(FEISHU_BASE)
                .post()
                .uri("/open-apis/im/v1/messages?receive_id_type=open_id")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("Feishu message sent to openId={}, taskId={}", openId, task.getId());
    }

    // ---- Token ----

    /**
     * 获取 tenant_access_token，Redis 缓存 90 分钟。
     * Key 中包含 appId 片段，凭据变更后自动用新 key 获取新 token。
     */
    private String getTenantAccessToken(String appId, String appSecret) {
        String tokenKey = TOKEN_KEY + ":" + appId.hashCode();
        String cached = redis.opsForValue().get(tokenKey);
        if (cached != null) return cached;

        Map<String, String> body = Map.of("app_id", appId, "app_secret", appSecret);
        String response = RestClient.create(FEISHU_BASE)
                .post()
                .uri("/open-apis/auth/v3/tenant_access_token/internal")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            String token = objectMapper.readTree(response).path("tenant_access_token").asText();
            redis.opsForValue().set(tokenKey, token, 90, TimeUnit.MINUTES);
            return token;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get feishu tenant_access_token", e);
        }
    }

    // ---- Card builder ----

    private String buildCard(ScanTask task, ReviewResult result) {
        String riskLevel    = getRiskLevel(result.score());
        String riskEmoji    = switch (riskLevel) { case "高" -> "🔴"; case "中" -> "🟡"; default -> "🟢"; };
        String colorTemplate = switch (riskLevel) { case "高" -> "red"; case "中" -> "yellow"; default -> "green"; };

        long highCount   = result.issues().stream().filter(i -> "HIGH".equalsIgnoreCase(i.severity())).count();
        long mediumCount = result.issues().stream().filter(i -> "MEDIUM".equalsIgnoreCase(i.severity())).count();
        long lowCount    = result.issues().size() - highCount - mediumCount;

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", Map.of("wide_screen_mode", true));

        // Header
        Map<String, Object> title = new LinkedHashMap<>();
        title.put("tag", "plain_text");
        title.put("content", "🔍 PR Code Review · " + task.getPrTitle());
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("title", title);
        header.put("template", colorTemplate);
        card.put("header", header);

        List<Object> elements = new ArrayList<>();

        // Row 1: PR 基础信息（4列）
        elements.add(fieldsDiv(
                field("PR 编号", "#" + task.getPrNumber()),
                field("提交作者", task.getPrAuthor()),
                field("综合评分", result.score() + " / 100"),
                field("风险等级", riskEmoji + " " + riskLevel)
        ));

        // Row 2: 合并方向 + Skill
        elements.add(fieldsDiv(
                field("合并方向", "`" + task.getSourceRef() + "` → `" + task.getTargetBranch() + "`"),
                field("使用 Skill", task.getSkillName())
        ));

        elements.add(Map.of("tag", "hr"));

        // 审查摘要
        elements.add(mdDiv("**审查摘要**\n" + result.summary()));

        // 问题统计（有问题时才展示）
        if (!result.issues().isEmpty()) {
            elements.add(Map.of("tag", "hr"));
            elements.add(fieldsDiv(
                    field("发现问题", result.issues().size() + " 条"),
                    field("🔴 高危", highCount + " 条"),
                    field("🟡 中危", mediumCount + " 条"),
                    field("⚪ 低危", lowCount + " 条")
            ));

            // Top 3 高/中危问题
            List<ReviewResult.Issue> topIssues = result.issues().stream()
                    .filter(i -> "HIGH".equalsIgnoreCase(i.severity()) || "MEDIUM".equalsIgnoreCase(i.severity()))
                    .limit(3)
                    .toList();
            if (!topIssues.isEmpty()) {
                StringBuilder issuesMd = new StringBuilder("**重点问题**\n");
                for (ReviewResult.Issue issue : topIssues) {
                    String emoji = "HIGH".equalsIgnoreCase(issue.severity()) ? "🔴" : "🟡";
                    String file  = issue.filePath() != null ? " `" + issue.filePath() + "`" : "";
                    issuesMd.append(emoji).append(file).append("\n").append(issue.description()).append("\n\n");
                }
                elements.add(mdDiv(issuesMd.toString().trim()));
            }
        }

        elements.add(Map.of("tag", "hr"));

        // 操作按钮
        Map<String, Object> actions = new LinkedHashMap<>();
        actions.put("tag", "action");
        actions.put("actions", List.of(
                button("查看完整报告", "primary",   "http://fristagent.internal/scans/" + task.getId()),
                button("直达 PR",     "default",  task.getPrUrl())
        ));
        elements.add(actions);

        // 底部备注
        Map<String, Object> note = new LinkedHashMap<>();
        note.put("tag", "note");
        Map<String, Object> noteText = new LinkedHashMap<>();
        noteText.put("tag", "plain_text");
        noteText.put("content", "由 FristAgent 自动扫描生成");
        note.put("elements", List.of(noteText));
        elements.add(note);

        card.put("elements", elements);
        try {
            return objectMapper.writeValueAsString(card);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize feishu card", e);
        }
    }

    // ---- Card DSL helpers ----

    private Map<String, Object> mdDiv(String content) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("tag", "lark_md");
        text.put("content", content);
        Map<String, Object> div = new LinkedHashMap<>();
        div.put("tag", "div");
        div.put("text", text);
        return div;
    }

    private Map<String, Object> fieldsDiv(Map<String, Object>... fields) {
        Map<String, Object> div = new LinkedHashMap<>();
        div.put("tag", "div");
        div.put("fields", List.of(fields));
        return div;
    }

    private Map<String, Object> field(String label, String value) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("tag", "lark_md");
        text.put("content", "**" + label + "**\n" + value);
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("is_short", true);
        f.put("text", text);
        return f;
    }

    private Map<String, Object> button(String label, String type, String url) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("tag", "plain_text");
        text.put("content", label);
        Map<String, Object> btn = new LinkedHashMap<>();
        btn.put("tag", "button");
        btn.put("text", text);
        btn.put("type", type);
        btn.put("url", url);
        return btn;
    }

    private String getRiskLevel(int score) {
        if (score < 50) return "高";
        if (score < 75) return "中";
        return "低";
    }

    private String notifyConfig(String field, String defaultValue) {
        String val = redis.opsForValue().get(NOTIFY_PREFIX + field);
        return (val != null && !val.isBlank()) ? val : (defaultValue != null ? defaultValue : "");
    }
}
