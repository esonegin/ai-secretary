package ai.personal.secretary.controller;

/**
 * @author onegines
 * @date 16.03.2026
 */

import ai.personal.secretary.dto.AddKnowledgeRequest;
import ai.personal.secretary.model.Knowledge;
import ai.personal.secretary.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @PostMapping
    public Knowledge add(@RequestBody AddKnowledgeRequest request) {
        return knowledgeService.add(request.getContent());
    }

    @GetMapping
    public List<Knowledge> list() {
        return knowledgeService.findAll();
    }

}