package ai.personal.secretary.repository;

import ai.personal.secretary.model.Knowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeRepository extends JpaRepository<Knowledge, Long> {

    @Query(value = """
            SELECT *
            FROM knowledge
            ORDER BY embedding <-> CAST(:embedding AS vector)
            LIMIT 5
            """, nativeQuery = true)
    List<Knowledge> search(float[] embedding);


    @Query(value = """
SELECT *
FROM knowledge
ORDER BY embedding <-> :embedding
LIMIT 5
""", nativeQuery = true)
    List<Knowledge> findRelevant(@Param("embedding") float[] embedding);


}