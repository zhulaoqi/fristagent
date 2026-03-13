package com.fristagent.notify.dto;

public record NotifyConfigDto(
        String feishuAppId,
        String feishuAppSecret,
        boolean feishuEnabled,
        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        String smtpPassword,
        String smtpFromName,
        boolean emailEnabled
) {}
