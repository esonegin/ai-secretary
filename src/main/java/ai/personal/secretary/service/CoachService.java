package ai.personal.secretary.service;

/**
 * @author onegines
 * @date 25.03.2026
 */

import ai.personal.secretary.model.*;
import ai.personal.secretary.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Центральный сервис AI-коуча.
 *
 * Для каждого домена строит уникальный контекст:
 *   1. Системный промпт коуча (из Domain.systemPrompt)
 *   2. Активные цели пользователя в этом домене
 *   3. Последние N активностей
 *   4. История диалога
 *   5. Текущее сообщение пользователя
 *
 * Мета-коуч (domain == null) видит активность по всем доменам
 * и может делать cross-domain наблюдения.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CoachService {

    private final AnthropicChatModel chatModel;
    private final ChatMessageRepository chatMessageRepository;
    private final DomainGoalRepository goalRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserProfileRepository userProfileRepository;

    @Value("${coach.max-history-messages:30}")
    private int maxHistory;

    @Value("${coach.weekly-report-days:7}")
    private int weeklyReportDays;

    // ─── Основной метод чата ──────────────────────────────────────────────────

    /**
     * Отправить сообщение коучу домена и получить ответ.
     *
     * @param userId   ID пользователя
     * @param domain   домен (null = мета-коуч)
     * @param message  текст от пользователя
     * @return ответ коуча
     */
    @Transactional
    public String chat(Long userId, Domain domain, String message) {
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Строим полный контекст для этого домена
        String systemPrompt = buildSystemPrompt(user, domain);
        List<Message> history = buildHistory(userId, domain);
        history.add(new UserMessage(message));

        // Создаём ChatClient с системным промптом именно этого домена
        ChatResponse response = ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .build()
                .prompt(new Prompt(history))
                .call()
                .chatResponse();

        String reply = response.getResult().getOutput().getText();
        int tokens = Optional.ofNullable(response.getMetadata().getUsage())
                .map(u -> (int) u.getTotalTokens())
                .orElse(0);

        log.debug("Coach [{}] replied: tokens={}", domain != null ? domain.getSlug() : "meta", tokens);

        // Сохраняем оба сообщения
        String sessionId = buildSessionId(userId, domain);
        save(user, domain, sessionId, ChatMessage.Role.USER, message, null);
        save(user, domain, sessionId, ChatMessage.Role.ASSISTANT, reply, tokens);

        return reply;
    }

    /**
     * Стриминговый вариант — для Telegram (печатает постепенно) или SSE.
     */
    public Flux<String> chatStream(Long userId, Domain domain, String message) {
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        String systemPrompt = buildSystemPrompt(user, domain);
        List<Message> history = buildHistory(userId, domain);
        history.add(new UserMessage(message));

        // Сохраняем входящее сообщение сразу
        String sessionId = buildSessionId(userId, domain);
        save(user, domain, sessionId, ChatMessage.Role.USER, message, null);

        StringBuilder fullReply = new StringBuilder();

        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .build()
                .prompt(new Prompt(history))
                .stream()
                .content()
                .doOnNext(fullReply::append)
                .doOnComplete(() -> {
                    // Сохраняем полный ответ после стриминга
                    save(user, domain, sessionId, ChatMessage.Role.ASSISTANT,
                            fullReply.toString(), null);
                    log.debug("Stream complete for domain [{}]",
                            domain != null ? domain.getSlug() : "meta");
                });
    }

    // ─── Построение системного промпта ───────────────────────────────────────

    /**
     * Системный промпт = базовый промпт коуча + цели + последние активности.
     * Для мета-коуча — сводка по всем доменам.
     */
    private String buildSystemPrompt(UserProfile user, Domain domain) {
        StringBuilder sb = new StringBuilder();

        if (domain == null) {
            // Мета-коуч: видит всё
            sb.append(metaCoachPrompt(user));
        } else {
            // Доменный коуч: только свой контекст
            sb.append(domain.getSystemPrompt());
            sb.append("\n\n");
            appendGoals(sb, domain);
            appendRecentActivity(sb, domain);
        }

        appendUserContext(sb, user);
        return sb.toString();
    }

    private String metaCoachPrompt(UserProfile user) {
        return """
            Ты — персональный AI-коуч %s. Видишь общую картину жизни пользователя.
            
            Твоя роль:
            - Проводить утренние и вечерние check-in по всем направлениям
            - Замечать паттерны между доменами (например: пропуск тренировок → раздражительность)
            - Давать еженедельную рефлексию и инсайты
            - Отвечать на общие вопросы о жизненном балансе
            - При необходимости направлять в нужный домен
            
            Говори живо, с заботой. Не перегружай вопросами — один-два самых важных.
            Отвечай на русском языке.
            """.formatted(user.getName());
    }

    private void appendGoals(StringBuilder sb, Domain domain) {
        List<DomainGoal> goals = goalRepository
                .findByDomainIdAndStatusOrderByCreatedAtDesc(domain.getId(), DomainGoal.GoalStatus.ACTIVE);

        if (goals.isEmpty()) return;

        sb.append("Активные цели пользователя в этом направлении:\n");
        goals.forEach(g -> {
            sb.append("- ").append(g.getTitle());
            if (g.getTargetDate() != null) {
                sb.append(" (дедлайн: ").append(g.getTargetDate()).append(")");
            }
            sb.append("\n");
        });
        sb.append("\n");
    }

    private void appendRecentActivity(StringBuilder sb, Domain domain) {
        List<ActivityLog> logs = activityLogRepository.findLastByDomainId(domain.getId(), 7);
        if (logs.isEmpty()) return;

        sb.append("Последние активности (новые сверху):\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM HH:mm");
        logs.forEach(a -> {
            sb.append("- [").append(a.getLoggedAt().format(fmt)).append("] ")
                    .append(a.getSummary());
            if (a.getMoodScore() != null) {
                sb.append(" (настроение: ").append(a.getMoodScore()).append("/5)");
            }
            sb.append("\n");
        });
        sb.append("\n");
    }

    private void appendUserContext(StringBuilder sb, UserProfile user) {
        sb.append("Пользователь: ").append(user.getName());
        if (user.getAge() != null) sb.append(", ").append(user.getAge()).append(" лет");
        sb.append(".\n");
    }

    // ─── Построение истории диалога ───────────────────────────────────────────

    private List<Message> buildHistory(Long userId, Domain domain) {
        Long domainId = domain != null ? domain.getId() : null;
        List<ChatMessage> dbMessages = chatMessageRepository
                .findLastByUserAndDomain(userId, domainId, maxHistory);

        // findLast возвращает DESC — разворачиваем в хронологию
        Collections.reverse(dbMessages);

        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : dbMessages) {
            if (msg.getRole() == ChatMessage.Role.USER) {
                messages.add(new UserMessage(msg.getContent()));
            } else {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        return messages;
    }

    // ─── Вспомогательные методы ───────────────────────────────────────────────

    private String buildSessionId(Long userId, Domain domain) {
        return "u" + userId + "_" + (domain != null ? domain.getSlug() : "meta");
    }

    private void save(UserProfile user, Domain domain, String sessionId,
                      ChatMessage.Role role, String content, Integer tokens) {
        chatMessageRepository.save(ChatMessage.builder()
                .user(user)
                .domain(domain)
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .tokenCount(tokens)
                .build());
    }

    // ─── Еженедельный отчёт (для мета-коуча) ─────────────────────────────────

    /**
     * Генерирует сводку активности за последние N дней по всем доменам.
     * Вызывается планировщиком или по запросу пользователя.
     */
    @Transactional(readOnly = true)
    public String generateWeeklySummary(Long userId) {
        LocalDateTime from = LocalDateTime.now().minusDays(weeklyReportDays);
        List<ActivityLog> allActivity = activityLogRepository
                .findAllByUserAndPeriod(userId, from);

        if (allActivity.isEmpty()) {
            return "За последние " + weeklyReportDays + " дней активностей не зафиксировано.";
        }

        // Группируем по домену
        Map<String, List<ActivityLog>> byDomain = allActivity.stream()
                .collect(Collectors.groupingBy(a -> a.getDomain().getName()));

        StringBuilder summary = new StringBuilder();
        summary.append("Активность за последние ").append(weeklyReportDays).append(" дней:\n\n");

        byDomain.forEach((domainName, logs) -> {
            summary.append(domainName).append(": ").append(logs.size()).append(" активностей\n");
            logs.stream().limit(3).forEach(log ->
                    summary.append("  • ").append(log.getSummary()).append("\n")
            );
        });

        // Находим домены с нулевой активностью
        List<Domain> allDomains = new ArrayList<>(); // упрощено, реально берём из репо
        // (см. CheckInScheduler который вызывает этот метод — там передаётся полный список)

        return summary.toString();
    }

    // ─── Геттеры для использования в других сервисах ─────────────────────────

    public List<ChatMessage> getHistory(Long userId, Long domainId) {
        return chatMessageRepository.findAllByUserAndDomainAsc(userId, domainId);
    }
}