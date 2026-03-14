package ai.personal.secretary.controller;

import ai.personal.secretary.service.LlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiController {

    private final LlmService llmService;

    @GetMapping("/ask")
    public String ask(@RequestParam String q) {
        return llmService.ask(q);
    }
}