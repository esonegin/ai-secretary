package ai.personal.secretary.service;

/**
 * @author onegines
 * @date 25.03.2026
 */

import ai.personal.secretary.model.*;
import ai.personal.secretary.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для работы с активностями.
 *
 * Позволяет логировать активности двумя путями:
 * 1. Явно — через REST API (POST /api/domains/{slug}/activity)
 * 2. Неявно — коуч сам извлекает активность из диалога и сохраняет
 *    (пользователь пишет "сегодня сделал жим 80кг" → коуч сохраняет лог)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    private final ActivityLogRepository activityLogRepository;
    private final DomainRepository domainRepository;
    private final UserProfileRepository userProfileRepository;

    /**
     * Залогировать активность явно (через API или команду в Telegram).
     */
    @Transactional
    public ActivityLog log(Long userId, String domainSlug, String summary,
                           String details, Integer moodScore, Integer energyScore) {

        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Domain domain = domainRepository.findByUserIdAndSlug(userId, domainSlug)
                .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainSlug));

        ActivityLog log = ActivityLog.builder()
                .user(user)
                .domain(domain)
                .loggedAt(LocalDateTime.now())
                .summary(summary)
                .details(details)
                .moodScore(moodScore)
                .energyScore(energyScore)
                .build();

        ActivityLog saved = activityLogRepository.save(log);
        this.log.info("Activity logged: user={} domain={} summary={}", userId, domainSlug, summary);
        return saved;
    }

    /**
     * Получить последние активности домена.
     */
    @Transactional(readOnly = true)
    public List<ActivityLog> getRecent(Long userId, String domainSlug, int limit) {
        Domain domain = domainRepository.findByUserIdAndSlug(userId, domainSlug)
                .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainSlug));

        return activityLogRepository.findLastByDomainId(domain.getId(), limit);
    }

    /**
     * Статистика активности за период (для еженедельного отчёта мета-коуча).
     */
    @Transactional(readOnly = true)
    public List<ActivityLog> getPeriod(Long userId, LocalDateTime from) {
        return activityLogRepository.findAllByUserAndPeriod(userId, from);
    }
}