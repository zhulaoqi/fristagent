package com.fristagent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fristagent.llm.dto.LlmConfigDto;
import com.fristagent.llm.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 统一 LLM 调用入口，支持任何 OpenAI 兼容协议。
 * 配置优先级：DB(llm_config 表) > application.yml
 */
@Slf4j
@Service
public class LlmGateway {

    private static final String CONFIG_KEY_PREFIX = "fristagent:llm:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

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

    public LlmGateway(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送 chat completion 请求，返回模型回复内容
     */
    public String chat(List<ChatMessage> messages) {
        String endpoint  = getConfig("endpoint",  defaultEndpoint);
        String apiKey    = getConfig("api_key",   defaultApiKey);
        String model     = getConfig("model",      defaultModel);
        int maxTokens    = Integer.parseInt(getConfig("max_tokens", String.valueOf(defaultMaxTokens)));
        int timeoutSec   = Integer.parseInt(getConfig("timeout_seconds", String.valueOf(defaultTimeoutSeconds)));

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", maxTokens,
                "temperature", 0.2
        );

        try {
            // 去掉 endpoint 末尾可能已包含的 "/v1" 或 "/v1/"，避免路径重复
            String baseUrl = endpoint.replaceAll("/v1/?$", "");

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(30));
            factory.setReadTimeout(Duration.ofSeconds(timeoutSec));

            RestClient client = RestClient.builder()
                    .requestFactory(factory)
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            String response = client.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("LLM call failed, endpoint={}, model={}", endpoint, model, e);
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }
    }

    /**
     * 更新运行时 LLM 配置（存入 Redis，立即生效，无需重启）
     */
    public void updateConfig(String endpoint, String apiKey, String model, int maxTokens, int timeoutSeconds) {
        redis.opsForValue().set(CONFIG_KEY_PREFIX + "endpoint", endpoint);
        redis.opsForValue().set(CONFIG_KEY_PREFIX + "api_key", apiKey);
        redis.opsForValue().set(CONFIG_KEY_PREFIX + "model", model);
        redis.opsForValue().set(CONFIG_KEY_PREFIX + "max_tokens", String.valueOf(maxTokens));
        redis.opsForValue().set(CONFIG_KEY_PREFIX + "timeout_seconds", String.valueOf(timeoutSeconds));
        log.info("LLM config updated: endpoint={}, model={}", endpoint, model);
    }

    /**
     * 读取当前生效的 LLM 配置（Redis 优先，fallback 到 application.yml 默认值）
     */
    public LlmConfigDto getConfig() {
        return new LlmConfigDto(
                getConfig("endpoint", defaultEndpoint),
                getConfig("api_key", defaultApiKey),
                getConfig("model", defaultModel),
                Integer.parseInt(getConfig("max_tokens", String.valueOf(defaultMaxTokens))),
                Integer.parseInt(getConfig("timeout_seconds", String.valueOf(defaultTimeoutSeconds)))
        );
    }

    private String getConfig(String key, String defaultValue) {
        String val = redis.opsForValue().get(CONFIG_KEY_PREFIX + key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
