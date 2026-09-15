package ai.personal.secretary.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorkoutResultParser {

    private static final Pattern NUMBERED_LINE_PATTERN = Pattern.compile(
            "^\\s*(\\d+)\\.\\s*(.+?)\\s*$");

    private static final Pattern DATE_HEADER_PATTERN = Pattern.compile(
            "^\\s*\\d{2}\\.\\d{2}\\.\\d{4}\\b.*$");

    private static final Pattern WEIGHT_AND_REPS_PATTERN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*кг\\s*[×xх*]\\s*(\\d+)(?:\\s*[×xх*]\\s*(\\d+))?");

    public WorkoutResult parse(String text) {
        var exercises = new ArrayList<ExerciseResult>();
        ExerciseResultBuilder current = null;

        for (String line : text.split("\\R")) {
            if (DATE_HEADER_PATTERN.matcher(line).matches()) {
                continue;
            }

            Matcher numbered = NUMBERED_LINE_PATTERN.matcher(line);
            if (!numbered.matches()) {
                continue;
            }

            String payload = numbered.group(2).trim();
            int order = Integer.parseInt(numbered.group(1));

            int colonIndex = payload.indexOf(':');
            if (colonIndex >= 0) {
                if (current != null) {
                    exercises.add(current.toResult());
                }
                String name = payload.substring(0, colonIndex).trim();
                String setsText = payload.substring(colonIndex + 1).trim();
                current = new ExerciseResultBuilder(order, name);
                current.sets.addAll(parseSets(setsText));
                continue;
            }

            Matcher setMatcher = WEIGHT_AND_REPS_PATTERN.matcher(payload);
            if (setMatcher.find()) {
                if (setMatcher.start() == 0) {
                    if (current != null) {
                        current.sets.addAll(parseWeightedSets(payload));
                    }
                    continue;
                }

                if (current != null) {
                    exercises.add(current.toResult());
                }
                String name = payload.substring(0, setMatcher.start())
                        .replaceFirst("\\s*[—–-]\\s*$", "")
                        .trim();
                current = new ExerciseResultBuilder(order, name);
                current.sets.addAll(parseWeightedSets(payload.substring(setMatcher.start())));
                continue;
            }

            if (isMobility(payload)) {
                if (current != null) {
                    current.sets.add(new SetResult(null, null, "MOBILITY"));
                }
                continue;
            }

            if (current != null) {
                exercises.add(current.toResult());
            }
            current = new ExerciseResultBuilder(order, payload);
        }

        if (current != null) {
            exercises.add(current.toResult());
        }

        return new WorkoutResult(exercises);
    }

    private boolean isMobility(String text) {
        return "mobility".equalsIgnoreCase(text.trim())
                || "stretching".equalsIgnoreCase(text.trim());
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
