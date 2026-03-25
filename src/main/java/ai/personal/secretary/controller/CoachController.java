package ai.personal.secretary.controller;

/**
 * @author onegines
 * @date 25.03.2026
 */

import ai.personal.secretary.dto.*;
import ai.personal.secretary.model.*;
import ai.personal.secretary.repository.DomainRepository;
import ai.personal.secretary.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

/**
 * Главный REST API коуча.
 *
 * POST   /coach/{userId}/chat                        — свободный диалог (роутер сам определяет домен)
 * POST   /coach/{userId}/chat/stream                 — то же, SSE стриминг
 * POST   /coach/{userId}/chat/{domainSlug}           — диалог с конкретным коучем домена
 * GET    /coach/{userId}/domains                     — список активных доменов
 * POST   /coach/{userId}/domains                     — добавить новый домен
 * PUT    /coach/{userId}/domains/{domainId}/prompt   — обновить промпт коуча
 * GET    /coach/{userId}/domains/{domainId}/goals    — цели домена
 * POST   /coach/{userId}/domains/{domainId}/goals    — добавить цель
 * PATCH  /coach/{userId}/goals/{goalId}/achieve      — отметить цель выполненной
 * POST   /coach/{userId}/domains/{domainSlug}/activity — залогировать активность
 * GET    /coach/{userId}/domains/{domainSlug}/activity — история активностей
 * GET    /coach/{userId}/summary                     — еженедельный отчёт
 * GET    /coach/{userId}/history                     — история диалога
 */
@RestController
@RequestMapping("/coach")
@RequiredArgsConstructor
@Slf4j
public class CoachController {

    private final CoachService coachService;
    private final DomainService domainService;
    private final ActivityService activityService;
    private final DomainRouterService routerService;
    private final DomainRepository domainRepository;

    // ─── Chat ─────────────────────────────────────────────────────────────────

    /**
     * Свободный диалог — DomainRouterService определяет домен по тексту.
     *
     * curl -X POST http://localhost:8080/api/coach/1/chat \
     *   -H "Content-Type: application/json" \
     *   -d '{"message": "сегодня сделал пробежку 5км, чувствую себя отлично"}'
     */
    @PostMapping("/{userId}/chat")
    public ResponseEntity<ChatResponse> chat(
            @PathVariable Long userId,
            @Valid @RequestBody ChatRequest request) {

        Optional<Domain> domain = request.isSkipRouting() && request.getDomainSlug() != null
                ? domainRepository.findByUserIdAndSlug(userId, request.getDomainSlug())
                : routerService.route(userId, request.getMessage());

        String reply = coachService.chat(userId, domain.orElse(null), request.getMessage());

        return ResponseEntity.ok(ChatResponse.of(
                domain.map(Domain::getSlug).orElse("meta"),
                domain.map(Domain::getName).orElse("Мета-коуч"),
                reply
        ));
    }

    /**
     * Диалог с конкретным коучем домена — без авторотинга.
     *
     * curl -X POST http://localhost:8080/api/coach/1/chat/sport \
     *   -H "Content-Type: application/json" \
     *   -d '{"message": "как правильно увеличивать рабочий вес?"}'
     */
    @PostMapping("/{userId}/chat/{domainSlug}")
    public ResponseEntity<ChatResponse> chatWithDomain(
            @PathVariable Long userId,
            @PathVariable String domainSlug,
            @Valid @RequestBody ChatRequest request) {

        Optional<Domain> domain = domainRepository.findByUserIdAndSlug(userId, domainSlug);
        String reply = coachService.chat(userId, domain.orElse(null), request.getMessage());

        return ResponseEntity.ok(ChatResponse.of(
                domainSlug,
                domain.map(Domain::getName).orElse("Мета-коуч"),
                reply
        ));
    }

    /**
     * Стриминговый чат (Server-Sent Events).
     * Токены приходят постепенно — для отображения печатания в Telegram/web.
     */
    @PostMapping(value = "/{userId}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(
            @PathVariable Long userId,
            @Valid @RequestBody ChatRequest request) {

        Optional<Domain> domain = routerService.route(userId, request.getMessage());
        return coachService.chatStream(userId, domain.orElse(null), request.getMessage());
    }

    // ─── Domains ──────────────────────────────────────────────────────────────

