package ai.personal.secretary.service;

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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoachService {

    private final AnthropicChatModel chatModel;
    private final ChatMessageRepository chatMessageRepository;
    private final DomainGoalRepository goalRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserProfileRepository userProfileRepository;

    @Value("${coach.max-history-messages:20}")
    private int maxHistory;

    @Value("${coach.weekly-report-days:7}")
    private int weeklyReportDays;

    // ── Основной метод ────────────────────────────────────────────────────────

    @Transactional
    public String chat(Long userId, Domain domain, String userMessage) {
        UserProfile user = findUser(userId);

        List<Message> history = buildHistory(userId, domain);
        history.add(new UserMessage(userMessage));

        ChatResponse response = ChatClient.builder(chatModel)
                .defaultSystem(buildSystemPrompt(user, domain))
                .build()
                .prompt(new Prompt(history))
                .call()
                .chatResponse();

        String reply = response.getResult().getOutput().getText();
        int tokens = Optional.ofNullable(response.getMetadata().getUsage())
                .map(u -> (int) u.getTotalTokens())
                .orElse(0);

        String sessionId = sessionId(userId, domain);
        // ChatMessage.MessageRole — зафиксированное имя, без вариантов
        save(user, domain, sessionId, ChatMessage.MessageRole.USER,      userMessage, null);
        save(user, domain, sessionId, ChatMessage.MessageRole.ASSISTANT, reply,       tokens);

        log.debug("[{}] tokens={}", domain != null ? domain.getSlug() : "meta", tokens);
        return reply;
    }

    // ── Системный промпт ──────────────────────────────────────────────────────

    private String buildSystemPrompt(UserProfile user, Domain domain) {
        var sb = new StringBuilder();

        if (domain == null) {
            sb.append("Ты — персональный AI-коуч ").append(user.getName()).append(".\n");
            sb.append("""
                Видишь общую картину жизни пользователя.
                Проводишь утренние и вечерние check-in, замечаешь паттерны между доменами,
                даёшь еженедельную рефлексию. Говори живо, с заботой.
                Не перегружай вопросами — один-два самых важных.
                Отвечай на русском языке.
                """);
        } else {
            sb.append(domain.getSystemPrompt()).append("\n\n");
            appendGoals(sb, domain);
            appendRecentActivity(sb, domain);
        }

        sb.append("\nПользователь: ").append(user.getName());
        if (user.getAge() != null) sb.append(", ").append(user.getAge()).append(" лет");
        sb.append(".\n");

        return sb.toString();
    }

    private void appendGoals(StringBuilder sb, Domain domain) {
        var goals = goalRepository.findByDomainIdAndStatusOrderByCreatedAtDesc(
                domain.getId(), DomainGoal.GoalStatus.ACTIVE);
        if (goals.isEmpty()) return;

        sb.append("Активные цели:\n");
        goals.forEach(g -> {
            sb.append("- ").append(g.getTitle());
            if (g.getTargetDate() != null) sb.append(" (до ").append(g.getTargetDate()).append(")");
            sb.append("\n");
        });
        sb.append("\n");
    }

    private void appendRecentActivity(StringBuilder sb, Domain domain) {
        var logs = activityLogRepository.findLastByDomainId(domain.getId(), 7);
        if (logs.isEmpty()) return;

        var fmt = DateTimeFormatter.ofPattern("dd.MM HH:mm");
        sb.append("Последние активности:\n");
        logs.forEach(a -> {
            sb.append("- [").append(a.getLoggedAt().format(fmt)).append("] ").append(a.getSummary());
            if (a.getMoodScore() != null) sb.append(" (настроение ").append(a.getMoodScore()).append("/5)");
            sb.append("\n");
        });
        sb.append("\n");
    }

    // ── История диалога ───────────────────────────────────────────────────────

    private List<Message> buildHistory(Long userId, Domain domain) {
        Long domainId = domain != null ? domain.getId() : null;
        var dbMessages = chatMessageRepository.findLastByUserAndDomain(userId, domainId, maxHistory);
        Collections.reverse(dbMessages); // DESC → ASC

        List<Message> messages = new ArrayList<>();
        for (var msg : dbMessages) {
            if (msg.getRole() == ChatMessage.MessageRole.USER) {
                messages.add(new UserMessage(msg.getContent()));
            } else {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        return messages;
    }

    // ── Еженедельный отчёт ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public String generateWeeklySummary(Long userId) {
        var from = LocalDateTime.now().minusDays(weeklyReportDays);
        var all = activityLogRepository.findAllByUserAndPeriod(userId, from);

        if (all.isEmpty()) return "За последние " + weeklyReportDays + " дней активностей нет.";

        var byDomain = all.stream().collect(Collectors.groupingBy(a -> a.getDomain().getName()));
        var sb = new StringBuilder("Активность за ").append(weeklyReportDays).append(" дней:\n\n");
        byDomain.forEach((name, logs) -> {
            sb.append(name).append(": ").append(logs.size()).append(" записей\n");
            logs.stream().limit(2).forEach(a -> sb.append("  • ").append(a.getSummary()).append("\n"));
        });
        return sb.toString();
    }

    // ── Вспомогательные ──────────────────────────────────────────────────────

    public List<ChatMessage> getHistory(Long userId, Long domainId) {
        return chatMessageRepository.findAllByUserAndDomainAsc(userId, domainId);
    }

    private UserProfile findUser(Long userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private String sessionId(Long userId, Domain domain) {
        return "u" + userId + "_" + (domain != null ? domain.getSlug() : "meta");
    }

    private void save(UserProfile user, Domain domain, String sessionId,
                      ChatMessage.MessageRole role, String content, Integer tokens) {
        chatMessageRepository.save(ChatMessage.builder()
                .user(user).domain(domain).sessionId(sessionId)
                .role(role).content(content).tokenCount(tokens)
                .build());
    }
}
