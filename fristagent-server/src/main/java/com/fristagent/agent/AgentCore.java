package com.fristagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fristagent.agent.model.ReviewResult;
import com.fristagent.diff.model.DiffContext;
import com.fristagent.llm.LlmGateway;
import com.fristagent.llm.model.ChatMessage;
import com.fristagent.skill.engine.SkillEngine;
import com.fristagent.skill.engine.SkillLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * Agent 核心：基于 AgentScope Progressive Disclosure 模式，
 * 按 diff 上下文动态组装 Skill system-prompt，再驱动 LLM 进行 Code Review。
 *
 * <h3>渐进式披露流程</h3>
 * <ol>
 *   <li>从 diff 文件路径检测编程语言集合</li>
 *   <li>始终加载 core.md（角色定义 + 审查维度概览）</li>
 *   <li>按需加载对应语言的 sections/lang/{lang}.md（深度规则）</li>
 *   <li>始终加载 output-format.md（JSON 格式约束，放在末尾强化）</li>
 * </ol>
 *
 * <p>好处：对纯 Java PR 不发送 Python/Go 规则，减少无关 token，
 * 同时让 LLM 聚焦于实际出现的语言的专项规则。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCore {

    private final SkillEngine skillEngine;
    private final SkillLoader skillLoader;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    /**
     * 对 DiffContext 执行 Code Review，返回结构化结果。
     *
     * @param diff    解析后的 PR diff 上下文
     * @param onChunk LLM 流式 chunk 回调，每收到一个 token 片段时触发（用于 WebSocket 实时推送）
     * @return ReviewResult（score + summary + issues[]）
     */
    public ReviewResult review(DiffContext diff, Consumer<String> onChunk) {
        String skillName = skillEngine.getActiveSkillName();

        log.info("=== AgentCore.review START === skill={}, files={}",
                skillName, diff.files().size());

        // ── 渐进式披露：按 diff 上下文组装最小化 system-prompt ─────────────
        String systemPrompt = skillLoader.buildContextualPrompt(skillName, diff);
        String userPrompt   = buildUserPrompt(diff);

        log.debug("[AgentCore] system-prompt chars={} (~{} tokens), user-prompt chars={}",
                systemPrompt.length(), systemPrompt.length() / 4, userPrompt.length());

        // ── 通过 AgentScope OpenAIChatModel 流式调用 LLM ────────────────────
        // onChunk 回调将每个 token 片段实时推送到 WebSocket
        String rawResponse = llmGateway.streamChat(
                List.of(ChatMessage.system(systemPrompt), ChatMessage.user(userPrompt)),
                onChunk
        );

        log.info("[AgentCore] LLM raw response (first 500): {}",
                rawResponse.substring(0, Math.min(500, rawResponse.length())));

        // ── 解析并返回 ──────────────────────────────────────────────────────
        ReviewResult result = parseReviewResult(rawResponse);

        log.info("=== AgentCore.review END === skill={} | score={} | issues={}",
                skillName, result.score(), result.issues().size());

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部工具
    // ─────────────────────────────────────────────────────────────────────────

    private String buildUserPrompt(DiffContext diff) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Pull Request Information\n");
        sb.append("- **Title**: ").append(diff.prTitle()).append("\n");
        sb.append("- **Author**: ").append(diff.prAuthor()).append("\n");
        sb.append("- **Source Branch**: ").append(diff.sourceBranch()).append("\n");
        sb.append("- **Target Branch**: ").append(diff.targetBranch()).append("\n\n");
        sb.append("## Code Changes\n\n");

        for (DiffContext.FileDiff file : diff.files()) {
            sb.append("### File: `").append(file.path())
              .append("` (").append(file.changeType()).append(")\n");
            sb.append("```diff\n");
            sb.append(file.patch());
            sb.append("```\n\n");
        }

        sb.append("Please review the above code changes and respond with the JSON result.");
        return sb.toString();
    }

    private ReviewResult parseReviewResult(String rawResponse) {
        try {
            String json = rawResponse.trim();
            // 清理 LLM 可能包裹的 markdown 代码块
            if (json.startsWith("```")) {
                int start = json.indexOf('\n') + 1;
                int end   = json.lastIndexOf("```");
                if (end > start) {
                    json = json.substring(start, end).trim();
                }
            }
            return objectMapper.readValue(json, ReviewResult.class);
        } catch (Exception e) {
            log.error("[AgentCore] Failed to parse LLM response as ReviewResult. raw={}",
                    rawResponse.substring(0, Math.min(300, rawResponse.length())), e);
            // 降级：保留原始回复供排查，score=0 触发人工关注
            return new ReviewResult(
                    0,
                    "Review parsing failed. Raw LLM response: "
                            + rawResponse.substring(0, Math.min(200, rawResponse.length())),
                    List.of()
            );
        }
    }
}
