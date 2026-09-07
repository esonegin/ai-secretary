package ai.personal.secretary.service;

import ai.personal.secretary.model.TrainingSession;
import ai.personal.secretary.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FitnessDataService {

    private final TrainingSessionRepository trainingSessionRepository;

    public List<TrainingSession> getWorkouts(Long userId) {
        return trainingSessionRepository.findByUserIdOrderByWorkoutDateDesc(userId);
    }

    public List<TrainingSession> getWorkouts(Long userId, LocalDate from, LocalDate to) {
        return trainingSessionRepository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDesc(
                userId, from, to);
    }

    public Optional<TrainingSession> getWorkout(Long userId, LocalDate workoutDate, String dayType) {
        return trainingSessionRepository.findByUserIdAndWorkoutDateAndDayType(
                userId, workoutDate, dayType);
    }
}
