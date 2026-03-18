package ai.personal.secretary.service;

/**
 * @author onegines
 * @date 16.03.2026
 */

import ai.personal.secretary.model.Knowledge;
import ai.personal.secretary.repository.KnowledgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeRepository knowledgeRepository;
    private final EmbeddingService embeddingService;

    public Knowledge add(String content) {

        Knowledge k = new Knowledge();

        k.setContent(content);
        k.setEmbedding(embeddingService.embed(content));

        return knowledgeRepository.save(k);
    }

    public List<Knowledge> findAll() {
        return knowledgeRepository.findAll();
    }

}