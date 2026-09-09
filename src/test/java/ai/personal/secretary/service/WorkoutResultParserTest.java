package ai.personal.secretary.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkoutResultParserTest {

    private final WorkoutResultParser parser = new WorkoutResultParser();

    @Test
    void parsesWeightedExercise() {
        var result = parser.parse("""
                1. Наклонный жим гантелей: 40 кг × 8, 40 кг × 8, 40 кг × 10
                """);

        assertEquals(1, result.exercises().size());

        var exercise = result.exercises().getFirst();

        assertEquals(1, exercise.exerciseOrder());
        assertEquals("Наклонный жим гантелей", exercise.exerciseName());
        assertEquals(3, exercise.sets().size());

        assertEquals("40", exercise.sets().get(0).weightKg().toPlainString());
        assertEquals(8, exercise.sets().get(0).actualReps());

        assertEquals("40", exercise.sets().get(1).weightKg().toPlainString());
        assertEquals(8, exercise.sets().get(1).actualReps());

        assertEquals("40", exercise.sets().get(2).weightKg().toPlainString());
        assertEquals(10, exercise.sets().get(2).actualReps());
    }

    @Test
    void parsesBodyweightExercise() {
        var result = parser.parse("""
                9. Wall Slides: 12, 12
                """);

        var exercise = result.exercises().getFirst();

        assertEquals("Wall Slides", exercise.exerciseName());
        assertEquals(2, exercise.sets().size());

        assertNull(exercise.sets().get(0).weightKg());
        assertEquals(12, exercise.sets().get(0).actualReps());
        assertEquals("BODYWEIGHT", exercise.sets().get(0).loadMode());
    }

    @Test
    void parsesStretching() {
        var result = parser.parse("""
                8. Растяжка грудных: stretching
                """);

        var exercise = result.exercises().getFirst();

        assertEquals("Растяжка грудных", exercise.exerciseName());
        assertEquals(1, exercise.sets().size());

        assertNull(exercise.sets().getFirst().weightKg());
        assertNull(exercise.sets().getFirst().actualReps());
        assertEquals("STRETCHING", exercise.sets().getFirst().loadMode());
    }
}