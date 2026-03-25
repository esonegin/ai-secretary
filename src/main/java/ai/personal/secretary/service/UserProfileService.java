package ai.personal.secretary.service;

/**
 * @author onegines
 * @date 20.03.2026
 */

import ai.personal.secretary.model.UserProfile;
import ai.personal.secretary.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository repository;

    public UserProfile getOrCreate() {
        return repository.findAll().stream().findFirst().orElseGet(() -> {
            UserProfile profile = new UserProfile();
            profile.setGoals("");
            profile.setNutrition("");
            profile.setLifestyle("");
            profile.setNotes("");
            return repository.save(profile);
        });
    }

    public UserProfile update(UserProfile updated) {
        UserProfile existing = getOrCreate();

        existing.setGoals(updated.getGoals());
        existing.setNutrition(updated.getNutrition());
        existing.setLifestyle(updated.getLifestyle());
        existing.setNotes(updated.getNotes());

        return repository.save(existing);
    }
}