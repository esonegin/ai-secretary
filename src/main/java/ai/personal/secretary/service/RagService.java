package ai.personal.secretary.service;

import ai.personal.secretary.model.Knowledge;
import ai.personal.secretary.repository.KnowledgeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private final KnowledgeRepository knowledgeRepository;
    private final EmbeddingService embeddingService;

    public RagService(KnowledgeRepository knowledgeRepository,
                      EmbeddingService embeddingService) {

        this.knowledgeRepository = knowledgeRepository;
        this.embeddingService = embeddingService;
    }

    public String search(String question) {

        float[] embedding = embeddingService.embed(question);

        List<Knowledge> knowledgeList =
                knowledgeRepository.findRelevant(embedding);

        StringBuilder context = new StringBuilder();

        for (Knowledge k : knowledgeList) {
            context.append(k.getContent()).append("\n");
        }

        return context.toString();
    }

    public String buildContext(String question) {

        List<Knowledge> knowledgeList = knowledgeRepository.findAll();

        StringBuilder context = new StringBuilder();

        for (Knowledge k : knowledgeList) {
            context.append(k.getContent()).append("\n");
        }

        return context.toString();
    }
}