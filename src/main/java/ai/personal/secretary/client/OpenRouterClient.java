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

    public String chat(String message) {

        String url = "https://openrouter.ai/api/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("HTTP-Referer", "http://localhost:8080");
        headers.set("X-Title", "ai-secretary");

        Map<String, Object> body = Map.of(
                "model", "openai/gpt-4o-mini",
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", message
                        )
                )
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        Map response = restTemplate.postForObject(url, entity, Map.class);

        List choices = (List) response.get("choices");
        Map choice = (Map) choices.get(0);
        Map msg = (Map) choice.get("message");

        return msg.get("content").toString();
    }
}