package com.fristagent.chat.repository;

import com.fristagent.chat.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    // 取最近N条（用于 LLM 上下文）
    List<ChatMessage> findTop20BySessionIdOrderByCreatedAtDesc(Long sessionId);
}
