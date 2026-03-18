package ai.personal.secretary.controller;

import ai.personal.secretary.service.LlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiController {

    private final LlmService llmService;

    @GetMapping("/chat")
    public String chat(
            @RequestParam(required = false) Long conversationId,
            @RequestParam String message
    ) {
        return llmService.ask(conversationId, message);
    }
}