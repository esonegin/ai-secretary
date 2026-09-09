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

    @Test
    void parsesFullWorkoutResult() {
        var result = parser.parse("""
            07.09.2026 День 2

            1. Наклонный жим гантелей: 40 кг × 8, 40 кг × 8, 40 кг × 10
            2. Румынская тяга: 80 кг × 10, 80 кг × 10, 80 кг × 10
            3. Вертикальная тяга: 30 кг × 10, 30 кг × 10, 30 кг × 12
            4. Отведения гантелей: 9 кг × 12, 9 кг × 12, 9 кг × 12
            5. Face Pull: 25 кг × 20, 25 кг × 20
            6. Молотки сидя: 15 кг × 12, 15 кг × 10, 15 кг × 10
            7. Жим узким хватом: 85 кг × 8, 87,5 кг × 8, 87,5 кг × 8
            8. Растяжка грудных: stretching
            9. Wall Slides: 12, 12

            Собственный вес 84,2 кг
            """);

        assertEquals(9, result.exercises().size());

        assertEquals(3, result.exercises().get(0).sets().size());
        assertEquals(3, result.exercises().get(1).sets().size());
        assertEquals(3, result.exercises().get(2).sets().size());
        assertEquals(3, result.exercises().get(3).sets().size());
        assertEquals(2, result.exercises().get(4).sets().size());
        assertEquals(3, result.exercises().get(5).sets().size());
        assertEquals(3, result.exercises().get(6).sets().size());

        assertEquals("STRETCHING",
                result.exercises().get(7).sets().getFirst().loadMode());

        assertEquals("BODYWEIGHT",
                result.exercises().get(8).sets().getFirst().loadMode());

        assertEquals(12,
                result.exercises().get(8).sets().getFirst().actualReps());
    }
}