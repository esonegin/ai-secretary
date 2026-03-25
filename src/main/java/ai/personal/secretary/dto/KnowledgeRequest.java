package ai.personal.secretary.dto;

/**
 * @author onegines
 * @date 18.03.2026
 */

import jakarta.validation.constraints.NotBlank;

public record KnowledgeRequest(
        @NotBlank(message = "Content must not be blank")
        String content
) {
}