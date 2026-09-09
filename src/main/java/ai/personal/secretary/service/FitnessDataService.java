package ai.personal.secretary.service;

import ai.personal.secretary.model.FitnessGoal;
import ai.personal.secretary.model.TrainingProgram;
import ai.personal.secretary.model.TrainingProgramDay;
import ai.personal.secretary.model.TrainingProgramExercise;
import ai.personal.secretary.model.TrainingSession;
import ai.personal.secretary.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.personal.secretary.model.TrainingExercise;
import ai.personal.secretary.model.TrainingSet;
import ai.personal.secretary.repository.TrainingExerciseRepository;
import ai.personal.secretary.repository.TrainingSetRepository;

import java.math.BigDecimal;
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
    private final TrainingProgramSetRepository trainingProgramSetRepository;
    private final UserProfileRepository userProfileRepository;
    private final TrainingExerciseRepository trainingExerciseRepository;
    private final TrainingSetRepository trainingSetRepository;

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

    public long getProgramSetCount(Long programExerciseId) {
        return trainingProgramSetRepository.countByProgramExerciseId(programExerciseId);
    }

    @Transactional
    public TrainingSession startWorkout(Long userId, LocalDate workoutDate, String dayType) {
        var existing = trainingSessionRepository
                .findByUserIdAndWorkoutDateAndDayType(userId, workoutDate, dayType);

        if (existing.isPresent()) {
            return existing.get();
        }
        var user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        var program = getActiveProgram(userId)
                .orElseThrow(() -> new IllegalStateException("Active training program not found"));

        var day = getProgramDay(program.getId(), dayType)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Training day not found: " + dayType));

        var exercises = trainingProgramExerciseRepository
                .findByProgramDayIdOrderByExerciseOrder(day.getId());

        if (exercises.isEmpty()) {
            throw new IllegalStateException("Training program day has no exercises");
        }

        var session = trainingSessionRepository.save(TrainingSession.builder()
                .user(user)
                .workoutDate(workoutDate)
                .dayType(dayType)
                .build());

        for (var programExercise : exercises) {
            var trainingExercise = trainingExerciseRepository.save(
                    TrainingExercise.builder()
                            .session(session)
                            .exerciseOrder(programExercise.getExerciseOrder())
                            .exerciseName(programExercise.getExerciseName())
                            .exerciseVariant(programExercise.getExerciseVariant())
                            .build());

            long setCount = trainingProgramSetRepository
                    .countByProgramExerciseId(programExercise.getId());

            for (int setNumber = 1; setNumber <= setCount; setNumber++) {
                trainingSetRepository.save(TrainingSet.builder()
                        .exercise(trainingExercise)
                        .setNumber(setNumber)
                        .loadMode("TOTAL")
                        .build());
            }
        }

        return session;
    }

    @Transactional
    public TrainingSet recordSetResult(
            Long userId,
            Long trainingSetId,
            BigDecimal weightKg,
            Integer actualReps) {

        var trainingSet = trainingSetRepository
                .findByIdAndUserId(trainingSetId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Training set not found for user: " + trainingSetId));

        trainingSet.setWeightKg(weightKg);
        trainingSet.setActualReps(actualReps);

        return trainingSetRepository.save(trainingSet);
    }
}
