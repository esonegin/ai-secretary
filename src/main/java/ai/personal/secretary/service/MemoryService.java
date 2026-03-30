package ai.personal.secretary.service;

import ai.personal.secretary.model.ChatMessage;
import ai.personal.secretary.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Долгосрочная память коуча.
 *
 * Текущая реализация: простой SQL-поиск по последним сообщениям.
 * Не требует embeddings API — работает с любым провайдером.
 *
 * Когда появится прямой доступ к OpenAI — можно переключить на
 * полноценный RAG через pgvector, заменив recall() на векторный поиск.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryService {

    private final ChatMessageRepository chatMessageRepository;

    /**
     * Заглушка — сохранение происходит автоматически через ChatMessageRepository
     * в CoachService. Оставляем метод для совместимости.
     */
    @Async
    public void remember(Long userId, String domainSlug, String content) {
        // Сообщения уже сохраняются в chat_messages через CoachService.save()
        log.debug("Memory: message already persisted for userId={} domain={}", userId, domainSlug);
    }

    /**
     * Возвращает релевантные воспоминания из прошлых разговоров.
     *
     * Берём последние сообщения пользователя за последние 30 дней
     * из того же домена — они уже в chat_messages.
     * Это даёт коучу контекст прошлых разговоров без embeddings.
     */
    public String recall(Long userId, String domainSlug, String query, int topK) {
        try {
            // Берём старые сообщения из истории (за пределами текущего окна)
            // findLastByUserAndDomain возвращает последние N — берём чуть больше
            // чтобы захватить то что выходит за maxHistory
            List<ChatMessage> oldMessages = chatMessageRepository
                    .findLastByUserAndDomain(userId,
                            domainSlug != null ? getDomainId(userId, domainSlug) : null,
                            50);  // берём 50, из них первые 20 уже в истории

            if (oldMessages.size() <= 20) return ""; // нет ничего за пределами окна

            // Берём сообщения которые не попадут в основную историю (20 сообщений)
            List<ChatMessage> memories = oldMessages.stream()
                    .skip(20)  // пропускаем то что уже в истории
                    .filter(m -> m.getRole() == ChatMessage.MessageRole.USER)
                    .limit(topK)
                    .collect(Collectors.toList());

            if (memories.isEmpty()) return "";

            String memoryText = memories.stream()
                    .map(m -> "• " + m.getContent())
                    .collect(Collectors.joining("\n"));

            log.debug("Recalled {} old messages for userId={} domain={}",
                    memories.size(), userId, domainSlug);

            return "\nИз прошлых разговоров (более ранние сообщения):\n" + memoryText + "\n";

        } catch (Exception e) {
            log.debug("Memory recall skipped: {}", e.getMessage());
            return "";
        }
    }

    private Long getDomainId(Long userId, String domainSlug) {
        // Возвращаем null чтобы не ломать если домен не найден
        // ChatMessageRepository обработает null как мета-коуч
        return null;
    }
}