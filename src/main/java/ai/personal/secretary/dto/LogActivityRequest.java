package ai.personal.secretary.dto;

/**
 * @author onegines
 * @date 25.03.2026
 */

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Запрос на запись активности в домен.
 *
 * Активности — сырые данные для анализа паттернов.
 * Коуч видит последние 7 активностей в системном промпте
 * и учитывает их при ответах.
 *
 * Примеры:
 *  - Спорт:   summary="Тренировка в зале"
 *             details="Жим 80кг×5×3, присед 100кг×3×4, подтягивания 3×8"
 *             moodScore=4, energyScore=5
 *
 *  - Чтение:  summary="Читал 'Думай медленно, решай быстро'"
 *             details="стр. 120-155, глава о системе 1 и системе 2"
 *             moodScore=5, energyScore=3
 *
 *  - Йога:    summary="Вечерняя практика инь-йоги"
 *             details="60 минут, фокус на бёдрах, шавасана 10 минут"
 *             moodScore=5, energyScore=4
 *
 * POST /api/coach/{userId}/domains/{domainSlug}/activity
 */
@Data
public class LogActivityRequest {

    /**
     * Краткое описание — коуч видит это в сводке последних активностей.
     * Должно быть понятно без контекста: "Утренняя пробежка 5км".
     */
    @NotBlank(message = "summary обязателен")
    @Size(max = 500, message = "summary не более 500 символов")
    private String summary;

    /**
     * Детали — для глубокого анализа по запросу.
     * Сюда: конкретные упражнения, страницы, минуты, ощущения.
     */
    @Size(max = 2000)
    private String details;

    /**
     * Настроение после активности: 1 (плохо) — 5 (отлично).
     * Коуч использует для поиска корреляций (пропустил тренировку → настроение упало).
     */
    @Min(value = 1, message = "moodScore от 1 до 5")
    @Max(value = 5, message = "moodScore от 1 до 5")
    private Integer moodScore;

    /**
     * Уровень энергии: 1 (истощён) — 5 (полон сил).
     */
    @Min(value = 1, message = "energyScore от 1 до 5")
    @Max(value = 5, message = "energyScore от 1 до 5")
    private Integer energyScore;
}