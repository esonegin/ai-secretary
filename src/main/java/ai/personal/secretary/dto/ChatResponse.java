package ai.personal.secretary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Ответ коуча — содержит текст и информацию о том,
 * какой домен обработал запрос (полезно для отладки и UI).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** Slug домена который ответил, или "meta" для мета-коуча. */
    private String domainSlug;

    /** Человекочитаемое название домена: "Спорт", "Йога", "Мета-коуч". */
    private String domainName;

    /** Текст ответа коуча. */
    private String message;

    private LocalDateTime timestamp;

    public static ChatResponse of(String domainSlug, String domainName, String message) {
        return ChatResponse.builder()
                .domainSlug(domainSlug)
                .domainName(domainName)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}