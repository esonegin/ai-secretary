package ai.personal.secretary.service;

import ai.personal.secretary.client.OpenRouterClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmService {

    private final OpenRouterClient openRouterClient;

    public String ask(String message) {
        return openRouterClient.chat(message);
    }
}