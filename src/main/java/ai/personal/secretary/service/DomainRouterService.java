package ai.personal.secretary.service;

/**
 * @author onegines
 * @date 25.03.2026
 */

import ai.personal.secretary.model.Domain;
import ai.personal.secretary.repository.DomainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Маршрутизатор сообщений по доменам.
 *
 * Задача: определить к какому домену относится сообщение пользователя.
 * Например: "сегодня пробежал 5км" → sport, "посмотрел Дюну" → cinema.
 *
 * Алгоритм:
 * 1. Сначала пробуем быстрое keyword-matching (без вызова API).
 * 2. Если неоднозначно — вызываем Claude с простым classification prompt.
 * 3. Если не определён — возвращаем Optional.empty() → мета-коуч.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DomainRouterService {

    private final AnthropicChatModel chatModel;
    private final DomainRepository domainRepository;

    /**
     * Определяет домен по тексту сообщения.
     *
     * @param userId  ID пользователя
     * @param message текст сообщения
     * @return домен или empty() для мета-коуча
     */
    public Optional<Domain> route(Long userId, String message) {
        List<Domain> activeDomains = domainRepository
                .findByUserIdAndIsActiveTrueOrderBySortOrderAsc(userId);

        if (activeDomains.isEmpty()) return Optional.empty();

        // Шаг 1: быстрый keyword match
        Optional<Domain> keywordMatch = quickMatch(message, activeDomains);
        if (keywordMatch.isPresent()) {
            log.debug("Domain routed by keyword: {}", keywordMatch.get().getSlug());
            return keywordMatch;
        }

        // Шаг 2: Claude-классификация
        return claudeClassify(userId, message, activeDomains);
    }

    /**
     * Быстрый матч по ключевым словам без вызова API.
     * Экономит токены для простых однозначных сообщений.
     */
    private Optional<Domain> quickMatch(String message, List<Domain> domains) {
        String lower = message.toLowerCase();
        for (Domain domain : domains) {
            // Проверяем slug и название домена
            if (lower.contains(domain.getSlug()) ||
                    lower.contains(domain.getName().toLowerCase())) {
                return Optional.of(domain);
            }
        }
        return Optional.empty();
    }

    /**
     * Вызов Claude для классификации неоднозначных сообщений.
     * Промпт минимальный — нужен только slug домена или "meta".
     */
    private Optional<Domain> claudeClassify(Long userId, String message, List<Domain> domains) {
        String domainList = domains.stream()
                .map(d -> d.getSlug() + " (" + d.getName() + ")")
                .collect(Collectors.joining(", "));

        String classificationPrompt = """
                Определи к какому домену относится сообщение пользователя.
                Доступные домены: %s
                Если сообщение не относится ни к одному домену — ответь "meta".
                Ответь ТОЛЬКО одним словом: slug домена или "meta". Никаких объяснений.
                
                Сообщение: "%s"
                """.formatted(domainList, message);

        try {
            String result = ChatClient.builder(chatModel)
                    .build()
                    .prompt(classificationPrompt)
                    .call()
                    .content()
                    .trim()
                    .toLowerCase()
                    .replaceAll("[^a-z]", ""); // убираем лишние символы

            log.debug("Claude classified message as domain: '{}'", result);

            if ("meta".equals(result)) return Optional.empty();

            return domainRepository.findByUserIdAndSlug(userId, result);

        } catch (Exception e) {
            log.warn("Domain classification failed, falling back to meta-coach: {}", e.getMessage());
            return Optional.empty();
        }
    }
}