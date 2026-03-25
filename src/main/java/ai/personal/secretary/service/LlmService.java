package ai.personal.secretary.service;

import ai.personal.secretary.client.OpenRouterClient;
import ai.personal.secretary.model.Conversation;
import ai.personal.secretary.model.Message;
import ai.personal.secretary.model.UserProfile;
import ai.personal.secretary.repository.ConversationRepository;
import ai.personal.secretary.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LlmService {

    private final OpenRouterClient openRouterClient;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final RagService ragService;
    private final UserProfileService userProfileService;

    public String ask(Long conversationId, String userMessage) {
        Conversation conversation = getOrCreateConversation(conversationId);

        List<Message> history =
                messageRepository.findByConversationIdOrderByCreatedAt(conversation.getId());

        String ragContext = ragService.buildContext(userMessage);
        UserProfile profile = userProfileService.getOrCreate();

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", buildSystemPrompt(profile, ragContext)));

        for (Message historyMessage : history) {
            messages.add(message(historyMessage.getRole(), historyMessage.getContent()));
        }

        messages.add(message("user", userMessage));

        saveMessage(conversation, "user", userMessage);

        String assistantAnswer = openRouterClient.chat(messages);

        saveMessage(conversation, "assistant", assistantAnswer);

        return assistantAnswer;
    }

    private Conversation getOrCreateConversation(Long conversationId) {
        if (conversationId != null) {
            return conversationRepository.findById(conversationId)
                    .orElseGet(this::createConversation);
        }
        return createConversation();
    }

    private Conversation createConversation() {
        Conversation conversation = new Conversation();
        conversation.setCreatedAt(Instant.now());
        return conversationRepository.save(conversation);
    }

    private void saveMessage(Conversation conversation, String role, String content) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(Instant.now());
        messageRepository.save(message);
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("role", role);
        map.put("content", content);
        return map;
    }

    private String buildSystemPrompt(UserProfile profile, String ragContext) {
        return """
                Ты — персональный AI-ассистент пользователя.

                Правила ответа:
                - отвечай на русском языке, если пользователь не попросил иначе
                - отвечай кратко, практично и структурировано
                - не выдумывай факты
                - если данных недостаточно, прямо так и скажи
                - учитывай профиль пользователя и контекст из базы знаний
                - давай рекомендации, которые можно применить на практике

                Профиль пользователя:
                Цели: %s
                Питание: %s
                Образ жизни: %s
                Дополнительно: %s

                Контекст из базы знаний:
                %s
                """.formatted(
                safe(profile.getGoals()),
                safe(profile.getNutrition()),
                safe(profile.getLifestyle()),
                safe(profile.getNotes()),
                safe(ragContext)
        );
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "не указано" : value;
    }
}