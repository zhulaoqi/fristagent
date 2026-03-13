package com.fristagent.webhook.model;

/**
 * 平台无关的 PR/MR 统一事件模型
 */
public record MergeRequestEvent(
        Platform platform,
        Long repoId,
        String repoUrl,
        String repoName,
        String prNumber,
        String prTitle,
        String prAuthor,
        String prUrl,
        String diffUrl,
        String sourceRef,
        String targetBranch,
        Action action
) {
    public enum Platform { GITHUB, GITLAB }

    public enum Action { OPENED, REOPENED, SYNCHRONIZED }
}
