package ai.personal.secretary.service;

/**
 * @author onegines
 * @date 25.03.2026
 */

import ai.personal.secretary.model.*;
import ai.personal.secretary.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Управление доменами развития.
 *
 * Ключевая фича: домены динамические.
 * Хочешь добавить "Медитация" — POST /api/domains с нужным промптом.
 * Не нужно перезапускать приложение или менять код.
 */
@Service
@RequiredArgsConstructor
public class DomainService {

    private final DomainRepository domainRepository;
    private final UserProfileRepository userProfileRepository;
    private final DomainGoalRepository goalRepository;

    public List<Domain> getAll(Long userId) {
        return domainRepository.findByUserIdAndIsActiveTrueOrderBySortOrderAsc(userId);
    }

    @Transactional
    public Domain create(Long userId, String slug, String name, String description,
                         String icon, String systemPrompt) {
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Определяем sort_order — последний + 1
        int nextOrder = domainRepository
                .findByUserIdAndIsActiveTrueOrderBySortOrderAsc(userId)
                .stream()
                .mapToInt(d -> d.getSortOrder() != null ? d.getSortOrder() : 0)
                .max()
                .orElse(0) + 1;

        Domain domain = Domain.builder()
                .user(user)
                .slug(slug.toLowerCase().replaceAll("[^a-z0-9_]", "_"))
                .name(name)
                .description(description)
                .icon(icon)
                .systemPrompt(systemPrompt)
                .isActive(true)
                .sortOrder(nextOrder)
                .build();

        return domainRepository.save(domain);
    }

    @Transactional
    public Domain updatePrompt(Long domainId, String newSystemPrompt) {
        Domain domain = domainRepository.findById(domainId)
                .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainId));
        domain.setSystemPrompt(newSystemPrompt);
        return domainRepository.save(domain);
    }

    @Transactional
    public void deactivate(Long domainId) {
        domainRepository.findById(domainId).ifPresent(d -> {
            d.setIsActive(false);
            domainRepository.save(d);
        });
    }

    // ─── Цели ─────────────────────────────────────────────────────────────────

    public List<DomainGoal> getGoals(Long domainId) {
        return goalRepository.findByDomainIdAndStatusOrderByCreatedAtDesc(
                domainId, DomainGoal.GoalStatus.ACTIVE);
    }

    @Transactional
    public DomainGoal addGoal(Long domainId, String title, String description,
                              java.time.LocalDate targetDate) {
        Domain domain = domainRepository.findById(domainId)
                .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainId));

        return goalRepository.save(DomainGoal.builder()
                .domain(domain)
                .title(title)
                .description(description)
                .targetDate(targetDate)
                .status(DomainGoal.GoalStatus.ACTIVE)
                .build());
    }

    @Transactional
    public void achieveGoal(Long goalId) {
        goalRepository.findById(goalId).ifPresent(g -> {
            g.setStatus(DomainGoal.GoalStatus.ACHIEVED);
            goalRepository.save(g);
        });
    }
}