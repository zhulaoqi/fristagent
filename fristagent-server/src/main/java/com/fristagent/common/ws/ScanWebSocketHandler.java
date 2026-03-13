package com.fristagent.common.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket handler：管理所有连接，支持广播推送。
 * 前端连接地址：ws://host:8080/ws
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScanWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("WebSocket connected: {}, total={}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("WebSocket disconnected: {}, total={}", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable e) {
        sessions.remove(session);
        log.warn("WebSocket transport error: {}", session.getId(), e);
    }

    /**
     * 向所有在线客户端广播消息
     */
    public void broadcast(WsMessage message) {
        if (sessions.isEmpty()) return;
        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage text = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(text);
                    } catch (IOException e) {
                        log.warn("Failed to send ws message to session {}", session.getId());
                        sessions.remove(session);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize ws message", e);
        }
    }
}
