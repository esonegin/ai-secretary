package ai.personal.secretary.service;

import ai.personal.secretary.dto.KnowledgeResponse;
import ai.personal.secretary.model.Knowledge;
import ai.personal.secretary.repository.KnowledgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeRepository knowledgeRepository;

    public KnowledgeResponse save(String content) {
        Knowledge knowledge = new Knowledge();
        knowledge.setContent(content);
        knowledge.setEmbedding(null); // пока отключено

        Knowledge saved = knowledgeRepository.save(knowledge);

        return new KnowledgeResponse(
                saved.getId(),
                saved.getContent()
        );
    }

    public List<KnowledgeResponse> findAll() {
        return knowledgeRepository.findAll()
                .stream()
                .map(k -> new KnowledgeResponse(
                        k.getId(),
                        k.getContent()
                ))
                .toList();
    }
}