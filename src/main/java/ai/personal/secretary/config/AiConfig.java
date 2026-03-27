package ai.personal.secretary.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * Базовый ChatClient без системного промпта.
     * CoachService строит отдельный клиент для каждого домена:
     *   ChatClient.builder(chatModel).defaultSystem(domain.getSystemPrompt()).build()
     */
    @Bean
    public ChatClient chatClient(AnthropicChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
