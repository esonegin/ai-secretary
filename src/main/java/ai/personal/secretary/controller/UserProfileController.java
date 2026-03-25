package ai.personal.secretary.controller;

/**
 * @author onegines
 * @date 20.03.2026
 */

import ai.personal.secretary.model.UserProfile;
import ai.personal.secretary.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD профиля пользователя.
 *
 * Профиль — базовый контекст для всех коучей:
 * имя, возраст, вес, часовой пояс, заметки о здоровье.
 *
 * Создай профиль перед первым использованием коуча.
 *
 * curl -X POST http://localhost:8080/api/users \
 *   -H "Content-Type: application/json" \
 *   -d '{"name":"Евгений","age":35,"timezone":"Europe/Amsterdam","activityLevel":"moderate"}'
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileRepository repository;

    @GetMapping
    public List<UserProfile> getAll() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfile> get(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UserProfile> create(@RequestBody UserProfile profile) {
        return ResponseEntity.ok(repository.save(profile));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserProfile> update(@PathVariable Long id,
                                              @RequestBody UserProfile updated) {
        return repository.findById(id).map(p -> {
            p.setName(updated.getName());
            p.setAge(updated.getAge());
            p.setTimezone(updated.getTimezone());
            p.setWeightKg(updated.getWeightKg());
            p.setHeightCm(updated.getHeightCm());
            p.setActivityLevel(updated.getActivityLevel());
            p.setHealthNotes(updated.getHealthNotes());
            return ResponseEntity.ok(repository.save(p));
        }).orElse(ResponseEntity.notFound().build());
    }
}