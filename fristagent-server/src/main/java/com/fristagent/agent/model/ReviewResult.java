package com.fristagent.agent.model;

import java.util.List;

/**
 * LLM 返回的结构化 Code Review 结果
 */
public record ReviewResult(
        int score,
        String summary,
        List<Issue> issues
) {
    public record Issue(
            String filePath,
            Integer lineStart,
            Integer lineEnd,
            String issueType,
            String severity,
            String description,
            String suggestion
    ) {}
}
