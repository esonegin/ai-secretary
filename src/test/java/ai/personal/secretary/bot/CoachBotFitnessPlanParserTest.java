package ai.personal.secretary.bot;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoachBotFitnessPlanParserTest {

    @Test
    void parsesFitnessPlanningMessage() {
        var result = CoachBot.parseFitnessPlan("Сегодня 07.09.2026 планирую День 2");

        assertTrue(result.isPresent());
        assertEquals(LocalDate.of(2026, 9, 7), result.get().date());
        assertEquals("DAY_2", result.get().dayType());
    }

    @Test
    void returnsEmptyForInvalidDate() {
        assertFalse(CoachBot.parseFitnessPlan("Сегодня 32.09.2026 планирую День 2").isPresent());
    }

    @Test
    void returnsEmptyWithoutProgramDay() {
        assertFalse(CoachBot.parseFitnessPlan("Сегодня 07.09.2026 планирую тренировку").isPresent());
    }
}
