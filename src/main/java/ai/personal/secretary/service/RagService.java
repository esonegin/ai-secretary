package ai.personal.secretary.service;

import ai.personal.secretary.model.Knowledge;
import ai.personal.secretary.repository.KnowledgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagService {

    private final KnowledgeRepository knowledgeRepository;

    public String buildContext(String userMessage) {

        List<Knowledge> all = knowledgeRepository.findAll();

        if (all.isEmpty()) {
            return "";
        }

        Set<String> queryWords = Arrays.stream(userMessage.toLowerCase().split("\\W+"))
                .filter(w -> w.length() > 2)
                .collect(Collectors.toSet());

        List<Knowledge> sorted = all.stream()
                .sorted((k1, k2) -> score(k2, queryWords) - score(k1, queryWords))
                .limit(5)
                .toList();

        return sorted.stream()
                .map(Knowledge::getContent)
                .collect(Collectors.joining("\n"));
    }

    private int score(Knowledge k, Set<String> queryWords) {
        String content = k.getContent().toLowerCase();

        int score = 0;
        for (String word : queryWords) {
            if (content.contains(word)) {
                score++;
            }
        }
        return score;
    }
}