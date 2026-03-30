package ai.personal.secretary.service;

import ai.personal.secretary.model.*;
import ai.personal.secretary.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
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

    private final OpenAiChatModel chatModel;
    private final ChatMessageRepository chatMessageRepository;
    private final DomainGoalRepository goalRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserProfileRepository userProfileRepository;
    private final MemoryService memoryService;

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
                .defaultSystem(buildSystemPrompt(user, domain, userMessage))
                .build()
                .prompt(new Prompt(history))
                .call()
                .chatResponse();

        String reply = response.getResult().getOutput().getText();
        int tokens = Optional.ofNullable(response.getMetadata().getUsage())
                .map(u -> (int) u.getTotalTokens())
                .orElse(0);

        String sessionId = sessionId(userId, domain);
        save(user, domain, sessionId, ChatMessage.MessageRole.USER, userMessage, null);
        save(user, domain, sessionId, ChatMessage.MessageRole.ASSISTANT, reply, tokens);

        // Сохраняем в долгосрочную память (асинхронно)
        memoryService.remember(userId,
                domain != null ? domain.getSlug() : null,
                userMessage);

        // Автосохранение активности — только если есть конкретный домен
        if (domain != null) {
            extractAndSaveActivity(user, domain, userMessage);
        }

        log.debug("[{}] tokens={}", domain != null ? domain.getSlug() : "meta", tokens);
        return reply;
    }

    // ── Автоизвлечение активности ─────────────────────────────────────────────

    /**
     * Анализирует сообщение пользователя и сохраняет активность если она там есть.
     * <p>
     * Использует отдельный лёгкий вызов к модели — classification prompt.
     * Ответ строго структурирован: "НЕТ" или "ДА|краткое описание".
     * Не блокирует основной ответ — ошибки тихо логируются.
     */
    private void extractAndSaveActivity(UserProfile user, Domain domain, String userMessage) {
        try {
            String extractPrompt = String.format("""
                    Проанализируй сообщение и определи — есть ли в нём конкретная активность
                    в домене "%s" которую стоит записать в дневник (тренировка, приём пищи,
                    прочитанные страницы, просмотренный фильм, практика и т.д.).
                                    
                    Правила:
                    - Если активность есть — ответь строго: ДА|краткое описание (1 строка, до 100 символов)
                    - Если активности нет (вопрос, рассуждение, планы) — ответь строго: НЕТ
                    - Никаких других слов кроме формата выше
                                    
                    Домен: %s
                    Сообщение: "%s"
                    """, domain.getName(), domain.getName(), userMessage);

            String result = ChatClient.builder(chatModel)
                    .build()
                    .prompt(extractPrompt)
                    .call()
                    .content()
                    .trim();

            if (result.startsWith("ДА|")) {
                String summary = result.substring(3).trim();
                if (!summary.isBlank()) {
                    activityLogRepository.save(ActivityLog.builder()
                            .user(user)
                            .domain(domain)
                            .loggedAt(LocalDateTime.now())
                            .summary(summary)
                            .build());
                    log.info("Auto-saved activity [{}]: {}", domain.getSlug(), summary);
                }
            }

        } catch (Exception e) {
            // Автосохранение некритично — не ломаем основной флоу
            log.debug("Activity extraction skipped: {}", e.getMessage());
        }
    }

    // ── Системный промпт ──────────────────────────────────────────────────────

    private String buildSystemPrompt(UserProfile user, Domain domain, String userMessage) {
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

        // Долгосрочная память — семантический поиск по прошлым разговорам
        String memories = memoryService.recall(
                user.getId(),
                domain != null ? domain.getSlug() : null,
                userMessage,
                4
        );
        if (!memories.isBlank()) sb.append(memories);

        sb.append("\nПользователь: ").append(user.getName());
        if (user.getAge() != null) sb.append(", ").append(user.getAge()).append(" лет");
        if (user.getWeightKg() != null) sb.append(", вес ").append(user.getWeightKg()).append(" кг");
        if (user.getHeightCm() != null) sb.append(", рост ").append(user.getHeightCm()).append(" см");
        if (user.getActivityLevel() != null) sb.append(", активность: ").append(user.getActivityLevel());
        if (user.getHealthNotes() != null) sb.append(". Здоровье: ").append(user.getHealthNotes());
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
        Collections.reverse(dbMessages);

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

    // ── Прогресс по целям ─────────────────────────────────────────────────────

    /**
     * Генерирует сводку активных целей с последними активностями по каждой.
     * Используется в вечернем check-in для формирования вопроса о прогрессе.
     */
    @Transactional(readOnly = true)
    public String buildGoalProgressContext(Long userId) {
        // Берём все активные домены пользователя
        var sb = new StringBuilder();
        var from = LocalDateTime.now().minusDays(7);
        var recentActivity = activityLogRepository.findAllByUserAndPeriod(userId, from);

        // Группируем активности по доменам
        var actByDomain = recentActivity.stream()
                .collect(Collectors.groupingBy(a -> a.getDomain().getId()));

        // Для каждого домена с активными целями собираем контекст
        goalRepository.findAll().stream()
                .filter(g -> g.getStatus() == DomainGoal.GoalStatus.ACTIVE)
                .filter(g -> g.getDomain().getUser().getId().equals(userId))
                .forEach(goal -> {
                    sb.append("• *").append(goal.getTitle()).append("*");
                    if (goal.getTargetDate() != null) {
                        sb.append(" (до ").append(goal.getTargetDate()).append(")");
                    }
                    sb.append("\n");

                    // Последние активности по этому домену
                    var acts = actByDomain.getOrDefault(goal.getDomain().getId(), List.of());
                    if (!acts.isEmpty()) {
                        sb.append("  За неделю: ");
                        acts.stream().limit(2)
                                .forEach(a -> sb.append(a.getSummary()).append("; "));
                        sb.append("\n");
                    } else {
                        sb.append("  Активностей за неделю нет\n");
                    }
                });

        return sb.toString();
    }

    /**
     * Генерирует вопрос о прогрессе по конкретной цели для вечернего check-in.
     */
    public String generateGoalCheckQuestion(Long userId) {
        String goalsContext = buildGoalProgressContext(userId);
        if (goalsContext.isBlank()) return "";

        String prompt = String.format("""
                Активные цели пользователя с прогрессом за неделю:
                %s
                            
                Задай 1-2 конкретных вопроса о прогрессе сегодня — по самым важным целям.
                Будь конкретным, не общим. Например: "Удалось позаниматься сегодня?" а не "Как дела с целями?"
                Максимум 3 предложения. Отвечай на русском.
                """, goalsContext);

        return chat(userId, null, prompt);
    }

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

    // ── Детектор целей ────────────────────────────────────────────────────────

    /**
     * Результат детектора: null = цели нет, иначе — текст цели для подтверждения.
     */
    public Optional<String> detectGoal(Domain domain, String userMessage) {
        if (domain == null) return Optional.empty();
        try {
            String prompt = String.format("""
                    Проанализируй сообщение. Есть ли в нём формулировка цели или намерения
                    которую стоит зафиксировать как цель в направлении "%s"?
                                    
                    Цель — это конкретное достижение к которому человек стремится:
                    "хочу пробежать 10км", "планирую прочитать 20 книг за год",
                    "хочу освоить стойку на руках к лету".
                                    
                    Правила ответа:
                    - Если цель есть — ответь строго: ДА|чёткая формулировка цели (до 150 символов)
                    - Если цели нет — ответь строго: НЕТ
                    - Никаких других слов
                                    
                    Домен: %s
                    Сообщение: "%s"
                    """, domain.getName(), domain.getName(), userMessage);

            String result = ChatClient.builder(chatModel).build()
                    .prompt(prompt).call().content().trim();

            if (result.startsWith("ДА|")) {
                String goalText = result.substring(3).trim();
                if (!goalText.isBlank()) {
                    log.debug("Goal detected in [{}]: {}", domain.getSlug(), goalText);
                    return Optional.of(goalText);
                }
            }
        } catch (Exception e) {
            log.debug("Goal detection skipped: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Сохраняет цель домена. Вызывается из CoachBot после подтверждения пользователем.
     */
    @Transactional
    public DomainGoal saveGoal(Long userId, Domain domain, String title) {
        DomainGoal goal = DomainGoal.builder()
                .domain(domain)
                .title(title)
                .status(DomainGoal.GoalStatus.ACTIVE)
                .build();
        DomainGoal saved = goalRepository.save(goal);
        log.info("Goal saved [{}]: {}", domain.getSlug(), title);
        return saved;
    }
}