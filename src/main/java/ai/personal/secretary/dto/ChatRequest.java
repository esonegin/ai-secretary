package ai.personal.secretary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Входящий запрос на чат с коучем.
 *
 * domainSlug — опциональный. Если не указан, DomainRouterService
 * определяет домен автоматически по тексту сообщения.
 *
 * skipRouting = true — принудительно использовать domainSlug без вызова роутера.
 * Полезно для Telegram-кнопок где пользователь явно выбрал домен.
 */
@Data
public class ChatRequest {

    @NotBlank(message = "message не может быть пустым")
    @Size(max = 10000, message = "Сообщение слишком длинное")
    private String message;

    /**
     * Явное указание домена (slug). Если null — роутер определяет сам.
     */
    private String domainSlug;

    /**
     * Если true — роутер не вызывается, domainSlug используется как есть.
     */
    private boolean skipRouting = false;
}