package com.fristagent.chat;

import com.fristagent.chat.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> send(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("sessionId", "default");
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        String reply = chatService.sendMessage(sessionId, message);
        return ResponseEntity.ok(Map.of("reply", reply, "sessionId", sessionId));
    }

    @GetMapping("/history/{sessionId}")
    public List<ChatMessage> history(@PathVariable("sessionId") String sessionId) {
        return chatService.getHistory(sessionId);
    }
}
