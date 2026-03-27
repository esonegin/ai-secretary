package ai.personal.secretary.service;

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
 * Определяет к какому домену относится сообщение.
 * Сначала быстрый keyword match, потом Claude-классификация.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DomainRouterService {

    private final AnthropicChatModel chatModel;
    private final DomainRepository domainRepository;

    public Optional<Domain> route(Long userId, String message) {
        List<Domain> domains = domainRepository.findByUserIdAndIsActiveTrueOrderBySortOrderAsc(userId);
        if (domains.isEmpty()) return Optional.empty();

        // Шаг 1: keyword match — без вызова API
        for (Domain d : domains) {
            String lower = message.toLowerCase();
            if (lower.contains(d.getSlug()) || lower.contains(d.getName().toLowerCase())) {
                log.debug("Keyword match → {}", d.getSlug());
                return Optional.of(d);
            }
        }

        // Шаг 2: Claude-классификация
        return claudeClassify(userId, message, domains);
    }

    private Optional<Domain> claudeClassify(Long userId, String message, List<Domain> domains) {
        String list = domains.stream()
                .map(d -> d.getSlug() + "=" + d.getName())
                .collect(Collectors.joining(", "));

        String prompt = String.format(
            "Домены: %s\nСообщение: \"%s\"\n" +
            "Ответь ТОЛЬКО slug домена или \"meta\". Никаких объяснений.", list, message);

        try {
            String result = ChatClient.builder(chatModel).build()
                    .prompt(prompt).call().content()
                    .trim().toLowerCase().replaceAll("[^a-z_]", "");

            log.debug("Claude routed → '{}'", result);
            if ("meta".equals(result)) return Optional.empty();
            return domainRepository.findByUserIdAndSlug(userId, result);

        } catch (Exception e) {
            log.warn("Router fallback to meta: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
