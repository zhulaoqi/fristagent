package com.fristagent.config;

import com.fristagent.llm.LlmGateway;
import com.fristagent.llm.dto.LlmConfigDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config/llm")
@RequiredArgsConstructor
public class LlmConfigController {

    private final LlmGateway llmGateway;

    @GetMapping
    public LlmConfigDto getConfig() {
        return llmGateway.getConfig();
    }

    @PostMapping
    public ResponseEntity<Void> updateConfig(@RequestBody LlmConfigDto dto) {
        llmGateway.updateConfig(
                dto.endpoint(),
                dto.apiKey(),
                dto.model(),
                dto.maxTokens() != null ? dto.maxTokens() : 4096,
                dto.timeoutSeconds() != null ? dto.timeoutSeconds() : 120
        );
        return ResponseEntity.ok().build();
    }
}
