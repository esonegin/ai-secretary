package ai.personal.secretary.controller;

import ai.personal.secretary.dto.ChatRequest;
import ai.personal.secretary.dto.ChatResponse;
import ai.personal.secretary.model.ChatMessage;
import ai.personal.secretary.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * REST API для общения с AI-секретарём.
 *
 * Endpoints:
 *   POST /api/chat           — отправить сообщение, получить ответ
 *   POST /api/chat/stream    — то же, но Server-Sent Events (стриминг)
 *   GET  /api/chat/{session} — история диалога
 *   DELETE /api/chat/{session} — очистить историю сессии
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;

    /**
     * Основной endpoint — синхронный запрос/ответ.
     *
     * curl -X POST http://localhost:8080/api/chat \
     *   -H "Content-Type: application/json" \
     *   -d '{"sessionId":"test-123","message":"Привет! Спланируй мой день."}'
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Chat request: session={}", request.getSessionId());

        String reply = chatService.chat(
                request.getSessionId(),
                request.getMessage(),
                request.getProfileId()
        );

        return ResponseEntity.ok(ChatResponse.of(request.getSessionId(), reply));
    }

    /**
     * Стриминговый endpoint — ответ приходит токен за токеном.
     * Удобен для Telegram WebApp или будущего web-чата.
     *
     * curl -N http://localhost:8080/api/chat/stream \
     *   -X POST \
     *   -H "Content-Type: application/json" \
     *   -d '{"sessionId":"test-123","message":"Что поесть на завтрак?"}'
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        log.info("Stream chat request: session={}", request.getSessionId());

        return chatService.chatStream(
                request.getSessionId(),
                request.getMessage(),
                request.getProfileId()
        );
    }

    /**
     * История сообщений сессии — для отображения в UI.
     *
     * curl http://localhost:8080/api/chat/test-123/history
     */
    @GetMapping("/{sessionId}/history")
    public ResponseEntity<List<ChatMessage>> getHistory(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatService.getHistory(sessionId));
    }
}