    @GetMapping("/{userId}/domains")
    public ResponseEntity<List<Domain>> getDomains(@PathVariable Long userId) {
        return ResponseEntity.ok(domainService.getAll(userId));
    }

    /**
     * Добавить новый домен без изменения кода.
     *
     * curl -X POST http://localhost:8080/api/coach/1/domains \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *     "slug": "meditation",
     *     "name": "Медитация",
     *     "icon": "🧠",
     *     "systemPrompt": "Ты — коуч по медитации..."
     *   }'
     */
    @PostMapping("/{userId}/domains")
    public ResponseEntity<Domain> createDomain(
            @PathVariable Long userId,
            @Valid @RequestBody CreateDomainRequest request) {

        Domain created = domainService.create(
                userId,
                request.getSlug(),
                request.getName(),
                request.getDescription(),
                request.getIcon(),
                request.getSystemPrompt()
        );
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{userId}/domains/{domainId}/prompt")
    public ResponseEntity<Domain> updatePrompt(
            @PathVariable Long userId,
            @PathVariable Long domainId,
            @Valid @RequestBody UpdatePromptRequest request) {

        return ResponseEntity.ok(domainService.updatePrompt(domainId, request.getSystemPrompt()));
    }

    @DeleteMapping("/{userId}/domains/{domainId}")
    public ResponseEntity<Void> deactivateDomain(
            @PathVariable Long userId,
            @PathVariable Long domainId) {

        domainService.deactivate(domainId);
        return ResponseEntity.noContent().build();
    }

    // ─── Goals ────────────────────────────────────────────────────────────────

    @GetMapping("/{userId}/domains/{domainId}/goals")
    public ResponseEntity<List<DomainGoal>> getGoals(
            @PathVariable Long userId,
            @PathVariable Long domainId) {

        return ResponseEntity.ok(domainService.getGoals(domainId));
    }

    @PostMapping("/{userId}/domains/{domainId}/goals")
    public ResponseEntity<DomainGoal> addGoal(
            @PathVariable Long userId,
            @PathVariable Long domainId,
            @Valid @RequestBody CreateGoalRequest request) {

        return ResponseEntity.ok(domainService.addGoal(
                domainId,
                request.getTitle(),
                request.getDescription(),
                request.getTargetDate()
        ));
    }

    @PatchMapping("/{userId}/goals/{goalId}/achieve")
    public ResponseEntity<Void> achieveGoal(
            @PathVariable Long userId,
            @PathVariable Long goalId) {

        domainService.achieveGoal(goalId);
        return ResponseEntity.ok().build();
    }

    // ─── Activity ─────────────────────────────────────────────────────────────

    /**
     * Залогировать активность в домен.
     *
     * curl -X POST http://localhost:8080/api/coach/1/domains/sport/activity \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *     "summary": "Утренняя пробежка",
     *     "details": "5.2 км за 28 минут, темп 5:23/км",
     *     "moodScore": 5,
     *     "energyScore": 4
     *   }'
     */
    @PostMapping("/{userId}/domains/{domainSlug}/activity")
    public ResponseEntity<ActivityLog> logActivity(
            @PathVariable Long userId,
            @PathVariable String domainSlug,
            @Valid @RequestBody LogActivityRequest request) {

        ActivityLog logged = activityService.log(
                userId,
                domainSlug,
                request.getSummary(),
                request.getDetails(),
                request.getMoodScore(),
                request.getEnergyScore()
        );
        return ResponseEntity.ok(logged);
    }

    @GetMapping("/{userId}/domains/{domainSlug}/activity")
    public ResponseEntity<List<ActivityLog>> getActivity(
            @PathVariable Long userId,
            @PathVariable String domainSlug,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(activityService.getRecent(userId, domainSlug, limit));
    }

    // ─── Summary & History ────────────────────────────────────────────────────

    @GetMapping("/{userId}/summary")
    public ResponseEntity<ChatResponse> getSummary(@PathVariable Long userId) {
        String summary = coachService.generateWeeklySummary(userId);
        return ResponseEntity.ok(ChatResponse.of("meta", "Мета-коуч", summary));
    }

    @GetMapping("/{userId}/history")
    public ResponseEntity<List<ChatMessage>> getHistory(
            @PathVariable Long userId,
            @RequestParam(required = false) Long domainId) {

        return ResponseEntity.ok(coachService.getHistory(userId, domainId));
    }
}