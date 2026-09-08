package ai.personal.secretary.service;

import ai.personal.secretary.model.FitnessGoal;
import ai.personal.secretary.model.TrainingProgram;
import ai.personal.secretary.model.TrainingProgramDay;
import ai.personal.secretary.model.TrainingProgramExercise;
import ai.personal.secretary.model.TrainingSession;
import ai.personal.secretary.repository.FitnessGoalRepository;
import ai.personal.secretary.repository.TrainingProgramDayRepository;
import ai.personal.secretary.repository.TrainingProgramExerciseRepository;
import ai.personal.secretary.repository.TrainingProgramRepository;
import ai.personal.secretary.repository.TrainingSessionRepository;
import ai.personal.secretary.repository.UserProfileRepository;
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
    private final FitnessGoalRepository fitnessGoalRepository;
    private final TrainingProgramRepository trainingProgramRepository;
    private final TrainingProgramDayRepository trainingProgramDayRepository;
    private final TrainingProgramExerciseRepository trainingProgramExerciseRepository;
    private final UserProfileRepository userProfileRepository;

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

    public Optional<FitnessGoal> getActiveGoal(Long userId) {
        return fitnessGoalRepository.findFirstByUserIdAndStatusOrderByPriorityDescCreatedAtDesc(
                userId, "ACTIVE");
    }

    @Transactional
    public FitnessGoal saveGoal(Long userId, String goalText) {
        var user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return fitnessGoalRepository.save(FitnessGoal.builder()
                .user(user)
                .goalText(goalText)
                .status("ACTIVE")
                .build());
    }

    public Optional<TrainingProgram> getActiveProgram(Long userId) {
        return trainingProgramRepository.findFirstByUserIdAndStatusOrderByValidFromDescCreatedAtDesc(
                userId, "ACTIVE");
    }

    public Optional<TrainingProgramDay> getProgramDay(Long programId, String dayType) {
        return trainingProgramDayRepository.findByProgramIdAndDayType(programId, dayType);
    }

    public List<TrainingProgramExercise> getProgramExercises(Long userId, String dayType) {
        return getActiveProgram(userId)
                .flatMap(program -> getProgramDay(program.getId(), dayType))
                .map(day -> trainingProgramExerciseRepository
                        .findByProgramDayIdOrderByExerciseOrder(day.getId()))
                .orElseGet(List::of);
    }
}
