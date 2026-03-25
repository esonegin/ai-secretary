package ai.personal.secretary.service;

/**
 * @author onegines
 * @date 25.03.2026
 */

import ai.personal.secretary.model.ChatMessage;
import ai.personal.secretary.model.UserProfile;
import ai.personal.secretary.repository.ChatMessageRepository;
import ai.personal.secretary.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Основной сервис агента-секретаря.
 *
 * Алгоритм обработки каждого сообщения:
 * 1. Загружаем профиль пользователя (если есть) → обогащаем system prompt
 * 2. Загружаем последние N сообщений сессии → собираем контекст
 * 3. Добавляем новое сообщение пользователя
 * 4. Отправляем в Claude через ChatClient
 * 5. Сохраняем оба сообщения (user + assistant) в БД
 * 6. Возвращаем ответ
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatClient chatClient;
    private final ChatMessageRepository chatMessageRepository;
    private final UserProfileRepository userProfileRepository;

    @Value("${secretary.max-history-messages:20}")
    private int maxHistoryMessages;

    /**
     * Главный метод — отправить сообщение агенту и получить ответ.
     *
     * @param sessionId  ID диалога (можно генерировать UUID на фронте)
     * @param userText   текст от пользователя
     * @param profileId  ID профиля пользователя (может быть null)
     * @return ответ ассистента
     */
    @Transactional
    public String chat(String sessionId, String userText, Long profileId) {
        log.debug("Chat request: session={}, profileId={}, text={}", sessionId, profileId, userText);

        // 1. Собираем историю для контекста
        List<Message> history = buildHistory(sessionId);

        // 2. Строим промпт: история + новое сообщение пользователя
        //    Профиль пользователя вшиваем в user-сообщение если есть
        String enrichedUserText = enrichWithProfile(userText, profileId);
        history.add(new UserMessage(enrichedUserText));

        // 3. Вызов Claude
        ChatResponse response = chatClient
                .prompt(new Prompt(history))
                .call()
                .chatResponse();

        String assistantText = response.getResult().getOutput().getText();
        int usageTokens = Optional.ofNullable(response.getMetadata().getUsage())
                .map(u -> (int) u.getTotalTokens())
                .orElse(0);

        log.debug("Claude response: tokens={}, text_length={}", usageTokens, assistantText.length());

        // 4. Сохраняем оба сообщения
        saveMessage(sessionId, ChatMessage.MessageRole.USER, userText, null);
        saveMessage(sessionId, ChatMessage.MessageRole.ASSISTANT, assistantText, usageTokens);

        return assistantText;
    }

    /**
     * Потоковый вариант для будущего Telegram/Web SSE интерфейса.
     * Пока возвращает Flux<String> — подключить к SSE endpoint.
     */
    public reactor.core.publisher.Flux<String> chatStream(String sessionId, String userText, Long profileId) {
        List<Message> history = buildHistory(sessionId);
        String enrichedUserText = enrichWithProfile(userText, profileId);
        history.add(new UserMessage(enrichedUserText));

        return chatClient
                .prompt(new Prompt(history))
                .stream()
                .content()
                .doOnComplete(() -> log.debug("Stream completed for session={}", sessionId));
    }

    /**
     * Возвращает историю сообщений сессии в формате для отображения.
     */
    public List<ChatMessage> getHistory(String sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * Загружает последние maxHistoryMessages сообщений и конвертирует
     * в формат Spring AI Message для передачи в промпт.
     */
    private List<Message> buildHistory(String sessionId) {
        List<ChatMessage> dbMessages = chatMessageRepository
                .findLastBySessionId(sessionId, maxHistoryMessages);

        // findLastBySessionId возвращает DESC — разворачиваем в хронологию
        Collections.reverse(dbMessages);

        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : dbMessages) {
            if (msg.getRole() == ChatMessage.MessageRole.USER) {
                messages.add(new UserMessage(msg.getContent()));
            } else {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        return messages;
    }

    /**
     * Если есть профиль — добавляем ключевые данные к сообщению пользователя.
     * Claude учтёт их при ответе без необходимости менять системный промпт.
     */
    private String enrichWithProfile(String userText, Long profileId) {
        if (profileId == null) return userText;

        return userProfileRepository.findById(profileId)
                .map(profile -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[Контекст пользователя: ");
                    sb.append("Имя: ").append(profile.getName()).append(". ");
                    if (profile.getAge() != null) {
                        sb.append("Возраст: ").append(profile.getAge()).append(". ");
                    }
                    if (profile.getActivityLevel() != null) {
                        sb.append("Активность: ").append(profile.getActivityLevel()).append(". ");
                    }
                    if (profile.getHealthNotes() != null) {
                        sb.append("Здоровье: ").append(profile.getHealthNotes()).append(". ");
                    }
                    if (profile.getContentPreferences() != null) {
                        sb.append("Предпочтения: ").append(profile.getContentPreferences()).append(". ");
                    }
                    sb.append("]\n\n");
                    sb.append(userText);
                    return sb.toString();
                })
                .orElse(userText);
    }

    private void saveMessage(String sessionId, ChatMessage.MessageRole role, String content, Integer tokenCount) {
        ChatMessage msg = ChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .tokenCount(tokenCount)
                .build();
        chatMessageRepository.save(msg);
    }
}