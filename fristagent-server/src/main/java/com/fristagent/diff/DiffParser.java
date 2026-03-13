package com.fristagent.diff;

import com.fristagent.diff.model.DiffContext;
import com.fristagent.webhook.model.MergeRequestEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 下载并解析 PR/MR 的 unified diff，提取各文件变更片段。
 */
@Slf4j
@Service
public class DiffParser {

    @Value("${fristagent.github.token:}")
    private String githubToken;

    @Value("${fristagent.gitlab.token:}")
    private String gitlabToken;

    private static final int MAX_DIFF_CHARS = 80_000; // 约 1000 行，防止超过 token 限制

    /**
     * 从 diffUrl 下载 diff 文本并解析为结构化 DiffContext
     */
    public DiffContext fetchAndParse(MergeRequestEvent event) {
        String rawDiff = downloadDiff(event.diffUrl(), event.platform());
        log.info("[DiffParser] platform={}, url={}, rawDiff length={}",
                event.platform(), event.diffUrl(), rawDiff.length());
        if (rawDiff.length() > MAX_DIFF_CHARS) {
            log.warn("Diff too large ({} chars), truncating for scan task prNumber={}",
                    rawDiff.length(), event.prNumber());
            rawDiff = rawDiff.substring(0, MAX_DIFF_CHARS) + "\n... (truncated)";
        }
        List<DiffContext.FileDiff> files = parseUnifiedDiff(rawDiff);
        log.info("[DiffParser] parsed {} file(s) from diff", files.size());
        if (files.isEmpty()) {
            log.warn("[DiffParser] No files parsed — rawDiff preview: {}",
                    rawDiff.substring(0, Math.min(300, rawDiff.length())));
        }
        return new DiffContext(
                event.prTitle(),
                event.prAuthor(),
                event.sourceRef(),
                event.targetBranch(),
                files
        );
    }

    /**
     * 按平台添加认证头，下载 diff 原文
     */
    private String downloadDiff(String diffUrl, MergeRequestEvent.Platform platform) {
        RestClient.Builder builder = RestClient.builder();

        if (platform == MergeRequestEvent.Platform.GITHUB) {
            // GitHub REST API: Accept vnd.github.v3.diff 直接返回 unified diff 文本
            builder.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3.diff");
            if (githubToken != null && !githubToken.isBlank()) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken);
                log.debug("[DiffParser] GitHub request with token");
            } else {
                log.warn("[DiffParser] GitHub token not configured — diff download may fail for private repos");
            }
        } else if (platform == MergeRequestEvent.Platform.GITLAB) {
            builder.defaultHeader(HttpHeaders.ACCEPT, "text/plain");
            if (gitlabToken != null && !gitlabToken.isBlank()) {
                builder.defaultHeader("PRIVATE-TOKEN", gitlabToken);
                log.debug("[DiffParser] GitLab request with token");
            } else {
                log.warn("[DiffParser] GitLab token not configured — diff download may fail for private repos");
            }
        }

        try {
            RestClient client = builder.build();
            String result = client.get().uri(diffUrl).retrieve().body(String.class);
            return result != null ? result : "";
        } catch (Exception e) {
            log.error("[DiffParser] Failed to download diff from {}: {}", diffUrl, e.getMessage());
            return "";
        }
    }

    /**
     * 解析 unified diff 格式，按文件分组
     */
    private List<DiffContext.FileDiff> parseUnifiedDiff(String rawDiff) {
        List<DiffContext.FileDiff> files = new ArrayList<>();
        String[] lines = rawDiff.split("\n");

        String currentFile = null;
        String changeType = "MODIFIED";
        StringBuilder currentPatch = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("diff --git ")) {
                if (currentFile != null) {
                    files.add(new DiffContext.FileDiff(currentFile, changeType, currentPatch.toString()));
                }
                currentFile = extractFilePath(line);
                changeType = "MODIFIED";
                currentPatch = new StringBuilder();
            } else if (line.startsWith("new file mode")) {
                changeType = "ADDED";
            } else if (line.startsWith("deleted file mode")) {
                changeType = "DELETED";
            } else if (line.startsWith("rename to ")) {
                changeType = "RENAMED";
            } else if (currentFile != null) {
                currentPatch.append(line).append("\n");
            }
        }
        if (currentFile != null && !currentPatch.isEmpty()) {
            files.add(new DiffContext.FileDiff(currentFile, changeType, currentPatch.toString()));
        }
        return files;
    }

    private String extractFilePath(String diffLine) {
        // "diff --git a/src/Foo.java b/src/Foo.java"
        String[] parts = diffLine.split(" ");
        if (parts.length >= 4) {
            String bPath = parts[3];
            return bPath.startsWith("b/") ? bPath.substring(2) : bPath;
        }
        return diffLine;
    }
}
