package ai.personal.secretary.service;

import ai.personal.secretary.model.TrainingSession;
import ai.personal.secretary.model.FitnessGoal;
import ai.personal.secretary.model.TrainingProgram;
import ai.personal.secretary.model.TrainingProgramDay;
import ai.personal.secretary.repository.FitnessGoalRepository;
import ai.personal.secretary.repository.TrainingProgramDayRepository;
import ai.personal.secretary.repository.TrainingProgramRepository;
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

    @Mock
    private FitnessGoalRepository fitnessGoalRepository;

    @Mock
    private TrainingProgramRepository trainingProgramRepository;

    @Mock
    private TrainingProgramDayRepository trainingProgramDayRepository;

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

    @Test
    void getActiveGoalDelegatesToActiveGoalQuery() {
        Long userId = 1L;
        Optional<FitnessGoal> goal = Optional.of(new FitnessGoal());
        when(fitnessGoalRepository.findFirstByUserIdAndStatusOrderByPriorityDescCreatedAtDesc(userId, "ACTIVE"))
                .thenReturn(goal);

        Optional<FitnessGoal> result = fitnessDataService.getActiveGoal(userId);

        assertSame(goal, result);
        verify(fitnessGoalRepository)
                .findFirstByUserIdAndStatusOrderByPriorityDescCreatedAtDesc(userId, "ACTIVE");
    }

    @Test
    void saveGoalDelegatesToRepository() {
        FitnessGoal goal = new FitnessGoal();
        when(fitnessGoalRepository.save(goal)).thenReturn(goal);

        FitnessGoal result = fitnessDataService.saveGoal(goal);

        assertSame(goal, result);
        verify(fitnessGoalRepository).save(goal);
    }

    @Test
    void getActiveProgramDelegatesToActiveProgramQuery() {
        Long userId = 1L;
        Optional<TrainingProgram> program = Optional.of(new TrainingProgram());
        when(trainingProgramRepository.findFirstByUserIdAndStatusOrderByValidFromDescCreatedAtDesc(userId, "ACTIVE"))
                .thenReturn(program);

        Optional<TrainingProgram> result = fitnessDataService.getActiveProgram(userId);

        assertSame(program, result);
        verify(trainingProgramRepository)
                .findFirstByUserIdAndStatusOrderByValidFromDescCreatedAtDesc(userId, "ACTIVE");
    }

    @Test
    void getProgramDayDelegatesToProgramDayQuery() {
        Long programId = 1L;
        String dayType = "1";
        Optional<TrainingProgramDay> day = Optional.of(new TrainingProgramDay());
        when(trainingProgramDayRepository.findByProgramIdAndDayType(programId, dayType)).thenReturn(day);

        Optional<TrainingProgramDay> result = fitnessDataService.getProgramDay(programId, dayType);

        assertSame(day, result);
        verify(trainingProgramDayRepository).findByProgramIdAndDayType(programId, dayType);
    }
}
