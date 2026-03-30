package ai.personal.secretary.scheduler;

import ai.personal.secretary.bot.CoachBot;
import ai.personal.secretary.repository.DomainGoalRepository;
import ai.personal.secretary.repository.UserProfileRepository;
import ai.personal.secretary.service.CoachService;
import ai.personal.secretary.service.PublishService;
import ai.personal.secretary.model.DomainGoal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckInScheduler {

    private final CoachService coachService;
    private final CoachBot coachBot;
    private final UserProfileRepository userProfileRepository;
    private final DomainGoalRepository goalRepository;

    @Value("${coach.owner-chat-id:0}")
    private Long ownerChatId;

    private static final Long USER_ID = 2L;

    // ── Утренний check-in: 09:00 ──────────────────────────────────────────────

    @Scheduled(cron = "${coach.checkin-morning-cron:0 0 9 * * *}")
    public void morningCheckIn() {
        if (ownerChatId == 0) { log.warn("owner-chat-id не задан, check-in пропущен"); return; }

        String name = userName();
        String day = LocalDate.now().getDayOfWeek()
                .getDisplayName(TextStyle.FULL, new Locale("ru"));

        String prompt = String.format("""
            Доброе утро! Сегодня %s, %s.
            Проведи краткий утренний check-in: спроси о планах на день по 2-3 ключевым направлениям.
            Будь живым и конкретным, не шаблонным. Максимум 4-5 предложений.
            """, day, name);

        String reply = coachService.chat(USER_ID, null, prompt);
        coachBot.sendToChat(ownerChatId, "☀️ *Доброе утро!*\n\n" + reply);
        log.info("Morning check-in sent");
    }

    // ── Вечерний блок: 21:00 ─────────────────────────────────────────────────
    // Три части: check-in → check по целям → запрос темы для поста

    @Scheduled(cron = "${coach.checkin-evening-cron:0 0 21 * * *}")
    public void eveningCheckIn() {
        if (ownerChatId == 0) return;

        // 1. Стандартный вечерний check-in
        sendEveningCheckin();

        // 2. Check по активным целям
        sendGoalsCheck();

        // 3. Запрос темы для поста в канал
        sendPublishRequest();
    }

    private void sendEveningCheckin() {
        String date = LocalDate.now().format(
                DateTimeFormatter.ofPattern("d MMMM", new Locale("ru")));
        String summary = coachService.generateWeeklySummary(USER_ID);

        String prompt = String.format("""
            Вечерний check-in, %s.
            Данные по активности за последнее время: %s
            Спроси как прошёл день, что удалось, что нет. Будь кратким — 2-3 предложения.
            """, date, summary.length() > 300 ? summary.substring(0, 300) + "..." : summary);

        String reply = coachService.chat(USER_ID, null, prompt);
        coachBot.sendToChat(ownerChatId, "🌙 *Добрый вечер!*\n\n" + reply);
        log.info("Evening check-in sent");
    }

    private void sendGoalsCheck() {
        // Проверяем есть ли активные цели
        List<DomainGoal> activeGoals = goalRepository.findAll().stream()
                .filter(g -> g.getStatus() == DomainGoal.GoalStatus.ACTIVE)
                .filter(g -> g.getDomain().getUser().getId().equals(USER_ID))
                .toList();

        if (activeGoals.isEmpty()) return;

        String question = coachService.generateGoalCheckQuestion(USER_ID);
        if (!question.isBlank()) {
            coachBot.sendToChat(ownerChatId,
                    "🎯 *Прогресс по целям:*\n\n" + question);
            // Переводим бота в режим ожидания ответа по целям
            coachBot.setPendingGoalCheck(ownerChatId);
            log.info("Goals check sent");
        }
    }

    private void sendPublishRequest() {
        String goalsContext = coachService.buildGoalProgressContext(USER_ID);

        String prompt = String.format("""
            Предложи пользователю тему для поста в канал сегодня.
            Учти его активные цели и прогресс: %s
            
            Предложи 2-3 варианта тем — разных по формату:
            одна может быть о конкретном достижении/книге/фильме/музыке,
            другая — рефлексия или мысль дня.
            Спроси: "О чём напишем сегодня?" и перечисли варианты.
            Или пусть предложит свою тему. Максимум 5 предложений.
            """, goalsContext.isBlank() ? "целей пока нет" : goalsContext);

        String reply = coachService.chat(USER_ID, null, prompt);
        coachBot.sendToChat(ownerChatId,
                "✍️ *Пост на сегодня:*\n\n" + reply +
                        "\n\n_Напиши свой ответ, и я подготовлю черновик для публикации._");

        // Переводим бота в режим ожидания темы поста
        coachBot.setPendingDailyPost(ownerChatId);
        log.info("Daily post request sent");
    }

    // ── Еженедельный отчёт: воскресенье 20:00 ────────────────────────────────

    @Scheduled(cron = "0 0 20 * * SUN")
    public void weeklyReport() {
        if (ownerChatId == 0) return;

        String data = coachService.generateWeeklySummary(USER_ID);
        String goalsContext = coachService.buildGoalProgressContext(USER_ID);

        String prompt = String.format("""
            Сделай еженедельный анализ:
            
            Активность: %s
            
            Цели и прогресс: %s
            
            Структура ответа:
            • Что получилось хорошо
            • Что провисло и почему
            • Прогресс по ключевым целям
            • 2-3 рекомендации на следующую неделю
            
            Говори как живой коуч. Около 200 слов.
            """, data, goalsContext.isBlank() ? "целей нет" : goalsContext);

        String reply = coachService.chat(USER_ID, null, prompt);
        coachBot.sendToChat(ownerChatId, "📊 *Итоги недели*\n\n" + reply);
        log.info("Weekly report sent");
    }

    private String userName() {
        return userProfileRepository.findFirstByOrderByIdAsc()
                .map(u -> u.getName()).orElse("друг");
    }
}