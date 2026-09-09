package ai.personal.secretary.repository;

import ai.personal.secretary.model.TrainingSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrainingSetRepository extends JpaRepository<TrainingSet, Long> {
    @Query("""
        select ts
        from TrainingSet ts
        join ts.exercise te
        join te.session s
        where ts.id = :setId
          and s.user.id = :userId
        """)
    Optional<TrainingSet> findByIdAndUserId(Long setId, Long userId);

}


