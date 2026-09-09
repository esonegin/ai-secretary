package ai.personal.secretary.service;

import ai.personal.secretary.model.*;
import ai.personal.secretary.repository.*;
import lombok.RequiredArgsConstructor;
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
    private final TrainingProgramDayRepository trainingProgramDayRepository;
    private final TrainingProgramExerciseRepository trainingProgramExerciseRepository;
    private final TrainingProgramSetRepository trainingProgramSetRepository;
    private final UserProfileRepository userProfileRepository;
    private final TrainingExerciseRepository trainingExerciseRepository;
    private final TrainingSetRepository trainingSetRepository;

    public List<TrainingSession> getWorkouts(Long userId) {
        return trainingSessionRepository.findByUserIdOrderByWorkoutDateDesc(userId);
    }

    public List<TrainingSession> getWorkouts(
            Long userId,
            LocalDate from,
            LocalDate to) {

        return trainingSessionRepository
                .findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDesc(
                        userId, from, to);
    }

    public Optional<TrainingSession> getWorkout(
            Long userId,
            LocalDate workoutDate,
            String dayType) {

        return trainingSessionRepository
                .findByUserIdAndWorkoutDateAndDayType(
                        userId, workoutDate, dayType);
    }

    public Optional<FitnessGoal> getActiveGoal(Long userId) {
        return fitnessGoalRepository
                .findFirstByUserIdAndStatusOrderByPriorityDescCreatedAtDesc(
                        userId, "ACTIVE");
    }

    @Transactional
    public FitnessGoal saveGoal(Long userId, String goalText) {
        var user = userProfileRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found: " + userId));

        return fitnessGoalRepository.save(
                FitnessGoal.builder()
                        .user(user)
                        .goalText(goalText)
                        .status("ACTIVE")
                        .build()
        );
    }

    public Optional<TrainingProgram> getActiveProgram(Long userId) {
        return trainingProgramRepository
                .findFirstByUserIdAndStatusOrderByValidFromDescCreatedAtDesc(
                        userId, "ACTIVE");
    }

    public Optional<TrainingProgramDay> getProgramDay(
            Long programId,
            String dayType) {

        return trainingProgramDayRepository
                .findByProgramIdAndDayType(
                        programId,
                        normalizeProgramDayType(dayType));
    }

    public List<TrainingProgramExercise> getProgramExercises(
            Long userId,
            String dayType) {

        return getActiveProgram(userId)
                .flatMap(program ->
                        getProgramDay(program.getId(), dayType))
                .map(day ->
                        trainingProgramExerciseRepository
                                .findByProgramDayIdOrderByExerciseOrder(
                                        day.getId()))
                .orElseGet(List::of);
    }

    public List<TrainingExercise> getTrainingExercises(Long sessionId) {
        return trainingExerciseRepository
                .findBySessionIdOrderByExerciseOrder(sessionId);
    }

    public List<TrainingSet> getTrainingSets(Long exerciseId) {
        return trainingSetRepository
                .findByExerciseIdOrderBySetNumber(exerciseId);
    }

    public List<TrainingProgramSet> getProgramSets(Long programExerciseId) {
        return trainingProgramSetRepository
                .findByProgramExerciseIdOrderBySetNumber(programExerciseId);
    }

    public long getProgramSetCount(Long programExerciseId) {
        return trainingProgramSetRepository
                .countByProgramExerciseId(programExerciseId);
    }

    @Transactional
    public TrainingSession startWorkout(
            Long userId,
            LocalDate workoutDate,
            String dayType) {

        var existing = trainingSessionRepository
                .findByUserIdAndWorkoutDateAndDayType(
                        userId, workoutDate, dayType);

        if (existing.isPresent()) {
            return existing.get();
        }

        var user = userProfileRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found: " + userId));

        var program = getActiveProgram(userId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Active training program not found"));

        var day = getProgramDay(program.getId(), dayType)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Training day not found: " + dayType));

        var exercises = trainingProgramExerciseRepository
                .findByProgramDayIdOrderByExerciseOrder(day.getId());

        if (exercises.isEmpty()) {
            throw new IllegalStateException(
                    "Training program day has no exercises");
        }

        var session = trainingSessionRepository.save(
                TrainingSession.builder()
                        .user(user)
                        .workoutDate(workoutDate)
                        .dayType(dayType)
                        .build()
        );

        for (var programExercise : exercises) {
            var trainingExercise = trainingExerciseRepository.save(
                    TrainingExercise.builder()
                            .session(session)
                            .exerciseOrder(
                                    programExercise.getExerciseOrder())
                            .exerciseName(
                                    programExercise.getExerciseName())
                            .exerciseVariant(
                                    programExercise.getExerciseVariant())
                            .build()
            );

            long setCount = trainingProgramSetRepository
                    .countByProgramExerciseId(programExercise.getId());

            for (int setNumber = 1;
                 setNumber <= setCount;
                 setNumber++) {

                trainingSetRepository.save(
                        TrainingSet.builder()
                                .exercise(trainingExercise)
                                .setNumber(setNumber)
                                .loadMode("TOTAL")
                                .build()
                );
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
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Training set not found for user: "
                                        + trainingSetId));

        trainingSet.setWeightKg(weightKg);
        trainingSet.setActualReps(actualReps);

        return trainingSetRepository.save(trainingSet);
    }

    @Transactional
    public TrainingSession recordWorkoutResult(
            Long userId,
            LocalDate workoutDate,
            String dayType,
            BigDecimal bodyWeightKg,
            WorkoutResultParser.WorkoutResult result) {

        var session = trainingSessionRepository
                .findByUserIdAndWorkoutDateAndDayType(
                        userId, workoutDate, dayType)
                .orElseGet(() ->
                        startWorkout(userId, workoutDate, dayType));

        if (bodyWeightKg != null) {
            session.setBodyWeightKg(bodyWeightKg);
        }

        var trainingExercises = trainingExerciseRepository
                .findBySessionIdOrderByExerciseOrder(
                        session.getId());

        var resultByOrder = result.exercises().stream()
                .collect(java.util.stream.Collectors.toMap(
                        WorkoutResultParser.ExerciseResult::exerciseOrder,
                        java.util.function.Function.identity()));

        for (var trainingExercise : trainingExercises) {
            var exerciseResult = resultByOrder.get(
                    trainingExercise.getExerciseOrder());

            if (exerciseResult == null) {
                continue;
            }

            var trainingSets = trainingSetRepository
                    .findByExerciseIdOrderBySetNumber(
                            trainingExercise.getId());

            boolean mobility = exerciseResult.sets().stream()
                    .allMatch(set -> "MOBILITY".equals(set.loadMode()));

            if (mobility) {
                if (exerciseResult.sets().size() != 1) {
                    throw new IllegalArgumentException(
                            "Mobility exercise must have exactly one result: "
                                    + trainingExercise.getExerciseOrder());
                }

                var trainingSet = trainingSets.get(0);
                var setResult = exerciseResult.sets().get(0);

                trainingSet.setWeightKg(null);
                trainingSet.setActualReps(null);
                trainingSet.setLoadMode("MOBILITY");

                continue;
            }

            if (trainingSets.size() != exerciseResult.sets().size()) {
                throw new IllegalArgumentException(
                        "Set count mismatch for exercise "
                                + trainingExercise.getExerciseOrder()
                                + ": expected "
                                + trainingSets.size()
                                + ", received "
                                + exerciseResult.sets().size());
            }

            for (int i = 0; i < exerciseResult.sets().size(); i++) {
                var trainingSet = trainingSets.get(i);
                var setResult = exerciseResult.sets().get(i);

                trainingSet.setWeightKg(setResult.weightKg());
                trainingSet.setActualReps(setResult.actualReps());
                trainingSet.setLoadMode(setResult.loadMode());
            }
        }

        return trainingSessionRepository.save(session);
    }

    private String normalizeProgramDayType(String dayType) {
        if (dayType == null || dayType.isBlank()) {
            throw new IllegalArgumentException(
                    "Training day type must not be blank");
        }

        if (dayType.startsWith("DAY_")) {
            return dayType.substring("DAY_".length());
        }

        return dayType;
    }
}