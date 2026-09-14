package ai.personal.secretary.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class TrainingStateCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TREND_THRESHOLD_PCT = BigDecimal.valueOf(5);

    public TrainingState calculate(TrainingAnalysisContext context) {
        var currentWorkout = context.exercises();
        var previousWorkout = context.history().isEmpty()
                ? List.<TrainingAnalysisContext.HistoricalExerciseContext>of()
                : context.history().getFirst().exercises();

        BigDecimal currentVolume = calculateVolume(currentWorkout.stream()
                .map(exercise -> exercise.actualSets())
                .toList());
        BigDecimal previousVolume = calculateVolume(previousWorkout.stream()
                .map(exercise -> exercise.actualSets())
                .toList());

        var previousByOrder = previousWorkout.stream()
                .collect(java.util.stream.Collectors.toMap(
                        TrainingAnalysisContext.HistoricalExerciseContext::order,
                        exercise -> exercise,
                        (first, ignored) -> first));

        var exercises = currentWorkout.stream()
                .map(current -> {
                    var previous = previousByOrder.get(current.order());
                    BigDecimal currentExerciseVolume = calculateVolume(List.of(current.actualSets()));
                    BigDecimal previousExerciseVolume = previous == null
                            ? null
                            : calculateVolume(List.of(previous.actualSets()));

                    return new TrainingState.ExerciseState(
                            current.order(),
                            current.name(),
                            currentExerciseVolume,
                            previousExerciseVolume,
                            calculateChangePct(currentExerciseVolume, previousExerciseVolume),
                            calculateTrend(currentExerciseVolume, previousExerciseVolume));
                })
                .toList();

        var trainingBlock = context.trainingBlock();
        var currentWeight = context.bodyWeightKg();
        var previousWeight = context.history().stream()
                .map(TrainingAnalysisContext.HistoricalWorkoutContext::bodyWeightKg)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        return new TrainingState(
                context.workoutDate(),
                trainingBlock == null ? null : trainingBlock.currentWeek(),
                trainingBlock == null ? null : trainingBlock.plannedWeeks(),
                trainingBlock == null ? null : trainingBlock.deloadWeek(),
                trainingBlock == null ? null : trainingBlock.goal(),
                trainingBlock == null ? null : trainingBlock.phase(),
                context.history().size(),
                currentWeight,
                previousWeight,
                calculateDifference(currentWeight, previousWeight),
                currentVolume,
                previousVolume,
                calculateChangePct(currentVolume, previousVolume),
                exercises);
    }

    private BigDecimal calculateVolume(
            List<List<TrainingAnalysisContext.ActualSetContext>> setsByExercise) {
        return setsByExercise.stream()
                .flatMap(List::stream)
                .filter(set -> set.weightKg() != null && set.actualReps() != null)
                .filter(set -> !"MOBILITY".equalsIgnoreCase(set.loadMode()))
                .map(set -> set.weightKg().multiply(BigDecimal.valueOf(set.actualReps())))
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal calculateDifference(BigDecimal current, BigDecimal previous) {
        return current == null || previous == null
                ? null
                : current.subtract(previous).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateChangePct(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(HUNDRED)
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private String calculateTrend(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) {
            return "INSUFFICIENT_DATA";
        }

        BigDecimal changePct = calculateChangePct(current, previous);
        if (changePct.compareTo(TREND_THRESHOLD_PCT) > 0) {
            return "PROGRESS";
        }
        if (changePct.compareTo(TREND_THRESHOLD_PCT.negate()) < 0) {
            return "REGRESSION";
        }
        return "STABLE";
    }
}
