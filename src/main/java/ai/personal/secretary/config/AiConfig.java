package ai.personal.secretary.config;

/**
 * @author onegines
 * @date 25.03.2026
 */

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация AI.
 *
 * Важное отличие от предыдущей версии:
 * здесь нет фиксированного системного промпта.
 *
 * Каждый домен имеет свой systemPrompt в БД.
 * CoachService динамически строит ChatClient с нужным промптом
 * для каждого разговора через ChatClient.builder(model).defaultSystem(domainPrompt).
 *
 * Этот бин — базовый строитель без промпта.
 * Используется CoachService как фабрика.
 */
@Configuration
public class AiConfig {

    /**
     * Базовый ChatClient без системного промпта.
     * CoachService переопределяет промпт при каждом вызове.
     */
    @Bean
    public ChatClient chatClient(AnthropicChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

}