package com.fristagent.diff.model;

import java.util.List;

/**
 * 结构化的 Diff 上下文，作为 Skill 的输入
 */
public record DiffContext(
        String prTitle,
        String prAuthor,
        String sourceBranch,
        String targetBranch,
        List<FileDiff> files
) {
    public record FileDiff(
            String path,
            String changeType,   // ADDED / MODIFIED / DELETED / RENAMED
            String patch         // 原始 unified diff 片段
    ) {}
}
