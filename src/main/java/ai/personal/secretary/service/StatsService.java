package ai.personal.secretary.service;

import ai.personal.secretary.model.ActivityLog;
import ai.personal.secretary.model.DomainGoal;
import ai.personal.secretary.repository.ActivityLogRepository;
import ai.personal.secretary.repository.DomainGoalRepository;
import ai.personal.secretary.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatsService {

    private final ActivityLogRepository activityLogRepository;
    private final DomainGoalRepository goalRepository;
    private final UserProfileRepository userProfileRepository;

    private static final Long USER_ID = 2L;

    /**
     * Генерирует текстовую сводку статистики за указанное количество дней.
     */
    @Transactional(readOnly = true)
    public String buildStats(int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        List<ActivityLog> logs = activityLogRepository.findAllByUserAndPeriod(USER_ID, from);

        if (logs.isEmpty()) {
            return "За последние " + days + " дней активностей не зафиксировано.\n" +
                    "Напиши коучу о своих тренировках или занятиях — я запомню!";
        }

        var sb = new StringBuilder();
        sb.append("📊 *Статистика за ").append(days).append(" дней*\n\n");

        // Группируем по доменам
        var byDomain = logs.stream()
                .collect(Collectors.groupingBy(a -> a.getDomain()));

        // Сортируем по количеству активностей
        byDomain.entrySet().stream()
                .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                .forEach(entry -> {
                    var domain = entry.getKey();
                    var acts = entry.getValue();
                    String icon = domain.getIcon() != null ? domain.getIcon() : "•";

                    sb.append(icon).append(" *").append(domain.getName()).append("*: ")
                            .append(acts.size()).append(" ");
                    sb.append(acts.size() == 1 ? "запись" : acts.size() < 5 ? "записи" : "записей");

                    // Для спорта считаем километры из Strava
                    if ("sport".equals(domain.getSlug())) {
                        double totalKm = acts.stream()
                                .filter(a -> a.getSummary() != null)
                                .mapToDouble(a -> extractKm(a.getSummary()))
                                .sum();
                        if (totalKm > 0) {
                            sb.append(String.format(" (%.1f км)", totalKm));
                        }
                    }
                    sb.append("\n");
                });

        // Streak — дни подряд с активностью
        int streak = calculateStreak(logs);
        if (streak > 0) {
            sb.append("\n🔥 *Streak:* ").append(streak).append(" ");
            sb.append(streak == 1 ? "день подряд" : streak < 5 ? "дня подряд" : "дней подряд");
            sb.append("\n");
        }

        // Самый активный день недели
        String mostActiveDay = getMostActiveDay(logs);
        if (mostActiveDay != null) {
            sb.append("⚡ *Самый активный день:* ").append(mostActiveDay).append("\n");
        }

        // Активные цели
        long activeGoals = goalRepository.findAll().stream()
                .filter(g -> g.getStatus() == DomainGoal.GoalStatus.ACTIVE)
                .filter(g -> g.getDomain().getUser().getId().equals(USER_ID))
                .count();
        sb.append("🎯 *Активных целей:* ").append(activeGoals).append("\n");

        // Средний mood если есть
        OptionalDouble avgMood = logs.stream()
                .filter(a -> a.getMoodScore() != null)
                .mapToInt(ActivityLog::getMoodScore)
                .average();
        if (avgMood.isPresent()) {
            sb.append(String.format("😊 *Среднее настроение:* %.1f/5\n", avgMood.getAsDouble()));
        }

        return sb.toString();
    }

    /**
     * Статистика по Strava — только беговые и велоактивности.
     */
    @Transactional(readOnly = true)
    public String buildStravaStats(int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        List<ActivityLog> sportLogs = activityLogRepository.findAllByUserAndPeriod(USER_ID, from)
                .stream()
                .filter(a -> "sport".equals(a.getDomain().getSlug()))
                .filter(a -> a.getSummary() != null &&
                        (a.getSummary().contains("🏃") || a.getSummary().contains("🚴")))
                .toList();

        if (sportLogs.isEmpty()) {
            return "За последние " + days + " дней тренировок из Strava нет.\n" +
                    "Используй /strava для синхронизации.";
        }

        var sb = new StringBuilder();
        sb.append("🏅 *Тренировки за ").append(days).append(" дней*\n\n");

        // Разбиваем на бег и вело
        var runs  = sportLogs.stream().filter(a -> a.getSummary().contains("🏃")).toList();
        var rides = sportLogs.stream().filter(a -> a.getSummary().contains("🚴")).toList();

        if (!runs.isEmpty()) {
            double km = runs.stream().mapToDouble(a -> extractKm(a.getSummary())).sum();
            sb.append("🏃 *Бег:* ").append(runs.size()).append(" тренировок");
            if (km > 0) sb.append(String.format(", %.1f км", km));
            sb.append("\n");

            // Лучшая пробежка
            runs.stream()
                    .max(Comparator.comparingDouble(a -> extractKm(a.getSummary())))
                    .ifPresent(best -> sb.append("  Лучшая: ").append(best.getSummary()).append("\n"));
        }

        if (!rides.isEmpty()) {
            double km = rides.stream().mapToDouble(a -> extractKm(a.getSummary())).sum();
            sb.append("🚴 *Велосипед:* ").append(rides.size()).append(" тренировок");
            if (km > 0) sb.append(String.format(", %.1f км", km));
            sb.append("\n");

            rides.stream()
                    .max(Comparator.comparingDouble(a -> extractKm(a.getSummary())))
                    .ifPresent(best -> sb.append("  Лучшая: ").append(best.getSummary()).append("\n"));
        }

        // Детали последних тренировок
        sb.append("\n*Последние тренировки:*\n");
        sportLogs.stream().limit(5).forEach(a -> {
            sb.append("• ").append(a.getSummary()).append("\n");
            if (a.getDetails() != null && !a.getDetails().isBlank()) {
                // Показываем только первые две строки деталей
                a.getDetails().lines().limit(2)
                        .forEach(line -> sb.append("  _").append(line).append("_\n"));
            }
        });

        return sb.toString();
    }

    // ── Вспомогательные ──────────────────────────────────────────────────────

    /** Извлекает километры из строки вида "🏃 Бег: название — 5.2 км за 30 мин" */
    private double extractKm(String summary) {
        try {
            if (summary == null) return 0;
            int kmIdx = summary.indexOf(" км");
            if (kmIdx < 0) return 0;
            // Ищем число перед " км"
            int start = kmIdx - 1;
            while (start > 0 && (Character.isDigit(summary.charAt(start - 1))
                    || summary.charAt(start - 1) == ',' || summary.charAt(start - 1) == '.')) {
                start--;
            }
            String numStr = summary.substring(start, kmIdx).replace(',', '.');
            return Double.parseDouble(numStr.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** Считает streak — сколько дней подряд была хоть одна активность */
    private int calculateStreak(List<ActivityLog> logs) {
        Set<LocalDate> activeDays = logs.stream()
                .map(a -> a.getLoggedAt().toLocalDate())
                .collect(Collectors.toSet());

        int streak = 0;
        LocalDate day = LocalDate.now();

        while (activeDays.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    /** Возвращает день недели с наибольшим числом активностей */
    private String getMostActiveDay(List<ActivityLog> logs) {
        return logs.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getLoggedAt().getDayOfWeek(),
                        Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey().getDisplayName(TextStyle.FULL, new Locale("ru")))
                .orElse(null);
    }
}