package ai.personal.secretary.repository;

import ai.personal.secretary.model.TrainingSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainingSetRepository extends JpaRepository<TrainingSet, Long> {
}
