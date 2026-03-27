package ai.personal.secretary.service;

import ai.personal.secretary.model.*;
import ai.personal.secretary.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

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
    public Domain create(Long userId, String slug, String name,
                         String description, String icon, String systemPrompt) {
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        int nextOrder = domainRepository.findByUserIdAndIsActiveTrueOrderBySortOrderAsc(userId)
                .stream().mapToInt(d -> d.getSortOrder() != null ? d.getSortOrder() : 0)
                .max().orElse(0) + 1;

        return domainRepository.save(Domain.builder()
                .user(user)
                .slug(slug.toLowerCase().replaceAll("[^a-z0-9_]", "_"))
                .name(name).description(description).icon(icon)
                .systemPrompt(systemPrompt).isActive(true).sortOrder(nextOrder)
                .build());
    }

    @Transactional
    public Domain updatePrompt(Long domainId, String prompt) {
        Domain d = domainRepository.findById(domainId)
                .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainId));
        d.setSystemPrompt(prompt);
        return domainRepository.save(d);
    }

    @Transactional
    public void deactivate(Long domainId) {
        domainRepository.findById(domainId).ifPresent(d -> {
            d.setIsActive(false);
            domainRepository.save(d);
        });
    }

    public List<DomainGoal> getGoals(Long domainId) {
        return goalRepository.findByDomainIdAndStatusOrderByCreatedAtDesc(
                domainId, DomainGoal.GoalStatus.ACTIVE);
    }

    @Transactional
    public DomainGoal addGoal(Long domainId, String title, String description, LocalDate targetDate) {
        Domain domain = domainRepository.findById(domainId)
                .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainId));
        return goalRepository.save(DomainGoal.builder()
                .domain(domain).title(title).description(description)
                .targetDate(targetDate).status(DomainGoal.GoalStatus.ACTIVE)
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
