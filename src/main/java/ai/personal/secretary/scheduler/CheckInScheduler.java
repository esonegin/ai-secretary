package ai.personal.secretary.scheduler;

import ai.personal.secretary.bot.CoachBot;
import ai.personal.secretary.repository.UserProfileRepository;
import ai.personal.secretary.service.CoachService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Автоматические check-in через Telegram.
 *
 * Чтобы check-in работал, нужно указать свой chatId в application.yml:
 *   telegram.bot.owner-chat-id: 123456789
 *
 * Найти свой chatId: написать боту /start и посмотреть в логах,
 * или использовать @userinfobot в Telegram.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckInScheduler {

    private final CoachService coachService;
    private final CoachBot coachBot;
    private final UserProfileRepository userProfileRepository;

    @Value("${telegram.bot.owner-chat-id:0}")
    private Long ownerChatId;

    private static final Long USER_ID = 1L;

    /** Утренний check-in: 09:00 каждый день */
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

    /** Вечерний check-in: 21:00 каждый день */
    @Scheduled(cron = "${coach.checkin-evening-cron:0 0 21 * * *}")
    public void eveningCheckIn() {
        if (ownerChatId == 0) return;

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM", new Locale("ru")));
        String summary = coachService.generateWeeklySummary(USER_ID);

        String prompt = String.format("""
            Вечерний check-in, %s.
            Данные по активности за последнее время: %s
            Спроси как прошёл день, что удалось, что нет. Будь кратким — 3-4 предложения.
            """, date, summary.length() > 200 ? summary.substring(0, 200) + "..." : summary);

        String reply = coachService.chat(USER_ID, null, prompt);
        coachBot.sendToChat(ownerChatId, "🌙 *Добрый вечер!*\n\n" + reply);
        log.info("Evening check-in sent");
    }

    /** Еженедельный отчёт: воскресенье 20:00 */
    @Scheduled(cron = "0 0 20 * * SUN")
    public void weeklyReport() {
        if (ownerChatId == 0) return;

        String data = coachService.generateWeeklySummary(USER_ID);
        String prompt = """
            Сделай еженедельный анализ на основе этих данных:
            
            """ + data + """
            
            Структура ответа:
            • Что получилось хорошо
            • Что провисло и почему (гипотеза)
            • 2-3 конкретных рекомендации на следующую неделю
            
            Говори как живой коуч, не как отчёт. Около 150 слов.
            """;

        String reply = coachService.chat(USER_ID, null, prompt);
        coachBot.sendToChat(ownerChatId, "📊 *Итоги недели*\n\n" + reply);
        log.info("Weekly report sent");
    }

    private String userName() {
        return userProfileRepository.findFirstByOrderByIdAsc()
                .map(u -> u.getName()).orElse("друг");
    }
}
