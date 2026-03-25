package ai.personal.secretary.controller;

/**
 * @author onegines
 * @date 20.03.2026
 */

import ai.personal.secretary.model.UserProfile;
import ai.personal.secretary.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public UserProfile getProfile() {
        return userProfileService.getOrCreate();
    }

    @PostMapping
    public UserProfile updateProfile(@RequestBody UserProfile profile) {
        return userProfileService.update(profile);
    }
}