package ai.personal.secretary.dto;

/**
 * @author onegines
 * @date 25.03.2026
 */

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Запрос на создание нового домена развития.
 *
 * Домены создаются динамически — без изменения кода.
 * Пример: добавить "Медитация", "Иностранный язык", "Финансы".
 */
@Data
public class CreateDomainRequest {

    /**
     * Машинное имя: только строчные латинские буквы, цифры и подчёркивание.
     * Примеры: "sport", "yoga", "foreign_language".
     */
    @NotBlank(message = "slug обязателен")
    @Pattern(regexp = "^[a-z0-9_]+$",
            message = "slug должен содержать только строчные латинские буквы, цифры и _")
    @Size(max = 50)
    private String slug;

    /**
     * Отображаемое название на русском: "Спорт", "Медитация".
     */
    @NotBlank(message = "name обязателен")
    @Size(max = 100)
    private String name;

    /** Краткое описание направления. */
    @Size(max = 500)
    private String description;

    /** Emoji-иконка для Telegram-интерфейса: "💪", "🧘", "📚". */
    @Size(max = 10)
    private String icon;

    /**
     * Системный промпт коуча этого домена.
     * Определяет характер, стиль и фокус коуча.
     * Хранится в БД — можно менять через PUT /domains/{id}/prompt.
     */
    @NotBlank(message = "systemPrompt обязателен")
    private String systemPrompt;
}