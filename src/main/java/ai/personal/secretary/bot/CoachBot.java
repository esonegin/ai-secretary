package ai.personal.secretary.bot;

import ai.personal.secretary.model.Domain;
import ai.personal.secretary.model.UserProfile;
import ai.personal.secretary.repository.DomainRepository;
import ai.personal.secretary.repository.UserProfileRepository;
import ai.personal.secretary.service.ActivityService;
import ai.personal.secretary.service.CoachService;
import ai.personal.secretary.service.DomainRouterService;
import ai.personal.secretary.service.DomainService;
import ai.personal.secretary.service.PublishService;
import ai.personal.secretary.service.StatsService;
import ai.personal.secretary.service.StravaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

@Component
@Slf4j
public class CoachBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final String botToken;

    private final CoachService coachService;
    private final DomainRouterService routerService;
    private final DomainService domainService;
    private final ActivityService activityService;
    private final DomainRepository domainRepository;
    private final UserProfileRepository userProfileRepository;
    private final PublishService publishService;
    private final StatsService statsService;
    private final StravaService stravaService;

    @Value("${telegram.bot.channel-id:0}")
    private String channelId;

    // chatId → выбранный slug домена (null = авто-роутинг)
    private final Map<Long, String> userDomainState = new ConcurrentHashMap<>();

    // chatId → ждём ввод активности для slug
    private final Map<Long, String> pendingActivity = new ConcurrentHashMap<>();

    // chatId → цель ожидающая подтверждения: "domainSlug|goalText"
    private final Map<Long, String> pendingGoal = new ConcurrentHashMap<>();

    // chatId → текущий шаг заполнения профиля
    private final Map<Long, String> profileStep = new ConcurrentHashMap<>();

    // chatId → тип публикации ожидающей контент ("книга", "фильм" и т.д.)
    private final Map<Long, String> pendingPublish = new ConcurrentHashMap<>();

    // chatId → черновик поста ожидающий подтверждения публикации
    private final Map<Long, String> pendingPost = new ConcurrentHashMap<>();

    // chatId → ждём ответ о прогрессе по целям (от scheduler)
    private final Set<Long> pendingGoalCheck = ConcurrentHashMap.newKeySet();

    // chatId → ждём тему для ежедневного поста (от scheduler)
    private final Set<Long> pendingDailyPost = ConcurrentHashMap.newKeySet();

    /** Вызывается из CheckInScheduler — переводим бота в режим ожидания ответа по целям */
    public void setPendingGoalCheck(Long chatId) {
        pendingGoalCheck.add(chatId);
    }

    /** Вызывается из CheckInScheduler — переводим бота в режим ожидания темы поста */
    public void setPendingDailyPost(Long chatId) {
        pendingDailyPost.add(chatId);
    }

    private static final Long USER_ID = 2L;

    // Шаги профиля по порядку
    private static final List<String> PROFILE_STEPS = List.of(
            "birth_date", "weight", "height", "activity", "health"
    );

    public CoachBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            CoachService coachService,
            DomainRouterService routerService,
            DomainService domainService,
            ActivityService activityService,
            DomainRepository domainRepository,
            UserProfileRepository userProfileRepository,
            PublishService publishService,
            StatsService statsService,
            StravaService stravaService) {

        this.botToken = botToken;
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.coachService = coachService;
        this.routerService = routerService;
        this.domainService = domainService;
        this.activityService = activityService;
        this.domainRepository = domainRepository;
        this.userProfileRepository = userProfileRepository;
        this.publishService = publishService;
        this.statsService = statsService;
        this.stravaService = stravaService;
    }

    @Override
    public String getBotToken() { return botToken; }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() { return this; }

    // ─── Главный обработчик ───────────────────────────────────────────────────

    @Override
    public void consume(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update);
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update);
            }
        } catch (Exception e) {
            log.error("Update error: {}", e.getMessage(), e);
        }
    }

    private void handleMessage(Update update) {
        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        // Приоритет: заполнение профиля
        if (profileStep.containsKey(chatId)) {
            handleProfileInput(chatId, text);
            return;
        }

        // Ответ на check по целям (от scheduler)
        if (pendingGoalCheck.remove(chatId)) {
            handleGoalCheckResponse(chatId, text);
            return;
        }

        // Ответ с темой для ежедневного поста (от scheduler)
        if (pendingDailyPost.remove(chatId)) {
            handleDailyPostResponse(chatId, text);
            return;
        }

        // Ожидаем контент для публикации
        if (pendingPublish.containsKey(chatId)) {
            handlePublishInput(chatId, text);
            return;
        }

        // Ожидаем описание для публикации
        if (pendingPost.containsKey(chatId) && pendingPost.get(chatId).startsWith("input:")) {
            handlePublishInput(chatId, text);
            return;
        }

        // Логирование активности
        if (pendingActivity.containsKey(chatId)) {
            logActivityFromInput(chatId, text);
            return;
        }

        if (text.startsWith("/")) {
            handleCommand(chatId, text);
        } else {
            handleChat(chatId, text);
        }
    }

    // ─── Команды ──────────────────────────────────────────────────────────────

    private void handleCommand(long chatId, String command) {
        String cmd = command.split(" ")[0].toLowerCase();
        switch (cmd) {
            case "/start"        -> onStart(chatId);
            case "/profile"      -> onProfileStart(chatId);
            case "/publish"      -> onPublish(chatId);
            case "/strava"       -> onStrava(chatId);
            case "/stats"        -> onStats(chatId, 7);
            case "/stats30"      -> onStats(chatId, 30);
            case "/strava-stats" -> onStravaStats(chatId);
            case "/domains"      -> onDomains(chatId);
            case "/free"         -> onFree(chatId);
            case "/goals"        -> onGoals(chatId);
            case "/log"          -> onLog(chatId, command);
            case "/summary"      -> onSummary(chatId);
            case "/help"         -> onHelp(chatId);
            default              -> handleChat(chatId, command);
        }
    }

    // ─── Публикация в канал ───────────────────────────────────────────────────

    private void onPublish(long chatId) {
        if ("0".equals(channelId)) {
            send(chatId, "❌ Канал не настроен. Добавь `TELEGRAM_CHANNEL_ID` в start.sh");
            return;
        }

        pendingPost.put(chatId, "input:null");

        sendWithKeyboard(chatId, "Что хочешь опубликовать? Выбери формат:",
                InlineKeyboardMarkup.builder().keyboard(List.of(
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder().text("📚 Книга").callbackData("publish:reading").build(),
                                InlineKeyboardButton.builder().text("🎬 Фильм").callbackData("publish:cinema").build()
                        ),
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder().text("🎵 Музыка").callbackData("publish:music").build(),
                                InlineKeyboardButton.builder().text("💭 Рефлексия").callbackData("publish:free").build()
                        )
                )).build());
    }

    private void handlePublishInput(long chatId, String text) {
        String state = pendingPost.get(chatId);

        // Режим обогащения — пользователь добавляет инфо к существующему черновику
        if (state != null && state.startsWith("enrich:")) {
            String existingDraft = state.substring(7);
            send(chatId, "⏳ Обновляю пост...");
            sendTyping(chatId);
            try {
                String enrichedContent = "Существующий черновик:\n" + existingDraft +
                        "\n\nДополнительная информация от автора: " + text +
                        "\n\nПерепиши пост, органично включив новую информацию. Сохрани стиль.";
                String newPost = publishService.generatePost(null, enrichedContent);
                pendingPost.put(chatId, "draft:" + newPost);
                send(chatId, "📝 *Обновлённый черновик:*\n\n" + newPost);
                sendWithKeyboard(chatId, "Публикуем?",
                        InlineKeyboardMarkup.builder().keyboard(List.of(
                                new InlineKeyboardRow(
                                        InlineKeyboardButton.builder()
                                                .text("✅ Опубликовать").callbackData("publish:confirm").build(),
                                        InlineKeyboardButton.builder()
                                                .text("✏️ Переписать").callbackData("publish:rewrite").build(),
                                        InlineKeyboardButton.builder()
                                                .text("➕ Добавить инфо").callbackData("publish:enrich").build(),
                                        InlineKeyboardButton.builder()
                                                .text("❌ Отмена").callbackData("publish:cancel").build()
                                )
                        )).build());
            } catch (Exception e) {
                pendingPost.remove(chatId);
                send(chatId, "❌ Ошибка: " + e.getMessage());
            }
            return;
        }

        // Обычный режим — state = "input:domainSlug"
        String domainSlug = state != null ? state.substring(6) : null;
        if ("null".equals(domainSlug)) domainSlug = null;

        send(chatId, "⏳ Генерирую пост...");
        sendTyping(chatId);

        try {
            String post = publishService.generatePost(domainSlug, text);
            // Сохраняем черновик
            pendingPost.put(chatId, "draft:" + post);

            send(chatId, "📝 *Черновик поста:*\n\n" + post);
            sendWithKeyboard(chatId, "Публикуем в канал?",
                    InlineKeyboardMarkup.builder().keyboard(List.of(
                            new InlineKeyboardRow(
                                    InlineKeyboardButton.builder()
                                            .text("✅ Опубликовать").callbackData("publish:confirm").build(),
                                    InlineKeyboardButton.builder()
                                            .text("✏️ Переписать").callbackData("publish:rewrite").build(),
                                    InlineKeyboardButton.builder()
                                            .text("➕ Добавить инфо").callbackData("publish:enrich").build(),
                                    InlineKeyboardButton.builder()
                                            .text("❌ Отмена").callbackData("publish:cancel").build()
                            )
                    )).build());
        } catch (Exception e) {
            pendingPost.remove(chatId);
            send(chatId, "❌ Не удалось сгенерировать пост: " + e.getMessage());
        }
    }

    private void publishToChannel(long chatId, String post) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(channelId)
                    .text(post)
                    .parseMode("Markdown")
                    .build());
            send(chatId, "✅ Опубликовано в канале!");
            log.info("Post published to channel {}", channelId);
        } catch (TelegramApiException e) {
            send(chatId, "❌ Ошибка публикации: " + e.getMessage());
            log.error("Publish failed: {}", e.getMessage());
        }
    }

    private void onStart(long chatId) {
        String name = userProfileRepository.findFirstByOrderByIdAsc()
                .map(UserProfile::getName).orElse("друг");
        send(chatId, String.format("""
            Привет, %s! 👋

            Я твой персональный AI-коуч. Слежу за твоими направлениями:
            спорт, питание, йога, чтение, кино, музыка, отношения, работа.

            Просто *пиши что угодно* — сам определю тему.
            Или выбери направление: /domains

            /profile — заполнить профиль
            /help — все команды
            """, name));
    }

    // ─── Профиль ──────────────────────────────────────────────────────────────

    private void onProfileStart(long chatId) {
        UserProfile profile = userProfileRepository.findFirstByOrderByIdAsc().orElse(null);

        String current = "";
        if (profile != null) {
            current = String.format("""
                *Текущий профиль:*
                • Дата рождения: %s
                • Возраст: %s
                • Вес: %s кг
                • Рост: %s см
                • Активность: %s
                • Здоровье: %s

                """,
                    profile.getBirthDate() != null ? profile.getBirthDate().toString() : "не задана",
                    profile.getAge() != null ? profile.getAge() + " лет" : "—",
                    profile.getWeightKg() != null ? profile.getWeightKg() : "—",
                    profile.getHeightCm() != null ? profile.getHeightCm() : "—",
                    profile.getActivityLevel() != null ? profile.getActivityLevel() : "—",
                    profile.getHealthNotes() != null ? profile.getHealthNotes() : "—"
            );
        }

        profileStep.put(chatId, "birth_date");
        send(chatId, current + "Заполняем профиль. Введи дату рождения в формате *ДД.ММ.ГГГГ*:\n_например: 15.03.1990_");
    }

    private void handleProfileInput(long chatId, String text) {
        String step = profileStep.get(chatId);
        UserProfile profile = userProfileRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> userProfileRepository.save(
                        UserProfile.builder().name("Евгений").build()));

        switch (step) {
            case "birth_date" -> {
                try {
                    LocalDate date = LocalDate.parse(text.trim(),
                            DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                    profile.setBirthDate(date);
                    userProfileRepository.save(profile);
                    profileStep.put(chatId, "weight");
                    send(chatId, "✅ Дата рождения сохранена. Тебе " + profile.getAge() + " лет.\n\nВведи вес в кг (например: *82*) или /skip:");
                } catch (DateTimeParseException e) {
                    send(chatId, "❌ Неверный формат. Введи дату в формате *ДД.ММ.ГГГГ*, например: 15.03.1990");
                }
            }
            case "weight" -> {
                if (!text.equals("/skip")) {
                    try {
                        profile.setWeightKg(Double.parseDouble(text.replace(",", ".")));
                        userProfileRepository.save(profile);
                    } catch (NumberFormatException e) {
                        send(chatId, "❌ Введи число, например: 82");
                        return;
                    }
                }
                profileStep.put(chatId, "height");
                send(chatId, "✅ Отлично!\n\nВведи рост в см (например: *180*) или /skip:");
            }
            case "height" -> {
                if (!text.equals("/skip")) {
                    try {
                        profile.setHeightCm(Double.parseDouble(text.replace(",", ".")));
                        userProfileRepository.save(profile);
                    } catch (NumberFormatException e) {
                        send(chatId, "❌ Введи число, например: 180");
                        return;
                    }
                }
                profileStep.put(chatId, "activity");
                sendWithKeyboard(chatId, "✅ Отлично!\n\nВыбери уровень физической активности:",
                        InlineKeyboardMarkup.builder().keyboard(List.of(
                                new InlineKeyboardRow(
                                        InlineKeyboardButton.builder().text("🪑 Низкий").callbackData("profile:activity:low").build(),
                                        InlineKeyboardButton.builder().text("🚶 Умеренный").callbackData("profile:activity:moderate").build()
                                ),
                                new InlineKeyboardRow(
                                        InlineKeyboardButton.builder().text("🏃 Активный").callbackData("profile:activity:active").build(),
                                        InlineKeyboardButton.builder().text("⚡ Очень активный").callbackData("profile:activity:very_active").build()
                                )
                        )).build());
            }
            case "health" -> {
                if (!text.equals("/skip")) {
                    profile.setHealthNotes(text);
                    userProfileRepository.save(profile);
                }
                profileStep.remove(chatId);
                send(chatId, "✅ Профиль заполнен! Теперь я знаю о тебе больше и буду учитывать это в советах. 🎯");
            }
        }
    }

    // ─── Domains ──────────────────────────────────────────────────────────────

    private void onDomains(long chatId) {
        List<Domain> domains = domainService.getAll(USER_ID);
        if (domains.isEmpty()) { send(chatId, "Нет активных направлений."); return; }

        List<InlineKeyboardRow> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        for (Domain d : domains) {
            String label = (d.getIcon() != null ? d.getIcon() + " " : "") + d.getName();
            row.add(InlineKeyboardButton.builder()
                    .text(label).callbackData("domain:" + d.getSlug()).build());
            if (row.size() == 2) { rows.add(new InlineKeyboardRow(row)); row = new ArrayList<>(); }
        }
        if (!row.isEmpty()) rows.add(new InlineKeyboardRow(row));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("🧠 Мета-коуч").callbackData("domain:meta").build()
        ));

        String current = userDomainState.containsKey(chatId)
                ? "_Сейчас: " + userDomainState.get(chatId) + "_\n\n" : "";
        sendWithKeyboard(chatId, current + "Выбери направление:",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void onFree(long chatId) {
        userDomainState.remove(chatId);
        send(chatId, "Авто-режим ✅ Пиши что угодно — сам разберусь к чему это.");
    }

    private void onGoals(long chatId) {
        var sb = new StringBuilder("📋 *Активные цели:*\n\n");
        boolean has = false;
        for (Domain d : domainService.getAll(USER_ID)) {
            var goals = domainService.getGoals(d.getId());
            if (!goals.isEmpty()) {
                has = true;
                sb.append("*").append(d.getName()).append(":*\n");
                goals.forEach(g -> {
                    sb.append("• ").append(g.getTitle());
                    if (g.getTargetDate() != null)
                        sb.append(" _до ").append(g.getTargetDate()).append("_");
                    sb.append("\n");
                });
                sb.append("\n");
            }
        }
        send(chatId, has ? sb.toString() : "Целей пока нет. Скажи коучу что хочешь достичь.");
    }

    private void onLog(long chatId, String command) {
        String[] parts = command.split(" ", 3);
        if (parts.length < 3) {
            send(chatId, "Формат: `/log <домен> <описание>`\nПример: `/log sport Пробежал 5км`");
            return;
        }
        try {
            activityService.log(USER_ID, parts[1].toLowerCase(), parts[2], null, null, null);
            send(chatId, "✅ Записано в *" + parts[1] + "*: " + parts[2]);
        } catch (Exception e) {
            send(chatId, "Домен `" + parts[1] + "` не найден. Список: /domains");
        }
    }

    private void onSummary(long chatId) {
        send(chatId, "⏳ Генерирую...");
        String data = coachService.generateWeeklySummary(USER_ID);
        String prompt = "Данные за неделю:\n\n" + data +
                "\n\nКороткий анализ: что хорошо, что провисает, одна рекомендация на следующую неделю.";
        String reply = coachService.chat(USER_ID, null, prompt);
        send(chatId, "📊 *Итоги недели*\n\n" + reply);
    }

    private void onStrava(long chatId) {
        send(chatId, "⏳ Синхронизирую тренировки со Strava...");
        int count = stravaService.syncNow();
        if (count < 0) {
            send(chatId, "❌ Strava не настроена. Добавь токены в start.sh");
        } else if (count == 0) {
            send(chatId, "✅ Новых тренировок за последние 7 дней нет.");
        } else {
            send(chatId, "✅ Импортировано *" + count + "* тренировок из Strava!\n\nПосмотреть: /domains → Спорт");
        }
    }

    private void onStats(long chatId, int days) {
        send(chatId, statsService.buildStats(days));
    }

    private void onStravaStats(long chatId) {
        send(chatId, statsService.buildStravaStats(7));
    }

    private void onHelp(long chatId) {
        send(chatId, """
            *Команды:*

            /profile — заполнить/обновить профиль
            /domains — выбрать направление
            /free — авто-режим (определяю тему сам)
            /goals — твои активные цели
            /stats — статистика за 7 дней
            /stats30 — статистика за 30 дней
            /strava — синхронизировать тренировки из Strava
            /strava\\-stats — детальная статистика Strava
            /log <домен> <текст> — записать активность
            /publish — опубликовать пост в канал
            /summary — итоги недели
            /help — справка

            Просто *пиши* — и я разберусь 💬
            """);
    }

    // ─── Обработка ответов от scheduler ──────────────────────────────────────

    /**
     * Пользователь ответил на вопрос о прогрессе по целям.
     * Сохраняем как активность мета-домена и благодарим.
     */
    private void handleGoalCheckResponse(long chatId, String text) {
        sendTyping(chatId);

        // Просим коуча проанализировать ответ о прогрессе
        String prompt = String.format("""
            Пользователь ответил на вопрос о прогрессе по целям: "%s"
            
            Дай короткий отклик (2-3 предложения): отметь что хорошо, если есть провал — поддержи,
            не нотации. Потом сохрани это как факт для следующих разговоров.
            """, text);

        String reply = coachService.chat(USER_ID, null, prompt);
        send(chatId, reply);
    }

    /**
     * Пользователь написал тему или контент для ежедневного поста.
     * Генерируем черновик с учётом всего контекста и показываем для проверки.
     */
    private void handleDailyPostResponse(long chatId, String text) {
        sendTyping(chatId);
        send(chatId, "⏳ Готовлю черновик поста...");

        try {
            // Обогащаем контент контекстом из целей и активностей
            String goalsContext = coachService.buildGoalProgressContext(USER_ID);
            String weeklySummary = coachService.generateWeeklySummary(USER_ID);

            String enrichedContent = String.format("""
                Сообщение пользователя: %s
                
                Дополнительный контекст (использовать если нужно):
                Цели и прогресс: %s
                Активность за неделю: %s
                """, text,
                    goalsContext.isBlank() ? "нет" : goalsContext,
                    weeklySummary.length() > 300 ? weeklySummary.substring(0, 300) : weeklySummary);

            String post = publishService.generatePost(null, enrichedContent);

            // Сохраняем черновик
            pendingPost.put(chatId, "draft:" + post);

            send(chatId, "📝 *Черновик поста:*\n\n" + post);
            sendWithKeyboard(chatId, "Публикуем в канал?",
                    InlineKeyboardMarkup.builder().keyboard(List.of(
                            new InlineKeyboardRow(
                                    InlineKeyboardButton.builder()
                                            .text("✅ Опубликовать").callbackData("publish:confirm").build(),
                                    InlineKeyboardButton.builder()
                                            .text("✏️ Переписать").callbackData("publish:rewrite").build(),
                                    InlineKeyboardButton.builder()
                                            .text("➕ Добавить инфо").callbackData("publish:enrich").build(),
                                    InlineKeyboardButton.builder()
                                            .text("❌ Отмена").callbackData("publish:cancel").build()
                            )
                    )).build());
        } catch (Exception e) {
            pendingPost.remove(chatId);
            send(chatId, "❌ Не удалось создать черновик: " + e.getMessage());
            log.error("Daily post generation failed: {}", e.getMessage());
        }
    }

    // ─── Callback ─────────────────────────────────────────────────────────────

    private void handlePublishCallback(long chatId, String data) {
        switch (data) {
            case "publish:reading", "publish:cinema", "publish:music" -> {
                String slug = data.substring(8); // после "publish:"
                pendingPost.put(chatId, "input:" + slug);
                String hint = switch (slug) {
                    case "reading" -> "Напиши о книге: название, автор, что зацепило, главные идеи.";
                    case "cinema"  -> "Напиши о фильме: название, год, впечатления, что запомнилось.";
                    case "music"   -> "Напиши о музыке: трек/альбом/артист, атмосфера, почему нравится.";
                    default        -> "Опиши что хочешь опубликовать.";
                };
                send(chatId, "✍️ " + hint);
            }
            case "publish:free" -> {
                pendingPost.put(chatId, "input:null");
                send(chatId, "✍️ Напиши о чём хочешь поразмышлять или поделиться.");
            }
            case "publish:confirm" -> {
                String state = pendingPost.remove(chatId);
                if (state != null && state.startsWith("draft:")) {
                    String post = state.substring(6);
                    publishToChannel(chatId, post);
                }
            }
            case "publish:rewrite" -> {
                String state = pendingPost.get(chatId);
                if (state != null && state.startsWith("draft:")) {
                    pendingPost.put(chatId, "input:null");
                    send(chatId, "✍️ Опиши заново или уточни что изменить:");
                }
            }
            case "publish:enrich" -> {
                // Просим пользователя добавить больше информации
                String state = pendingPost.get(chatId);
                if (state != null && state.startsWith("draft:")) {
                    pendingPost.put(chatId, "enrich:" + state.substring(6));
                    send(chatId, "➕ Добавь любую информацию которую хочешь включить в пост — " +
                            "факт, цитату, личное наблюдение, или напиши \"найди интересный факт по теме\":");
                }
            }
            case "publish:cancel" -> {
                pendingPost.remove(chatId);
                send(chatId, "Публикация отменена.");
            }
        }
    }

    private void handleCallback(Update update) {
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        String data = update.getCallbackQuery().getData();

        if (data.startsWith("domain:")) {
            String slug = data.substring(7);
            if ("meta".equals(slug)) {
                userDomainState.remove(chatId);
                send(chatId, "🧠 *Мета-коуч.* Слежу за всеми направлениями сразу.");
            } else {
                userDomainState.put(chatId, slug);
                domainRepository.findByUserIdAndSlug(USER_ID, slug).ifPresent(d -> {
                    String icon = d.getIcon() != null ? d.getIcon() + " " : "";
                    send(chatId, icon + "*" + d.getName() + "* — слушаю.");
                });
            }
        } else if (data.startsWith("publish:")) {
            handlePublishCallback(chatId, data);
        } else if (data.startsWith("goal:")) {
            String pending = pendingGoal.remove(chatId);
            if (pending == null) return;

            if ("goal:confirm".equals(data)) {
                String[] parts = pending.split("\\|", 2);
                String slug = parts[0];
                String goalText = parts[1];
                domainRepository.findByUserIdAndSlug(USER_ID, slug).ifPresent(d -> {
                    coachService.saveGoal(USER_ID, d, goalText);
                    send(chatId, "✅ Цель зафиксирована в *" + d.getName() + "*!\n\n" +
                            "Посмотреть все цели: /goals");
                });
            } else {
                send(chatId, "Хорошо, просто разговор 👍");
            }
        } else if (data.startsWith("profile:activity:")) {
            String level = data.substring("profile:activity:".length());
            userProfileRepository.findFirstByOrderByIdAsc().ifPresent(p -> {
                p.setActivityLevel(level);
                userProfileRepository.save(p);
            });
            profileStep.put(chatId, "health");
            send(chatId, "✅ Уровень активности сохранён.\n\nЕсть ли что-то важное по здоровью что я должен учитывать? (аллергии, травмы, особенности)\nИли /skip:");
        }
    }

    // ─── Свободный диалог ─────────────────────────────────────────────────────

    private void handleChat(long chatId, String text) {
        sendTyping(chatId);

        String fixedSlug = userDomainState.get(chatId);
        Optional<Domain> domain = fixedSlug != null
                ? domainRepository.findByUserIdAndSlug(USER_ID, fixedSlug)
                : routerService.route(USER_ID, text);

        String reply = coachService.chat(USER_ID, domain.orElse(null), text);

        if (fixedSlug == null && domain.isPresent()) {
            Domain d = domain.get();
            String tag = "\n\n_— " + (d.getIcon() != null ? d.getIcon() + " " : "") + d.getName() + "_";
            reply = reply + tag;
        }

        send(chatId, reply);

        // Детектор целей — предлагаем зафиксировать если нашли цель
        if (domain.isPresent()) {
            Domain d = domain.get();
            coachService.detectGoal(d, text).ifPresent(goalText -> {
                pendingGoal.put(chatId, d.getSlug() + "|" + goalText);
                sendWithKeyboard(chatId,
                        "🎯 Похоже ты сформулировал цель:\n\n*" + goalText + "*\n\nЗафиксировать?",
                        InlineKeyboardMarkup.builder().keyboard(List.of(
                                new InlineKeyboardRow(
                                        InlineKeyboardButton.builder()
                                                .text("✅ Да, сохранить").callbackData("goal:confirm").build(),
                                        InlineKeyboardButton.builder()
                                                .text("❌ Нет").callbackData("goal:cancel").build()
                                )
                        )).build());
            });
        }
    }

    private void logActivityFromInput(long chatId, String text) {
        String slug = pendingActivity.remove(chatId);
        try {
            activityService.log(USER_ID, slug, text, null, null, null);
            send(chatId, "✅ Записано! Хочешь что-то обсудить?");
        } catch (Exception e) {
            send(chatId, "Не удалось записать. Попробуй: /log " + slug + " " + text);
        }
    }

    // ─── Утилиты ──────────────────────────────────────────────────────────────

    private void send(long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId).text(text).parseMode("Markdown").build());
        } catch (TelegramApiException e) {
            log.error("Send failed chatId={}: {}", chatId, e.getMessage());
        }
    }

    private void sendWithKeyboard(long chatId, String text, InlineKeyboardMarkup kb) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId).text(text).parseMode("Markdown").replyMarkup(kb).build());
        } catch (TelegramApiException e) {
            log.error("Send keyboard failed: {}", e.getMessage());
        }
    }

    private void sendTyping(long chatId) {
        try {
            telegramClient.execute(SendChatAction.builder()
                    .chatId(chatId).action("typing").build());
        } catch (TelegramApiException e) {
            log.debug("Typing indicator failed: {}", e.getMessage());
        }
    }

    public void sendToChat(Long chatId, String text) {
        send(chatId, text);
    }
}