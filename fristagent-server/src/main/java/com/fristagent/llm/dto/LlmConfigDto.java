package com.fristagent.llm.dto;

public record LlmConfigDto(
        String endpoint,
        String apiKey,
        String model,
        Integer maxTokens,
        Integer timeoutSeconds
) {}
