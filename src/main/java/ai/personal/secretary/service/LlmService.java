package ai.personal.secretary.service;

import ai.personal.secretary.client.OpenRouterClient;
import ai.personal.secretary.model.Conversation;
import ai.personal.secretary.model.Message;
import ai.personal.secretary.repository.ConversationRepository;
import ai.personal.secretary.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LlmService {

    private final OpenRouterClient openRouterClient;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final RagService ragService;

    public String chat(Long conversationId, String userMessage) {

        Conversation conversation;

        if (conversationId == null) {

            conversation = new Conversation();
            conversation.setCreatedAt(
                    LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
            );

            conversation = conversationRepository.save(conversation);

        } else {

            conversation = conversationRepository
                    .findById(conversationId)
                    .orElseThrow();
        }

        List<Message> history =
                messageRepository.findByConversationIdOrderByCreatedAt(conversation.getId());

        List<Map<String, String>> messages = new ArrayList<>();

        for (Message msg : history) {

            Map<String, String> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());

            messages.add(m);
        }

        String context = ragService.buildContext(userMessage);

        if (!context.isEmpty()) {

            Map<String, String> system = new HashMap<>();
            system.put("role", "system");
            system.put("content", context);

            messages.add(system);
        }

        Map<String, String> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", userMessage);

        messages.add(user);

        String answer = openRouterClient.chat(messages);

        Message userMsg = new Message();
        userMsg.setConversation(conversation);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        userMsg.setCreatedAt(LocalDateTime.now());

        messageRepository.save(userMsg);

        Message assistantMsg = new Message();
        assistantMsg.setConversation(conversation);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(answer);
        assistantMsg.setCreatedAt(LocalDateTime.now());

        messageRepository.save(assistantMsg);

        return answer;
    }

    public String ask(Long conversationId, String message) {
        return chat(conversationId, message);
    }
}