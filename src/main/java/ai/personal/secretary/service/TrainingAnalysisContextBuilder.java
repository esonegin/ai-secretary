package ai.personal.secretary.service;

import ai.personal.secretary.model.TrainingProgramExercise;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingAnalysisContextBuilder {

    private final FitnessDataService fitnessDataService;

    public TrainingAnalysisContext build(Long userId, LocalDate workoutDate, String dayType) {
        var session = fitnessDataService.getWorkout(userId, workoutDate, dayType)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Training session not found: " + workoutDate + " " + dayType));

        var goal = fitnessDataService.getActiveGoal(userId)
                .map(g -> g.getGoalText())
                .orElse(null);

        var program = fitnessDataService.getActiveProgram(userId).orElse(null);

        var programContext = program == null ? null : new TrainingAnalysisContext.ProgramContext(
                program.getName(), program.getVersion());

        var trainingBlock = program == null
                ? null
                : fitnessDataService.getActiveTrainingBlockForProgram(program.getId())
                .map(block -> new TrainingAnalysisContext.TrainingBlockContext(
                        block.getName(),
                        block.getGoal(),
                        block.getPhase(),
                        block.getStatus(),
                        block.getStartedAt(),
                        block.getPlannedWeeks(),
                        block.getDeloadWeek(),
                        calculateCurrentWeek(block.getStartedAt(), workoutDate)))
                .orElse(null);

        var programExercises = fitnessDataService.getProgramExercises(userId, dayType);
        var actualExercises = fitnessDataService.getTrainingExercises(session.getId());

        var exercises = actualExercises.stream()
                .map(actualExercise -> {
                    TrainingProgramExercise plannedExercise = programExercises.stream()
                            .filter(e -> e.getExerciseOrder().equals(actualExercise.getExerciseOrder()))
                            .findFirst()
                            .orElse(null);

                    var plannedSets = plannedExercise == null
                            ? List.<TrainingAnalysisContext.PlannedSetContext>of()
                            : fitnessDataService.getProgramSets(plannedExercise.getId()).stream()
                            .map(set -> new TrainingAnalysisContext.PlannedSetContext(
                                    set.getSetNumber(), set.getWeightKg(), set.getPlannedRepsMin(),
                                    set.getPlannedRepsMax(), set.getLoadMode()))
                            .toList();

                    var actualSets = fitnessDataService.getTrainingSets(actualExercise.getId()).stream()
                            .map(set -> new TrainingAnalysisContext.ActualSetContext(
                                    set.getSetNumber(), set.getWeightKg(), set.getActualReps(), set.getLoadMode()))
                            .toList();

                    return new TrainingAnalysisContext.ExerciseContext(
                            actualExercise.getExerciseOrder(), actualExercise.getExerciseName(),
                            actualExercise.getExerciseVariant(), plannedSets, actualSets);
                })
                .toList();

        var history = fitnessDataService.getPreviousWorkouts(userId, dayType, workoutDate).stream()
                .map(previousSession -> {
                    var historicalExercises = fitnessDataService.getTrainingExercises(previousSession.getId()).stream()
                            .map(exercise -> {
                                var actualSets = fitnessDataService.getTrainingSets(exercise.getId()).stream()
                                        .map(set -> new TrainingAnalysisContext.ActualSetContext(
                                                set.getSetNumber(), set.getWeightKg(), set.getActualReps(), set.getLoadMode()))
                                        .toList();

                                return new TrainingAnalysisContext.HistoricalExerciseContext(
                                        exercise.getExerciseOrder(), exercise.getExerciseName(), actualSets);
                            })
                            .toList();

                    return new TrainingAnalysisContext.HistoricalWorkoutContext(
                            previousSession.getWorkoutDate(), previousSession.getDayType(),
                            previousSession.getBodyWeightKg(), historicalExercises);
                })
                .toList();

        return new TrainingAnalysisContext(
                session.getWorkoutDate(), session.getDayType(), session.getBodyWeightKg(),
                goal, programContext, trainingBlock, exercises, history);
    }

    private Integer calculateCurrentWeek(LocalDate startedAt, LocalDate workoutDate) {
        if (startedAt == null || workoutDate.isBefore(startedAt)) {
            return null;
        }
        return Math.toIntExact(ChronoUnit.WEEKS.between(startedAt, workoutDate) + 1);
    }
}
