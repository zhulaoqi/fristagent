package com.fristagent.webhook.adapter;

import com.fristagent.webhook.model.MergeRequestEvent;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public interface WebhookAdapter {

    MergeRequestEvent.Platform platform();

    /**
     * 校验 Webhook 签名合法性
     */
    boolean verify(HttpServletRequest request, String body, String secret);

    /**
     * 解析请求体为统一事件模型，返回 empty 代表忽略该事件（如非 PR 相关事件）
     */
    Optional<MergeRequestEvent> parse(Long repoId, String repoUrl, String body, HttpServletRequest request);
}
