package ai.personal.secretary.service;

import ai.personal.secretary.model.FitnessGoal;
import ai.personal.secretary.model.TrainingBlock;
import ai.personal.secretary.model.TrainingExercise;
import ai.personal.secretary.model.TrainingProgram;
import ai.personal.secretary.model.TrainingProgramDay;
import ai.personal.secretary.model.TrainingProgramExercise;
import ai.personal.secretary.model.TrainingProgramSet;
import ai.personal.secretary.model.TrainingSession;
import ai.personal.secretary.model.TrainingSet;
import ai.personal.secretary.repository.FitnessGoalRepository;
import ai.personal.secretary.repository.TrainingBlockRepository;
import ai.personal.secretary.repository.TrainingExerciseRepository;
import ai.personal.secretary.repository.TrainingProgramDayRepository;
import ai.personal.secretary.repository.TrainingProgramExerciseRepository;
import ai.personal.secretary.repository.TrainingProgramRepository;
import ai.personal.secretary.repository.TrainingProgramSetRepository;
import ai.personal.secretary.repository.TrainingSessionRepository;
import ai.personal.secretary.repository.TrainingSetRepository;
import ai.personal.secretary.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final TrainingBlockRepository trainingBlockRepository;
    private final TrainingProgramDayRepository trainingProgramDayRepository;
    private final TrainingProgramExerciseRepository trainingProgramExerciseRepository;
    private final TrainingProgramSetRepository trainingProgramSetRepository;
    private final UserProfileRepository userProfileRepository;
    private final TrainingExerciseRepository trainingExerciseRepository;
    private final TrainingSetRepository trainingSetRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<TrainingSession> getWorkouts(Long userId) {
        return trainingSessionRepository.findByUserIdOrderByWorkoutDateDesc(userId);
    }

    public List<TrainingSession> getWorkouts(Long userId, LocalDate from, LocalDate to) {
        return trainingSessionRepository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDesc(userId, from, to);
    }

    public Optional<TrainingSession> getWorkout(Long userId, LocalDate workoutDate, String dayType) {
        return trainingSessionRepository.findByUserIdAndWorkoutDateAndDayType(userId, workoutDate, dayType);
    }

    public List<TrainingSession> getPreviousWorkouts(Long userId, String dayType, LocalDate workoutDate) {
        var exact = trainingSessionRepository.findTop3ByUserIdAndDayTypeAndWorkoutDateBeforeOrderByWorkoutDateDesc(
                userId, dayType, workoutDate);
        if (!exact.isEmpty()) return exact;

        String normalized = normalizeProgramDayType(dayType);
        if (normalized.equals(dayType)) return exact;

        return trainingSessionRepository.findTop3ByUserIdAndDayTypeAndWorkoutDateBeforeOrderByWorkoutDateDesc(
                userId, normalized, workoutDate);
    }

    public Optional<FitnessGoal> getActiveGoal(Long userId) {
        return fitnessGoalRepository.findFirstByUserIdAndStatusOrderByPriorityDescCreatedAtDesc(userId, "ACTIVE");
    }

    @Transactional
    public FitnessGoal saveGoal(Long userId, String goalText) {
        var user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return fitnessGoalRepository.save(FitnessGoal.builder()
                .user(user).goalText(goalText).status("ACTIVE").build());
    }

    public Optional<TrainingProgram> getActiveProgram(Long userId) {
        return trainingProgramRepository.findFirstByUserIdAndStatusOrderByValidFromDescCreatedAtDesc(userId, "ACTIVE");
    }

    public Optional<TrainingBlock> getActiveTrainingBlock(Long userId) {
        return trainingBlockRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(userId, "ACTIVE");
    }

    public Optional<TrainingBlock> getActiveTrainingBlockForProgram(Long programId) {
        return trainingBlockRepository.findFirstByTrainingProgramIdAndStatusOrderByStartedAtDesc(programId, "ACTIVE");
    }

    public Optional<TrainingProgramDay> getProgramDay(Long programId, String dayType) {
        return trainingProgramDayRepository.findByProgramIdAndDayType(programId, normalizeProgramDayType(dayType));
    }

    public List<TrainingProgramExercise> getProgramExercises(Long userId, String dayType) {
        return getActiveProgram(userId)
                .flatMap(program -> getProgramDay(program.getId(), dayType))
                .map(day -> trainingProgramExerciseRepository.findByProgramDayIdOrderByExerciseOrder(day.getId()))
                .orElseGet(List::of);
    }

    public List<TrainingExercise> getTrainingExercises(Long sessionId) {
        return trainingExerciseRepository.findBySessionIdOrderByExerciseOrder(sessionId);
    }

    public List<TrainingSet> getTrainingSets(Long exerciseId) {
        return trainingSetRepository.findByExerciseIdOrderBySetNumber(exerciseId);
    }

    public List<TrainingProgramSet> getProgramSets(Long programExerciseId) {
        return trainingProgramSetRepository.findByProgramExerciseIdOrderBySetNumber(programExerciseId);
    }

    public long getProgramSetCount(Long programExerciseId) {
        return trainingProgramSetRepository.countByProgramExerciseId(programExerciseId);
    }

    /**
     * Creates only the workout session. The program is a prescription and must not
     * be copied into the factual training_exercises/training_sets tables.
     */
    @Transactional
    public TrainingSession startWorkout(Long userId, LocalDate workoutDate, String dayType) {
        var existing = trainingSessionRepository.findByUserIdAndWorkoutDateAndDayType(userId, workoutDate, dayType);
        if (existing.isPresent()) return existing.get();

        var user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return trainingSessionRepository.save(TrainingSession.builder()
                .user(user).workoutDate(workoutDate).dayType(dayType).build());
    }

    @Transactional
    public TrainingSet recordSetResult(Long userId, Long trainingSetId, BigDecimal weightKg, Integer actualReps) {
        var trainingSet = trainingSetRepository.findByIdAndUserId(trainingSetId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Training set not found for user: " + trainingSetId));
        trainingSet.setWeightKg(weightKg);
        trainingSet.setActualReps(actualReps);
        return trainingSetRepository.save(trainingSet);
    }

    @Transactional
    public TrainingSession recordWorkoutResult(Long userId, LocalDate workoutDate, String dayType,
                                                BigDecimal bodyWeightKg, WorkoutResultParser.WorkoutResult result) {
        var session = trainingSessionRepository.findByUserIdAndWorkoutDateAndDayType(userId, workoutDate, dayType)
                .orElseGet(() -> startWorkout(userId, workoutDate, dayType));
        if (bodyWeightKg != null) session.setBodyWeightKg(bodyWeightKg);

        replaceRecordedExercises(session);

        for (var exerciseResult : result.exercises()) {
            if (exerciseResult.exerciseName() == null || exerciseResult.exerciseName().isBlank()) {
                throw new IllegalArgumentException("Workout exercise name must not be blank");
            }

            var trainingExercise = trainingExerciseRepository.save(TrainingExercise.builder()
                    .session(session)
                    .exerciseOrder(exerciseResult.exerciseOrder())
                    .exerciseName(exerciseResult.exerciseName())
                    .notes(exerciseResult.notes())
                    .build());

            int setNumber = 1;
            for (var setResult : exerciseResult.sets()) {
                trainingSetRepository.save(TrainingSet.builder()
                        .exercise(trainingExercise)
                        .setNumber(setNumber++)
                        .weightKg(setResult.weightKg())
                        .actualReps(setResult.actualReps())
                        .loadMode(setResult.loadMode())
                        .build());
            }
        }

        var saved = trainingSessionRepository.save(session);
        eventPublisher.publishEvent(new WorkoutRecordedEvent(
                userId, workoutDate, dayType, bodyWeightKg));
        return saved;
    }

    private void replaceRecordedExercises(TrainingSession session) {
        var existingExercises = trainingExerciseRepository.findBySessionIdOrderByExerciseOrder(session.getId());
        for (var exercise : existingExercises) {
            trainingSetRepository.deleteByExerciseId(exercise.getId());
        }
        if (!existingExercises.isEmpty()) {
            trainingExerciseRepository.deleteAll(existingExercises);
        }
    }

    private String normalizeProgramDayType(String dayType) {
        if (dayType == null || dayType.isBlank()) {
            throw new IllegalArgumentException("Training day type must not be blank");
        }
        return dayType.startsWith("DAY_") ? dayType.substring("DAY_".length()) : dayType;
    }
}
