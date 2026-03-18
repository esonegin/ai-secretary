package ai.personal.secretary.service;

import ai.personal.secretary.client.OpenRouterClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private final OpenRouterClient openRouterClient;

    public EmbeddingService(OpenRouterClient openRouterClient) {
        this.openRouterClient = openRouterClient;
    }

    public float[] embed(String text) {

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", text)
        );

        String response = openRouterClient.chat(messages);

        // временно возвращаем dummy embedding
        // позже подключим настоящую embedding модель
        float[] vector = new float[1536];

        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) Math.random();
        }

        return vector;
    }
}