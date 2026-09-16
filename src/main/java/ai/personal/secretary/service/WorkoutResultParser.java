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

    private static final Pattern BODY_WEIGHT_PATTERN = Pattern.compile(
            "^\\s*(?:собственный\\s+вес|вес\\s+тела|bodyweight)\\s*[:=-]?\\s*(\\d+(?:[.,]\\d+)?)\\s*кг?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern WEIGHT_AND_REPS_PATTERN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*кг(?:\\s+([^×xх*]+?))?\\s*[×xх*]\\s*(\\d+)(?:\\s*[×xх*]\\s*(\\d+))?");

    private static final Pattern COMMENT_PATTERN = Pattern.compile("^(.+?)\\s*\\(([^()]*)\\)\\s*$");

    public WorkoutResult parse(String text) {
        var exercises = new ArrayList<ExerciseResult>();
        BigDecimal bodyWeightKg = null;
        ExerciseResultBuilder current = null;

        for (String line : text.split("\\R")) {
            if (DATE_HEADER_PATTERN.matcher(line).matches()) {
                continue;
            }

            Matcher bodyWeightMatcher = BODY_WEIGHT_PATTERN.matcher(line);
            if (bodyWeightMatcher.matches()) {
                bodyWeightKg = new BigDecimal(bodyWeightMatcher.group(1).replace(',', '.'));
                continue;
            }

            Matcher numbered = NUMBERED_LINE_PATTERN.matcher(line);
            if (!numbered.matches()) {
                continue;
            }

            String payload = numbered.group(2).trim();
            int order = Integer.parseInt(numbered.group(1));

            if (current != null && isIndentedSetLine(line)) {
                if (isMobility(payload)) {
                    current.sets.add(new SetResult(null, null, "MOBILITY"));
                } else {
                    current.sets.addAll(parseWeightedSets(payload));
                }
                continue;
            }

            if (current != null) {
                exercises.add(current.toResult());
            }

            Matcher comment = COMMENT_PATTERN.matcher(payload);
            String exerciseText = payload;
            String notes = null;
            if (comment.matches()) {
                exerciseText = comment.group(1).trim();
                notes = comment.group(2).trim();
            }

            int colonIndex = exerciseText.indexOf(':');
            if (colonIndex >= 0) {
                String name = exerciseText.substring(0, colonIndex).trim();
                String setsText = exerciseText.substring(colonIndex + 1).trim();
                current = new ExerciseResultBuilder(order, name, notes);
                current.sets.addAll(parseSets(setsText));
                continue;
            }

            Matcher setMatcher = WEIGHT_AND_REPS_PATTERN.matcher(exerciseText);
            if (setMatcher.find()) {
                String name = exerciseText.substring(0, setMatcher.start())
                        .replaceFirst("\\s*[—–-]\\s*$", "")
                        .trim();
                current = new ExerciseResultBuilder(order, name, notes);
                current.sets.addAll(parseWeightedSets(exerciseText.substring(setMatcher.start())));
                continue;
            }

            if (isMobility(exerciseText)) {
                current = new ExerciseResultBuilder(order, exerciseText, notes);
                current.sets.add(new SetResult(null, null, "MOBILITY"));
                continue;
            }

            current = new ExerciseResultBuilder(order, exerciseText, notes);
        }

        if (current != null) {
            exercises.add(current.toResult());
        }

        return new WorkoutResult(exercises, bodyWeightKg);
    }

    private boolean isIndentedSetLine(String line) {
        return line.matches("^\\s{2,}\\d+\\.\\s+.*$");
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
            String loadMode = parseLoadMode(matcher.group(2));
            int reps = Integer.parseInt(matcher.group(3));
            int setCount = matcher.group(4) == null ? 1 : Integer.parseInt(matcher.group(4));

            for (int i = 0; i < setCount; i++) {
                result.add(new SetResult(weight, reps, loadMode));
            }
        }

        return result;
    }

    private String parseLoadMode(String qualifier) {
        if (qualifier == null || qualifier.isBlank()) {
            return "TOTAL";
        }
        String normalized = qualifier.trim().toLowerCase();
        if (normalized.contains("на руку") || normalized.contains("на каждую руку")
                || normalized.contains("на сторону")) {
            return "PER_HAND";
        }
        if (normalized.contains("на ногу") || normalized.contains("на каждую ногу")) {
            return "PER_LEG";
        }
        return "TOTAL";
    }

    private static final class ExerciseResultBuilder {
        private final int order;
        private final String name;
        private final String notes;
        private final List<SetResult> sets = new ArrayList<>();

        private ExerciseResultBuilder(int order, String name, String notes) {
            this.order = order;
            this.name = name;
            this.notes = notes;
        }

        private ExerciseResult toResult() {
            return new ExerciseResult(order, name, notes, List.copyOf(sets));
        }
    }

    public record WorkoutResult(List<ExerciseResult> exercises, BigDecimal bodyWeightKg) {}

    public record ExerciseResult(
            int exerciseOrder,
            String exerciseName,
            String notes,
            List<SetResult> sets) {}

    public record SetResult(
            BigDecimal weightKg,
            Integer actualReps,
            String loadMode) {}
}
