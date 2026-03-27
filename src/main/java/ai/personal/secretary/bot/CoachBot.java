package ai.personal.secretary.bot;

import ai.personal.secretary.model.Domain;
import ai.personal.secretary.repository.DomainRepository;
import ai.personal.secretary.repository.UserProfileRepository;
import ai.personal.secretary.service.ActivityService;
import ai.personal.secretary.service.CoachService;
import ai.personal.secretary.service.DomainRouterService;
import ai.personal.secretary.service.DomainService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    // chatId → выбранный slug домена (null = авто-роутинг)
    private final Map<Long, String> userDomainState = new ConcurrentHashMap<>();

    // chatId → ждём ввод активности для slug
    private final Map<Long, String> pendingActivity = new ConcurrentHashMap<>();

    private static final Long USER_ID = 2L;

    public CoachBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            CoachService coachService,
            DomainRouterService routerService,
            DomainService domainService,
            ActivityService activityService,
            DomainRepository domainRepository,
            UserProfileRepository userProfileRepository) {

        this.botToken = botToken;
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.coachService = coachService;
        this.routerService = routerService;
        this.domainService = domainService;
        this.activityService = activityService;
        this.domainRepository = domainRepository;
        this.userProfileRepository = userProfileRepository;
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
            case "/start"   -> onStart(chatId);
            case "/domains" -> onDomains(chatId);
            case "/free"    -> onFree(chatId);
            case "/goals"   -> onGoals(chatId);
            case "/log"     -> onLog(chatId, command);
            case "/summary" -> onSummary(chatId);
            case "/help"    -> onHelp(chatId);
            default         -> handleChat(chatId, command);
        }
    }

    private void onStart(long chatId) {
        String name = userProfileRepository.findFirstByOrderByIdAsc()
                .map(u -> u.getName()).orElse("друг");
        send(chatId, String.format("""
            Привет, %s! 👋

            Я твой персональный AI-коуч. Слежу за твоими направлениями:
            спорт, питание, йога, чтение, кино, музыка, отношения, работа.

            Просто *пиши что угодно* — сам определю тему.
            Или выбери направление: /domains

            /help — все команды
            """, name));
    }

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
            InlineKeyboardButton.builder()
                .text("🧠 Мета-коуч").callbackData("domain:meta").build()
        ));

        String current = userDomainState.containsKey(chatId)
                ? "_Сейчас выбрано: " + userDomainState.get(chatId) + "_\n\n" : "";

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
        // /log sport Пробежал 5км
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

    private void onHelp(long chatId) {
        send(chatId, """
            *Команды:*

            /domains — выбрать направление
            /free — авто-режим (определяю тему сам)
            /goals — твои активные цели
            /log <домен> <текст> — записать активность
            /summary — итоги недели
            /help — справка

            Просто *пиши* — и я разберусь 💬
            """);
    }

    // ─── Callback (инлайн-кнопки) ─────────────────────────────────────────────

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

        // В авто-режиме показываем какой коуч ответил
        if (fixedSlug == null && domain.isPresent()) {
            Domain d = domain.get();
            String tag = "\n\n_— " + (d.getIcon() != null ? d.getIcon() + " " : "") + d.getName() + "_";
            reply = reply + tag;
        }

        send(chatId, reply);
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

    // ─── Утилиты отправки ─────────────────────────────────────────────────────

    private void send(long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId).text(text).parseMode("Markdown").build());
        } catch (TelegramApiException e) {
            log.error("Send failed for chatId={}: {}", chatId, e.getMessage());
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
            log.debug("Typing indicator failed (non-critical): {}", e.getMessage());
        }
    }

    /** Вызывается из CheckInScheduler */
    public void sendToChat(Long chatId, String text) {
        send(chatId, text);
    }
}
