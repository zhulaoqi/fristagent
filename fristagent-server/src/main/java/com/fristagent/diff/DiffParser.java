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

    private static final int MAX_DIFF_CHARS = 80_000; // 约 1000 行，防止超过 token 限制

    /**
     * 从 diffUrl 下载 diff 文本并解析为结构化 DiffContext
     */
    public DiffContext fetchAndParse(MergeRequestEvent event) {
        String rawDiff = downloadDiff(event.diffUrl());
        if (rawDiff.length() > MAX_DIFF_CHARS) {
            log.warn("Diff too large ({} chars), truncating for scan task prNumber={}",
                    rawDiff.length(), event.prNumber());
            rawDiff = rawDiff.substring(0, MAX_DIFF_CHARS) + "\n... (truncated)";
        }
        List<DiffContext.FileDiff> files = parseUnifiedDiff(rawDiff);
        return new DiffContext(
                event.prTitle(),
                event.prAuthor(),
                event.sourceRef(),
                event.targetBranch(),
                files
        );
    }

    private String downloadDiff(String diffUrl) {
        RestClient client = RestClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, "text/plain, application/vnd.github.v3.diff")
                .build();
        String result = client.get().uri(diffUrl).retrieve().body(String.class);
        return result != null ? result : "";
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
