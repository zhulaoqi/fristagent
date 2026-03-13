package com.fristagent.chat;

import com.fristagent.chat.model.ChatSession;
import com.fristagent.chat.repository.ChatMessageRepository;
import com.fristagent.chat.repository.ChatSessionRepository;
import com.fristagent.llm.LlmGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final LlmGateway llmGateway;

    /**
     * 获取或创建 session
     */
    public ChatSession getOrCreateSession(String sessionKey) {
        return chatSessionRepository.findBySessionKey(sessionKey)
                .orElseGet(() -> {
                    ChatSession s = new ChatSession();
                    s.setSessionKey(sessionKey);
                    return chatSessionRepository.save(s);
                });
    }

    /**
     * 发送消息，返回 AI 回复
     */
    public String sendMessage(String sessionKey, String userMessage) {
        ChatSession session = getOrCreateSession(sessionKey);

        // 保存用户消息
        com.fristagent.chat.model.ChatMessage userMsg = new com.fristagent.chat.model.ChatMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        chatMessageRepository.save(userMsg);

        // 构建历史上下文（最近20条，时间正序）
        List<com.fristagent.chat.model.ChatMessage> recentMsgs =
                chatMessageRepository.findTop20BySessionIdOrderByCreatedAtDesc(session.getId());
        // 反转为正序
        Collections.reverse(recentMsgs);

        List<com.fristagent.llm.model.ChatMessage> llmMessages = new ArrayList<>();
        llmMessages.add(com.fristagent.llm.model.ChatMessage.system(
                "你是 FristAgent 的智能助手，专门帮助开发者了解代码审查结果、理解 PR 问题、改进代码质量。" +
                "回答简洁、专业，使用中文。"));
        recentMsgs.forEach(m ->
                llmMessages.add("user".equals(m.getRole())
                        ? com.fristagent.llm.model.ChatMessage.user(m.getContent())
                        : com.fristagent.llm.model.ChatMessage.assistant(m.getContent())
                )
        );

        // 调 LLM
        String reply;
        try {
            reply = llmGateway.chat(llmMessages);
        } catch (Exception e) {
            log.error("LLM chat failed", e);
            reply = "抱歉，AI 服务暂时不可用：" + e.getMessage();
        }

        // 保存 assistant 回复
        com.fristagent.chat.model.ChatMessage assistantMsg = new com.fristagent.chat.model.ChatMessage();
        assistantMsg.setSessionId(session.getId());
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(reply);
        chatMessageRepository.save(assistantMsg);

        return reply;
    }

    /**
     * 获取 session 历史
     */
    public List<com.fristagent.chat.model.ChatMessage> getHistory(String sessionKey) {
        return chatSessionRepository.findBySessionKey(sessionKey)
                .map(s -> chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(s.getId()))
                .orElse(List.of());
    }
}
