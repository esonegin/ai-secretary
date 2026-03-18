package ai.personal.secretary.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class OpenRouterClient {

    @Value("${OPENROUTER_API_KEY}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public String chat(List<Map<String, String>> messages) {

        String url = "https://openrouter.ai/api/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("HTTP-Referer", "http://localhost:8080");
        headers.set("X-Title", "ai-secretary");

        Map<String, Object> body = Map.of(
                "model", "openai/gpt-4o-mini",
                "messages", messages
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        Map response = restTemplate.postForObject(url, entity, Map.class);

        List choices = (List) response.get("choices");
        Map choice = (Map) choices.get(0);
        Map msg = (Map) choice.get("message");

        return msg.get("content").toString();
    }

    public float[] embedding(String text) {

        String url = "https://openrouter.ai/api/v1/embeddings";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", "text-embedding-3-small",
                "input", text
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        Map response = restTemplate.postForObject(url, entity, Map.class);

        List data = (List) response.get("data");

        Map embedding = (Map) data.get(0);

        List<Double> vector = (List<Double>) embedding.get("embedding");

        float[] result = new float[vector.size()];

        for (int i = 0; i < vector.size(); i++) {
            result[i] = vector.get(i).floatValue();
        }

        return result;
    }
}