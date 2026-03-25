package ai.personal.secretary.dto;

/**
 * @author onegines
 * @date 25.03.2026
 */

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Запрос на обновление системного промпта коуча домена.
 *
 * Позволяет менять характер и инструкции коуча без перезапуска.
 * Например: сделать коуча по спорту более требовательным
 * или добавить ему знание о конкретной программе тренировок.
 *
 * PUT /api/coach/{userId}/domains/{domainId}/prompt
 */
@Data
public class UpdatePromptRequest {

    @NotBlank(message = "systemPrompt не может быть пустым")
    private String systemPrompt;
}