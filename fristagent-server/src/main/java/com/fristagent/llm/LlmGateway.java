package com.fristagent.llm;

import com.fristagent.llm.dto.LlmConfigDto;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Consumer;

/**
 * 统一 LLM 调用入口，基于 AgentScope Java {@link OpenAIChatModel}。
 *
 * <p>使用 AgentScope 的 Builder 模式构建模型实例，支持任何 OpenAI 兼容协议（自定义 baseUrl）。
 * 每次 chat() 调用动态读取 Redis 配置，实现热更新无需重启。
 *
 * <p>配置优先级：Redis(热更新) > application.yml 默认值
 */
@Slf4j
@Service
public class LlmGateway {

    private static final String CONFIG_KEY_PREFIX = "fristagent:llm:";

    private final StringRedisTemplate redis;

    @Value("${fristagent.llm.endpoint}")
    private String defaultEndpoint;
    @Value("${fristagent.llm.api-key}")
    private String defaultApiKey;
    @Value("${fristagent.llm.model}")
    private String defaultModel;
    @Value("${fristagent.llm.timeout-seconds}")
    private int defaultTimeoutSeconds;
    @Value("${fristagent.llm.max-tokens}")
    private int defaultMaxTokens;

    public LlmGateway(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 发送 chat completion 请求，返回模型回复内容。
     *
     * <p>使用 AgentScope {@link OpenAIChatModel} 替代裸 RestClient，获得：
     * <ul>
     *   <li>Builder pattern 统一配置管理</li>
     *   <li>内置 baseUrl 支持（任意 OpenAI 兼容端点）</li>
     *   <li>Reactive Mono 响应（此处 block() 在 @Async 线程中安全）</li>
     * </ul>
     *
     * @param messages system + user 消息列表（由 AgentCore 组装）
     * @return LLM 回复的纯文本内容
     */
    public String chat(List<com.fristagent.llm.model.ChatMessage> messages) {
        String endpoint    = getConfig("endpoint",        defaultEndpoint);
        String apiKey      = getConfig("api_key",         defaultApiKey);
        String model       = getConfig("model",           defaultModel);
        int    maxTokens   = Integer.parseInt(getConfig("max_tokens",      String.valueOf(defaultMaxTokens)));

        // 去掉末尾多余的 /v1 或 /v1/（AgentScope 会自动拼接 /v1/chat/completions）
        String baseUrl = endpoint.replaceAll("/v1/?$", "");

        log.debug("[LlmGateway] endpoint={}, model={}, maxTokens={}", baseUrl, model, maxTokens);

        try {
            // ── AgentScope OpenAIChatModel Builder ──────────────────────────
            OpenAIChatModel chatModel = OpenAIChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(model)
                    .baseUrl(baseUrl)
                    .build();

            // ── GenerateOptions ─────────────────────────────────────────────
            GenerateOptions options = GenerateOptions.builder()
                    .maxTokens(maxTokens)
                    .temperature(0.2)
                    .build();

            // ── 消息转换：FristAgent ChatMessage → AgentScope Msg ───────────
            List<Msg> agentScopeMsgs = messages.stream()
                    .map(this::toAgentScopeMsg)
                    .toList();

            // ── stream() 收集全量文本（block() 在 @Async 线程中安全）────────
            // AgentScope Model API: stream(msgs, tools, options) → Flux<ChatResponse>
            // 每个 ChatResponse.getContent() 包含流式 chunk，TextBlock.getText() 取文本
            StringBuilder fullText = new StringBuilder();
            chatModel.stream(agentScopeMsgs, List.of(), options)
                    .doOnNext(response -> response.getContent().forEach(block -> {
                        if (block instanceof TextBlock tb) {
                            fullText.append(tb.getText());
                        }
                    }))
                    .blockLast();

            String content = fullText.toString();
            log.debug("[LlmGateway] response length={} chars", content.length());
            return content;

        } catch (Exception e) {
            log.error("[LlmGateway] call failed, endpoint={}, model={}", baseUrl, model, e);
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }
    }

    /**
     * 流式调用 LLM，每个 chunk 文本通过 onChunk 回调实时通知调用方。
     * 同时将完整响应文本 block 到结果返回，保证调用方拿到完整 JSON。
     *
     * @param messages  system + user 消息列表
     * @param onChunk   每收到一个非空 chunk 时的回调（用于 WebSocket 流式推送）
     * @return 完整的 LLM 响应文本
     */
    public String streamChat(List<com.fristagent.llm.model.ChatMessage> messages,
                             Consumer<String> onChunk) {
        String endpoint  = getConfig("endpoint",   defaultEndpoint);
        String apiKey    = getConfig("api_key",    defaultApiKey);
        String model     = getConfig("model",      defaultModel);
        int    maxTokens = Integer.parseInt(getConfig("max_tokens", String.valueOf(defaultMaxTokens)));
        String baseUrl   = endpoint.replaceAll("/v1/?$", "");

        log.debug("[LlmGateway] stream endpoint={}, model={}", baseUrl, model);

        try {
            OpenAIChatModel chatModel = OpenAIChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(model)
                    .baseUrl(baseUrl)
                    .build();

            GenerateOptions options = GenerateOptions.builder()
                    .maxTokens(maxTokens)
                    .temperature(0.2)
                    .build();

            List<Msg> msgs = messages.stream().map(this::toAgentScopeMsg).toList();

            StringBuilder fullText = new StringBuilder();

            // 每个 chunk 先触发回调推给前端，再累积到 fullText
            chatModel.stream(msgs, List.of(), options)
                    .doOnNext(response -> response.getContent().forEach(block -> {
                        if (block instanceof TextBlock tb) {
                            String text = tb.getText();
                            if (text != null && !text.isEmpty()) {
                                fullText.append(text);
                                onChunk.accept(text);
                            }
                        }
                    }))
                    .blockLast();

            String content = fullText.toString();
            log.debug("[LlmGateway] stream complete, total chars={}", content.length());
            return content;

        } catch (Exception e) {
            log.error("[LlmGateway] stream failed, endpoint={}, model={}", baseUrl, model, e);
            throw new RuntimeException("LLM stream failed: " + e.getMessage(), e);
        }
    }

    /**
     * 更新运行时 LLM 配置（存入 Redis，立即生效，无需重启）
     */
    public void updateConfig(String endpoint, String apiKey, String model, int maxTokens, int timeoutSeconds) {
        redis.opsForValue().set(CONFIG_KEY_PREFIX + "endpoint",        endpoint);
        redis.opsForValue().set(CONFIG_KEY_PREFIX + "api_key",         apiKey);
        redis.opsForValue().set(CONFIG_KEY_PREFIX + "model",           model);
        redis.opsForValue().set(CONFIG_KEY_PREFIX + "max_tokens",      String.valueOf(maxTokens));
        redis.opsForValue().set(CONFIG_KEY_PREFIX + "timeout_seconds", String.valueOf(timeoutSeconds));
        log.info("[LlmGateway] config updated: endpoint={}, model={}", endpoint, model);
    }

    /**
     * 读取当前生效的 LLM 配置（Redis 优先，fallback 到 application.yml）
     */
    public LlmConfigDto getConfig() {
        return new LlmConfigDto(
                getConfig("endpoint",        defaultEndpoint),
                getConfig("api_key",         defaultApiKey),
                getConfig("model",           defaultModel),
                Integer.parseInt(getConfig("max_tokens",      String.valueOf(defaultMaxTokens))),
                Integer.parseInt(getConfig("timeout_seconds", String.valueOf(defaultTimeoutSeconds)))
        );
    }

    // ─────────────────────────────────────────────────────────────
    // 内部工具
    // ─────────────────────────────────────────────────────────────

    /** FristAgent ChatMessage → AgentScope Msg 转换 */
    private Msg toAgentScopeMsg(com.fristagent.llm.model.ChatMessage msg) {
        MsgRole role = switch (msg.role()) {
            case "system"    -> MsgRole.SYSTEM;
            case "assistant" -> MsgRole.ASSISTANT;
            default          -> MsgRole.USER;
        };
        return Msg.builder()
                .role(role)
                .textContent(msg.content())
                .build();
    }

    private String getConfig(String key, String defaultValue) {
        String val = redis.opsForValue().get(CONFIG_KEY_PREFIX + key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
