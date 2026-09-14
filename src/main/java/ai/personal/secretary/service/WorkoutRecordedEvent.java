package ai.personal.secretary.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkoutRecordedEvent(
        Long userId,
        LocalDate workoutDate,
        String dayType,
        BigDecimal bodyWeightKg) {
}
