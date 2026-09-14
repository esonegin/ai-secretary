package ai.personal.secretary.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorkoutResultParser {

    private static final Pattern NUMBERED_LINE_PATTERN = Pattern.compile(
            "^\\s*(\\d+)\\.\\s*(.+?)\\s*$");

    private static final Pattern WEIGHT_AND_REPS_PATTERN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*кг\\s*[×xх*]\\s*(\\d+)(?:\\s*[×xх*]\\s*(\\d+))?");

    public WorkoutResult parse(String text) {
        var exercises = new ArrayList<ExerciseResult>();
        ExerciseResultBuilder currentExercise = null;

        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            Matcher numbered = NUMBERED_LINE_PATTERN.matcher(line);
            if (!numbered.matches()) {
                continue;
            }

            String payload = numbered.group(2).trim();

            if (containsWorkoutSet(payload)) {
                if (currentExercise != null) {
                    currentExercise.sets.addAll(parseSets(payload));
                }
                continue;
            }

            if (isMobility(payload)) {
                if (currentExercise != null) {
                    currentExercise.sets.add(new SetResult(null, null, "MOBILITY"));
                }
                continue;
            }

            int order = Integer.parseInt(numbered.group(1));
            String exerciseName = payload;

            int colonIndex = payload.indexOf(':');
            if (colonIndex >= 0) {
                exerciseName = payload.substring(0, colonIndex).trim();
                String setsText = payload.substring(colonIndex + 1).trim();
                currentExercise = new ExerciseResultBuilder(order, exerciseName);
                currentExercise.sets.addAll(parseSets(setsText));
                exercises.add(currentExercise.toResult());
                currentExercise = new ExerciseResultBuilder(order, exerciseName);
                currentExercise.sets.addAll(exercises.remove(exercises.size() - 1).sets());
                continue;
            }

            if (currentExercise != null) {
                exercises.add(currentExercise.toResult());
            }
            currentExercise = new ExerciseResultBuilder(order, exerciseName);
        }

        if (currentExercise != null) {
            exercises.add(currentExercise.toResult());
        }

        return new WorkoutResult(exercises);
    }

    private boolean containsWorkoutSet(String text) {
        return WEIGHT_AND_REPS_PATTERN.matcher(text).find();
    }

    private boolean isMobility(String text) {
        String normalized = text.trim();
        return "mobility".equalsIgnoreCase(normalized)
                || "stretching".equalsIgnoreCase(normalized);
    }

    private List<SetResult> parseSets(String setsText) {
        if (setsText == null || setsText.isBlank()) {
            return List.of();
        }

        if (isMobility(setsText)) {
            return List.of(new SetResult(null, null, "MOBILITY"));
        }

        return parseWeightedSets(setsText);
    }

    private List<SetResult> parseWeightedSets(String setsText) {
        var result = new ArrayList<SetResult>();
        Matcher matcher = WEIGHT_AND_REPS_PATTERN.matcher(setsText);

        while (matcher.find()) {
            BigDecimal weight = new BigDecimal(matcher.group(1).replace(',', '.'));
            int reps = Integer.parseInt(matcher.group(2));
            int setCount = matcher.group(3) == null ? 1 : Integer.parseInt(matcher.group(3));

            for (int i = 0; i < setCount; i++) {
                result.add(new SetResult(weight, reps, "TOTAL"));
            }
        }

        return result;
    }

    private static final class ExerciseResultBuilder {
        private final int order;
        private final String name;
        private final List<SetResult> sets = new ArrayList<>();

        private ExerciseResultBuilder(int order, String name) {
            this.order = order;
            this.name = name;
        }

        private ExerciseResult toResult() {
            return new ExerciseResult(order, name, List.copyOf(sets));
        }
    }

    public record WorkoutResult(List<ExerciseResult> exercises) {}

    public record ExerciseResult(
            int exerciseOrder,
            String exerciseName,
            List<SetResult> sets) {}

    public record SetResult(
            BigDecimal weightKg,
            Integer actualReps,
            String loadMode) {}
}
