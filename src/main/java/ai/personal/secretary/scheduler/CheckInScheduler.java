package ai.personal.secretary.scheduler;

/**
 * @author onegines
 * @date 25.03.2026
 */

import ai.personal.secretary.model.Domain;
import ai.personal.secretary.model.UserProfile;
import ai.personal.secretary.repository.DomainRepository;
import ai.personal.secretary.repository.UserProfileRepository;
import ai.personal.secretary.service.CoachService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Планировщик check-in.
 *
 * Утром: мета-коуч спрашивает о планах на день по всем активным доменам.
 * Вечером: мета-коуч спрашивает что было сделано, как прошёл день.
 *
 * Сейчас пишет в лог — когда добавишь Telegram-бот,
 * замени log.info() на telegramBotService.sendToUser(userId, text).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckInScheduler {

    private final CoachService coachService;
    private final UserProfileRepository userProfileRepository;
    private final DomainRepository domainRepository;

    /**
     * Утренний check-in — каждый день в 09:00.
     * Cron из application.yml: coach.checkin-morning-cron
     */
    @Scheduled(cron = "${coach.checkin-morning-cron:0 0 9 * * *}")
    public void morningCheckIn() {
        log.info("Running morning check-in...");
        UserProfile user = getDefaultUser();
        if (user == null) return;

        List<Domain> domains = domainRepository
                .findByUserIdAndIsActiveTrueOrderBySortOrderAsc(user.getId());

        String domainNames = domains.stream()
                .map(d -> d.getIcon() != null ? d.getIcon() + " " + d.getName() : d.getName())
                .collect(Collectors.joining(", "));

        String checkInMessage = """
            Доброе утро, %s! Новый день начинается.
            
            Активные направления: %s
            
            Расскажи: что планируешь сегодня? Есть что-то важное или сложное?
            Я помогу расставить приоритеты и поддержу в течение дня.
            """.formatted(user.getName(), domainNames);

        // TODO: заменить на отправку в Telegram когда добавишь бота
        String reply = coachService.chat(user.getId(), null, checkInMessage);
        log.info("Morning check-in reply:\n{}", reply);
    }

    /**
     * Вечерний check-in — каждый день в 21:00.
     */
    @Scheduled(cron = "${coach.checkin-evening-cron:0 0 21 * * *}")
    public void eveningCheckIn() {
        log.info("Running evening check-in...");
        UserProfile user = getDefaultUser();
        if (user == null) return;

        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMMM"));

        String checkInMessage = """
            Добрый вечер! Подводим итоги %s.
            
            Как прошёл день? Что удалось сделать из запланированного?
            Если что-то не получилось — это нормально, расскажи что помешало.
            """.formatted(today);

        // TODO: заменить на отправку в Telegram
        String reply = coachService.chat(user.getId(), null, checkInMessage);
        log.info("Evening check-in reply:\n{}", reply);
    }

    /**
     * Еженедельный отчёт — каждое воскресенье в 20:00.
     */
    @Scheduled(cron = "0 0 20 * * SUN")
    public void weeklyReport() {
        log.info("Running weekly report...");
        UserProfile user = getDefaultUser();
        if (user == null) return;

        String summaryData = coachService.generateWeeklySummary(user.getId());

        String reportRequest = """
            Вот данные об активности за последние 7 дней:
            
            %s
            
            Сделай анализ: что получается хорошо, что провисает, какие паттерны видны.
            Дай 2-3 конкретных рекомендации на следующую неделю. Говори живо, не по-корпоративному.
            """.formatted(summaryData);

        String reply = coachService.chat(user.getId(), null, reportRequest);
        log.info("Weekly report:\n{}", reply);
    }

    private UserProfile getDefaultUser() {
        return userProfileRepository.findFirstByOrderByIdAsc().orElse(null);
    }
}