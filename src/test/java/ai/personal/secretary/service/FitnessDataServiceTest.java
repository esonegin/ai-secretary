package ai.personal.secretary.service;

import ai.personal.secretary.model.TrainingSession;
import ai.personal.secretary.repository.TrainingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FitnessDataServiceTest {

    @Mock
    private TrainingSessionRepository trainingSessionRepository;

    @InjectMocks
    private FitnessDataService fitnessDataService;

    @Test
    void getWorkoutsDelegatesToFullHistoryQuery() {
        Long userId = 1L;
        List<TrainingSession> sessions = List.of(new TrainingSession());
        when(trainingSessionRepository.findByUserIdOrderByWorkoutDateDesc(userId)).thenReturn(sessions);

        List<TrainingSession> result = fitnessDataService.getWorkouts(userId);

        assertSame(sessions, result);
        verify(trainingSessionRepository).findByUserIdOrderByWorkoutDateDesc(userId);
    }

    @Test
    void getWorkoutsForRangeDelegatesToDateRangeQuery() {
        Long userId = 1L;
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        List<TrainingSession> sessions = List.of(new TrainingSession());
        when(trainingSessionRepository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDesc(
                userId, from, to)).thenReturn(sessions);

        List<TrainingSession> result = fitnessDataService.getWorkouts(userId, from, to);

        assertSame(sessions, result);
        verify(trainingSessionRepository)
                .findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDesc(userId, from, to);
    }

    @Test
    void getWorkoutDelegatesToUniqueWorkoutQuery() {
        Long userId = 1L;
        LocalDate workoutDate = LocalDate.of(2026, 1, 15);
        String dayType = "UPPER";
        Optional<TrainingSession> session = Optional.of(new TrainingSession());
        when(trainingSessionRepository.findByUserIdAndWorkoutDateAndDayType(userId, workoutDate, dayType))
                .thenReturn(session);

        Optional<TrainingSession> result = fitnessDataService.getWorkout(userId, workoutDate, dayType);

        assertSame(session, result);
        verify(trainingSessionRepository)
                .findByUserIdAndWorkoutDateAndDayType(userId, workoutDate, dayType);
    }
}
