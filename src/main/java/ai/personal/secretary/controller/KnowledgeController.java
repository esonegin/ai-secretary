package ai.personal.secretary.controller;

import ai.personal.secretary.dto.KnowledgeRequest;
import ai.personal.secretary.dto.KnowledgeResponse;
import ai.personal.secretary.service.KnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @PostMapping
    public KnowledgeResponse addKnowledge(@RequestBody @Valid KnowledgeRequest request) {
        return knowledgeService.save(request.content());
    }

    @GetMapping
    public List<KnowledgeResponse> getAllKnowledge() {
        return knowledgeService.findAll();
    }
}