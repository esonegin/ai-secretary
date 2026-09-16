package ai.personal.secretary.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class WorkoutResultParserTest {

    private final WorkoutResultParser parser = new WorkoutResultParser();

    @Test
    void parsesWeightedSets() {
        var result = parser.parse("""
                1. Наклонный жим гантелей: 40 кг × 8, 40 кг × 8, 40 кг × 10
                """);

        assertEquals(1, result.exercises().size());
        assertEquals(1, result.exercises().get(0).exerciseOrder());
        assertEquals("Наклонный жим гантелей", result.exercises().get(0).exerciseName());

        var sets = result.exercises().get(0).sets();
        assertEquals(3, sets.size());
        assertEquals(new BigDecimal("40"), sets.get(0).weightKg());
        assertEquals(8, sets.get(0).actualReps());
        assertEquals("TOTAL", sets.get(0).loadMode());
        assertEquals(new BigDecimal("40"), sets.get(1).weightKg());
        assertEquals(8, sets.get(1).actualReps());
        assertEquals(new BigDecimal("40"), sets.get(2).weightKg());
        assertEquals(10, sets.get(2).actualReps());
    }

    @Test
    void parsesMobility() {
        var result = parser.parse("""
                8. Растяжка грудных: mobility
                9. Wall Slides: mobility
                """);

        assertEquals(2, result.exercises().size());
        assertEquals("MOBILITY", result.exercises().get(0).sets().get(0).loadMode());
        assertEquals("MOBILITY", result.exercises().get(1).sets().get(0).loadMode());
    }

    @Test
    void parsesStretchingAsMobilityAlias() {
        var result = parser.parse("""
                8. Растяжка грудных: stretching
                """);

        var set = result.exercises().get(0).sets().get(0);
        assertNull(set.weightKg());
        assertNull(set.actualReps());
        assertEquals("MOBILITY", set.loadMode());
    }

    @Test
    void parsesBodyweightSets() {
        var result = parser.parse("""
                1. Отжимания: собственный вес × 12 × 3
                2. Подтягивания: bodyweight × 8 × 2
                3. Wall Slides: вес тела × 15
                """);

        assertEquals(3, result.exercises().size());

        var pushUps = result.exercises().get(0).sets();
        assertEquals(3, pushUps.size());
        assertNull(pushUps.get(0).weightKg());
        assertEquals(12, pushUps.get(0).actualReps());
        assertEquals("BODYWEIGHT", pushUps.get(0).loadMode());
        assertEquals("BODYWEIGHT", pushUps.get(2).loadMode());

        var pullUps = result.exercises().get(1).sets();
        assertEquals(2, pullUps.size());
        assertEquals(8, pullUps.get(1).actualReps());
        assertEquals("BODYWEIGHT", pullUps.get(1).loadMode());

        var wallSlides = result.exercises().get(2).sets();
        assertEquals(1, wallSlides.size());
        assertEquals(15, wallSlides.get(0).actualReps());
        assertEquals("BODYWEIGHT", wallSlides.get(0).loadMode());
    }

    @Test
    void parsesFullWorkoutResult() {
        var result = parser.parse("""
                07.09.2026 День 2
                1. Наклонный жим гантелей: 40 кг × 8, 40 кг × 8, 40 кг × 10
                2. Румынская тяга: 80 кг × 10, 80 кг × 10, 80 кг × 10
                3. Вертикальная тяга: 30 кг × 10, 30 кг × 10, 30 кг × 12
                4. Отведения гантелей в стороны: 9 кг × 12, 9 кг × 12, 9 кг × 12
                5. Face Pull: 25 кг × 20, 25 кг × 20
                6. Молотки сидя с гантелями: 15 кг × 12, 15 кг × 10, 15 кг × 10
                7. Жим штанги узким хватом: 85 кг × 8, 87,5 кг × 8, 87,5 кг × 8
                8. Растяжка грудных: mobility
                9. Wall Slides: mobility
                Собственный вес 84,2кг
                """);

        assertEquals(9, result.exercises().size());
        assertEquals(3, result.exercises().get(0).sets().size());
        assertEquals(3, result.exercises().get(1).sets().size());
        assertEquals(3, result.exercises().get(2).sets().size());
        assertEquals(3, result.exercises().get(3).sets().size());
        assertEquals(2, result.exercises().get(4).sets().size());
        assertEquals(3, result.exercises().get(5).sets().size());
        assertEquals(3, result.exercises().get(6).sets().size());
        assertEquals("MOBILITY", result.exercises().get(7).sets().get(0).loadMode());
        assertEquals("MOBILITY", result.exercises().get(8).sets().get(0).loadMode());
    }

    @Test
    void parsesChecklistResult() {
        var result = parser.parse("""
                📅 14.09.2026
                💪 День DAY_1: базовая сила + верх тела

                1. Приседания со штангой
                   1. 92,5 кг × 10
                   2. 92,5 кг × 9
                   3. 92,5 кг × 9

                2. Жим штанги лёжа
                   1. 102,5 кг × 8
                   2. 100 кг × 9
                   3. 100 кг × 8

                7. Пресс «молитва»
                   1. 65 кг × 15
                   2. 65 кг × 15
                   3. 65 кг × 15

                9. Wall Slides
                   1. mobility
                   2. mobility
                """);

        assertEquals(4, result.exercises().size());
        assertEquals("Приседания со штангой", result.exercises().get(0).exerciseName());
        assertEquals(3, result.exercises().get(0).sets().size());
        assertEquals(new BigDecimal("92.5"), result.exercises().get(0).sets().get(0).weightKg());
        assertEquals(10, result.exercises().get(0).sets().get(0).actualReps());
        assertEquals("Жим штанги лёжа", result.exercises().get(1).exerciseName());
        assertEquals(new BigDecimal("100"), result.exercises().get(1).sets().get(1).weightKg());
        assertEquals("Пресс «молитва»", result.exercises().get(2).exerciseName());
        assertEquals(15, result.exercises().get(2).sets().get(2).actualReps());
        assertEquals("MOBILITY", result.exercises().get(3).sets().get(0).loadMode());
        assertEquals("MOBILITY", result.exercises().get(3).sets().get(1).loadMode());
    }

    @Test
    void expandsCompactSetCount() {
        var result = parser.parse("1. Пресс «молитва»: 65 кг × 15 × 3");
        assertEquals(1, result.exercises().size());
        assertEquals(3, result.exercises().get(0).sets().size());
        assertEquals(15, result.exercises().get(0).sets().get(2).actualReps());
    }
}
