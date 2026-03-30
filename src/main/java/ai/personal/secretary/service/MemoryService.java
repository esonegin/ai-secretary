package ai.personal.secretary.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Долгосрочная семантическая память коуча через pgvector.
 *
 * Каждое сообщение пользователя сохраняется как Document с метаданными:
 *   - userId, domainSlug — для фильтрации при поиске
 *   - role — USER или ASSISTANT
 *
 * При новом запросе ищем семантически похожие сообщения из прошлого
 * и вставляем их в системный промпт как "воспоминания".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryService {

    private final VectorStore vectorStore;

    /**
     * Сохраняет сообщение пользователя в векторное хранилище.
     * Асинхронно — не блокирует основной ответ коуча.
     *
     * Сохраняем только USER сообщения — они содержат фактическую информацию
     * (что делал, что ел, как себя чувствует). Ответы коуча не индексируем.
     */
    @Async
    public void remember(Long userId, String domainSlug, String content) {
        try {
            Document doc = new Document(
                    content,
                    Map.of(
                            "userId",     userId.toString(),
                            "domainSlug", domainSlug != null ? domainSlug : "meta"
                    )
            );
            vectorStore.add(List.of(doc));
            log.debug("Memory saved: userId={} domain={} len={}", userId, domainSlug, content.length());
        } catch (Exception e) {
            log.warn("Memory save failed (non-critical): {}", e.getMessage());
        }
    }

    /**
     * Ищет семантически похожие воспоминания для данного запроса.
     * Фильтрует по userId и опционально по домену.
     *
     * @param userId     ID пользователя
     * @param domainSlug slug домена (null = ищем по всем доменам)
     * @param query      текущее сообщение пользователя
     * @param topK       сколько воспоминаний вернуть
     * @return форматированная строка для вставки в системный промпт
     */
    public String recall(Long userId, String domainSlug, String query, int topK) {
        try {
            var b = new FilterExpressionBuilder();
            var filter = domainSlug != null
                    ? b.and(
                    b.eq("userId", userId.toString()),
                    b.eq("domainSlug", domainSlug)
            ).build()
                    : b.eq("userId", userId.toString()).build();

            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .filterExpression(filter)
                            .similarityThreshold(0.75)  // только релевантные
                            .build()
            );

            if (results.isEmpty()) return "";

            String memories = results.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n- "));

            log.debug("Recalled {} memories for userId={} domain={}", results.size(), userId, domainSlug);
            return "\nРелевантные воспоминания из прошлых разговоров:\n- " + memories + "\n";

        } catch (Exception e) {
            log.warn("Memory recall failed (non-critical): {}", e.getMessage());
            return "";
        }
    }
}