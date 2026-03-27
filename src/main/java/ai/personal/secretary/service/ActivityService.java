package ai.personal.secretary.service;

import ai.personal.secretary.model.*;
import ai.personal.secretary.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    private final ActivityLogRepository activityLogRepository;
    private final DomainRepository domainRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public ActivityLog log(Long userId, String domainSlug, String summary,
                           String details, Integer moodScore, Integer energyScore) {
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        Domain domain = domainRepository.findByUserIdAndSlug(userId, domainSlug)
                .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainSlug));

        var entry = ActivityLog.builder()
                .user(user).domain(domain)
                .loggedAt(LocalDateTime.now())
                .summary(summary).details(details)
                .moodScore(moodScore).energyScore(energyScore)
                .build();

        log.info("Activity: user={} domain={} → {}", userId, domainSlug, summary);
        return activityLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getRecent(Long userId, String domainSlug, int limit) {
        Domain domain = domainRepository.findByUserIdAndSlug(userId, domainSlug)
                .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainSlug));
        return activityLogRepository.findLastByDomainId(domain.getId(), limit);
    }
}
