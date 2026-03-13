package com.fristagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fristagent.agent.model.ReviewResult;
import com.fristagent.diff.model.DiffContext;
import com.fristagent.llm.LlmGateway;
import com.fristagent.llm.model.ChatMessage;
import com.fristagent.skill.engine.SkillEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 核心：加载当前激活的 Skill，将 Diff 上下文格式化后发给 LLM，解析返回结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCore {

    private final SkillEngine skillEngine;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    /**
     * 对 DiffContext 执行 Code Review，返回结构化结果
     */
    public ReviewResult review(DiffContext diff) {
        String skillName = skillEngine.getActiveSkillName();
        String systemPrompt = skillEngine.loadActiveSystemPrompt();
        String userPrompt = buildUserPrompt(diff);

        log.info("=== AgentCore.review START ===");
        log.info("Active skill: {}", skillName);
        log.info("System prompt length: {} chars, first 200: {}",
                systemPrompt.length(), systemPrompt.substring(0, Math.min(200, systemPrompt.length())));
        log.info("Diff files: {}, user prompt length: {} chars",
                diff.files().size(), userPrompt.length());

        String rawResponse = llmGateway.chat(List.of(
                ChatMessage.system(systemPrompt),
                ChatMessage.user(userPrompt)
        ));

        log.info("LLM raw response (first 500 chars): {}",
                rawResponse.substring(0, Math.min(500, rawResponse.length())));

        ReviewResult reviewResult = parseReviewResult(rawResponse);
        log.info("Parsed result: score={}, issues={}, summary={}",
                reviewResult.score(), reviewResult.issues().size(),
                reviewResult.summary() != null ? reviewResult.summary().substring(0, Math.min(100, reviewResult.summary().length())) : "null");
        log.info("=== AgentCore.review END ===");

        return reviewResult;
    }

    private String buildUserPrompt(DiffContext diff) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Pull Request Information\n");
        sb.append("- **Title**: ").append(diff.prTitle()).append("\n");
        sb.append("- **Author**: ").append(diff.prAuthor()).append("\n");
        sb.append("- **Source Branch**: ").append(diff.sourceBranch()).append("\n");
        sb.append("- **Target Branch**: ").append(diff.targetBranch()).append("\n\n");
        sb.append("## Code Changes\n\n");

        for (DiffContext.FileDiff file : diff.files()) {
            sb.append("### File: `").append(file.path()).append("` (").append(file.changeType()).append(")\n");
            sb.append("```diff\n");
            sb.append(file.patch());
            sb.append("```\n\n");
        }

        sb.append("Please review the above code changes and respond with the JSON result.");
        return sb.toString();
    }

    private ReviewResult parseReviewResult(String rawResponse) {
        try {
            // 清理可能的 markdown 代码块包裹
            String json = rawResponse.trim();
            if (json.startsWith("```")) {
                int start = json.indexOf('\n') + 1;
                int end = json.lastIndexOf("```");
                json = json.substring(start, end).trim();
            }
            return objectMapper.readValue(json, ReviewResult.class);
        } catch (Exception e) {
            log.error("Failed to parse LLM response as ReviewResult, raw: {}", rawResponse, e);
            // 解析失败时返回降级结果
            return new ReviewResult(
                    0,
                    "Review parsing failed. Raw response: " + rawResponse.substring(0, Math.min(200, rawResponse.length())),
                    List.of()
            );
        }
    }
}
