package ai.personal.secretary.dto;

/**
 * @author onegines
 * @date 25.03.2026
 */

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Запрос на создание цели в домене.
 *
 * Примеры целей:
 *  - Спорт:   "Пробежать 10км без остановки" → targetDate: 2025-06-01
 *  - Йога:    "Освоить стойку на руках"      → targetDate: 2025-12-31
 *  - Чтение:  "Читать 20 минут каждый день"  → targetDate: null (бессрочная)
 *
 * POST /api/coach/{userId}/domains/{domainId}/goals
 */
@Data
public class CreateGoalRequest {

    @NotBlank(message = "title обязателен")
    @Size(max = 200, message = "title не более 200 символов")
    private String title;

    /** Детальное описание цели — что именно, как измерять прогресс. */
    @Size(max = 1000)
    private String description;

    /**
     * Дедлайн цели. Необязателен — некоторые цели бессрочные.
     * Если указан — должен быть сегодня или в будущем.
     */
    @FutureOrPresent(message = "targetDate должна быть сегодня или в будущем")
    private LocalDate targetDate;
}