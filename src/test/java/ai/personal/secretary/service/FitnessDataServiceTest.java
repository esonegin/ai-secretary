package ai.personal.secretary.service;

import ai.personal.secretary.model.*;
import ai.personal.secretary.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FitnessDataServiceTest {

    @Mock private TrainingSessionRepository trainingSessionRepository;
    @Mock private FitnessGoalRepository fitnessGoalRepository;
    @Mock private TrainingProgramRepository trainingProgramRepository;
    @Mock private TrainingProgramDayRepository trainingProgramDayRepository;
    @Mock private TrainingProgramExerciseRepository trainingProgramExerciseRepository;
    @Mock private TrainingProgramSetRepository trainingProgramSetRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private TrainingExerciseRepository trainingExerciseRepository;
    @Mock private TrainingSetRepository trainingSetRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private FitnessDataService fitnessDataService;

    @Test
    void getWorkoutsDelegatesToFullHistoryQuery() {
        Long userId = 1L;
        List<TrainingSession> sessions = List.of(new TrainingSession());
        when(trainingSessionRepository.findByUserIdOrderByWorkoutDateDesc(userId)).thenReturn(sessions);

        assertSame(sessions, fitnessDataService.getWorkouts(userId));
        verify(trainingSessionRepository).findByUserIdOrderByWorkoutDateDesc(userId);
    }

    @Test
    void getWorkoutsForRangeDelegatesToDateRangeQuery() {
        Long userId = 1L;
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        List<TrainingSession> sessions = List.of(new TrainingSession());
        when(trainingSessionRepository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDesc(userId, from, to))
                .thenReturn(sessions);

        assertSame(sessions, fitnessDataService.getWorkouts(userId, from, to));
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

        assertSame(session, fitnessDataService.getWorkout(userId, workoutDate, dayType));
        verify(trainingSessionRepository).findByUserIdAndWorkoutDateAndDayType(userId, workoutDate, dayType);
    }

    @Test
    void getActiveGoalDelegatesToActiveGoalQuery() {
        Long userId = 1L;
        FitnessGoal goal = new FitnessGoal();
        when(fitnessGoalRepository.findFirstByUserIdAndStatusOrderByPriorityDescCreatedAtDesc(userId, "ACTIVE"))
                .thenReturn(Optional.of(goal));

        assertSame(goal, fitnessDataService.getActiveGoal(userId).orElseThrow());
    }

    @Test
    void getActiveProgramDelegatesToActiveProgramQuery() {
        Long userId = 1L;
        TrainingProgram program = new TrainingProgram();
        when(trainingProgramRepository.findFirstByUserIdAndStatusOrderByValidFromDescCreatedAtDesc(userId, "ACTIVE"))
                .thenReturn(Optional.of(program));

        assertSame(program, fitnessDataService.getActiveProgram(userId).orElseThrow());
    }

    @Test
    void getProgramDayNormalizesDayPrefix() {
        TrainingProgramDay day = new TrainingProgramDay();
        when(trainingProgramDayRepository.findByProgramIdAndDayType(10L, "1"))
                .thenReturn(Optional.of(day));

        assertSame(day, fitnessDataService.getProgramDay(10L, "DAY_1").orElseThrow());
        verify(trainingProgramDayRepository).findByProgramIdAndDayType(10L, "1");
    }

    @Test
    void getProgramExercisesReturnsExercisesForActiveProgramDay() {
        TrainingProgram program = TrainingProgram.builder().id(10L).build();
        TrainingProgramDay day = TrainingProgramDay.builder().id(20L).build();
        var exercise = TrainingProgramExercise.builder().id(30L).build();

        when(trainingProgramRepository.findFirstByUserIdAndStatusOrderByValidFromDescCreatedAtDesc(1L, "ACTIVE"))
                .thenReturn(Optional.of(program));
        when(trainingProgramDayRepository.findByProgramIdAndDayType(10L, "1"))
                .thenReturn(Optional.of(day));
        when(trainingProgramExerciseRepository.findByProgramDayIdOrderByExerciseOrder(20L))
                .thenReturn(List.of(exercise));

        assertEquals(List.of(exercise), fitnessDataService.getProgramExercises(1L, "DAY_1"));
    }

    @Test
    void getTrainingExercisesDelegatesToSessionQuery() {
        var exercises = List.of(new TrainingExercise());
        when(trainingExerciseRepository.findBySessionIdOrderByExerciseOrder(10L)).thenReturn(exercises);

        assertSame(exercises, fitnessDataService.getTrainingExercises(10L));
        verify(trainingExerciseRepository).findBySessionIdOrderByExerciseOrder(10L);
    }

    @Test
    void getTrainingSetsDelegatesToExerciseQuery() {
        var sets = List.of(new TrainingSet());
        when(trainingSetRepository.findByExerciseIdOrderBySetNumber(10L)).thenReturn(sets);

        assertSame(sets, fitnessDataService.getTrainingSets(10L));
        verify(trainingSetRepository).findByExerciseIdOrderBySetNumber(10L);
    }

    @Test
    void getProgramSetsDelegatesToProgramExerciseQuery() {
        var sets = List.of(new TrainingProgramSet());
        when(trainingProgramSetRepository.findByProgramExerciseIdOrderBySetNumber(10L)).thenReturn(sets);

        assertSame(sets, fitnessDataService.getProgramSets(10L));
    }

    @Test
    void getProgramSetCountDelegatesToCountQuery() {
        when(trainingProgramSetRepository.countByProgramExerciseId(10L)).thenReturn(3L);

        assertEquals(3L, fitnessDataService.getProgramSetCount(10L));
    }

    @Test
    void startWorkoutCreatesOnlySessionWithoutCopyingProgram() {
        var date = LocalDate.of(2026, 9, 16);
        var user = UserProfile.builder().id(1L).build();
        var session = TrainingSession.builder().id(100L).user(user).workoutDate(date).dayType("2").build();
        when(trainingSessionRepository.findByUserIdAndWorkoutDateAndDayType(1L, date, "2"))
                .thenReturn(Optional.empty());
        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(user));
        when(trainingSessionRepository.save(any(TrainingSession.class))).thenReturn(session);

        var result = fitnessDataService.startWorkout(1L, date, "2");

        assertSame(session, result);
        verify(trainingSessionRepository).save(any(TrainingSession.class));
        verifyNoInteractions(trainingProgramRepository, trainingProgramDayRepository,
                trainingProgramExerciseRepository, trainingProgramSetRepository,
                trainingExerciseRepository, trainingSetRepository);
    }

    @Test
    void recordWorkoutResultPersistsActualExercisesAndVariableSetCount() {
        var date = LocalDate.of(2026, 9, 16);
        var user = UserProfile.builder().id(1L).build();
        var session = TrainingSession.builder().id(100L).user(user).workoutDate(date).dayType("2").build();
        when(trainingSessionRepository.findByUserIdAndWorkoutDateAndDayType(1L, date, "2"))
                .thenReturn(Optional.of(session));

        var result = new WorkoutResultParser.WorkoutResult(List.of(
                new WorkoutResultParser.ExerciseResult(1, "Вертикальный Хаммер",
                        "свободного верхнего блока не было", List.of(
                        new WorkoutResultParser.SetResult(new BigDecimal("40"), 12, "PER_HAND"),
                        new WorkoutResultParser.SetResult(new BigDecimal("40"), 10, "PER_HAND"),
                        new WorkoutResultParser.SetResult(new BigDecimal("35"), 12, "PER_HAND"),
                        new WorkoutResultParser.SetResult(new BigDecimal("35"), 10, "PER_HAND")
                )),
                new WorkoutResultParser.ExerciseResult(2, "Румынская тяга", null, List.of(
                        new WorkoutResultParser.SetResult(new BigDecimal("82.5"), 10, "TOTAL"),
                        new WorkoutResultParser.SetResult(new BigDecimal("82.5"), 9, "TOTAL")
                ))
        ));

        when(trainingExerciseRepository.save(any(TrainingExercise.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(trainingSetRepository.save(any(TrainingSet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(trainingSessionRepository.save(session)).thenReturn(session);

        var saved = fitnessDataService.recordWorkoutResult(1L, date, "2", new BigDecimal("83.0"), result);

        assertSame(session, saved);
        assertEquals(new BigDecimal("83.0"), session.getBodyWeightKg());

        var exerciseCaptor = org.mockito.ArgumentCaptor.forClass(TrainingExercise.class);
        verify(trainingExerciseRepository, times(2)).save(exerciseCaptor.capture());
        var exercises = exerciseCaptor.getAllValues();
        assertEquals("Вертикальный Хаммер", exercises.get(0).getExerciseName());
        assertEquals("свободного верхнего блока не было", exercises.get(0).getNotes());
        assertEquals("Румынская тяга", exercises.get(1).getExerciseName());

        var setCaptor = org.mockito.ArgumentCaptor.forClass(TrainingSet.class);
        verify(trainingSetRepository, times(6)).save(setCaptor.capture());
        var sets = setCaptor.getAllValues();
        assertEquals(6, sets.size());
        assertEquals("PER_HAND", sets.get(0).getLoadMode());
        assertEquals(new BigDecimal("40"), sets.get(0).getWeightKg());
        assertEquals(12, sets.get(0).getActualReps());
        assertEquals(1, sets.get(0).getSetNumber());
        assertEquals(4, sets.get(3).getSetNumber());
        assertEquals(1, sets.get(4).getSetNumber());
        assertEquals(2, sets.get(5).getSetNumber());
    }

    @Test
    void recordWorkoutResultSupportsStretchingAndBodyweightWithoutProgramSets() {
        var date = LocalDate.of(2026, 9, 16);
        var session = TrainingSession.builder().id(200L).workoutDate(date).dayType("2").build();
        when(trainingSessionRepository.findByUserIdAndWorkoutDateAndDayType(1L, date, "2"))
                .thenReturn(Optional.of(session));
        when(trainingExerciseRepository.save(any(TrainingExercise.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(trainingSetRepository.save(any(TrainingSet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(trainingSessionRepository.save(session)).thenReturn(session);

        var result = new WorkoutResultParser.WorkoutResult(List.of(
                new WorkoutResultParser.ExerciseResult(8, "Растяжка грудных", null, List.of(
                        new WorkoutResultParser.SetResult(null, null, "MOBILITY")
                )),
                new WorkoutResultParser.ExerciseResult(9, "Wall Slides", null, List.of(
                        new WorkoutResultParser.SetResult(null, 12, "BODYWEIGHT"),
                        new WorkoutResultParser.SetResult(null, 10, "BODYWEIGHT")
                ))
        ));

        fitnessDataService.recordWorkoutResult(1L, date, "2", null, result);

        var setCaptor = org.mockito.ArgumentCaptor.forClass(TrainingSet.class);
        verify(trainingSetRepository, times(3)).save(setCaptor.capture());
        var sets = setCaptor.getAllValues();
        assertEquals("MOBILITY", sets.get(0).getLoadMode());
        assertNull(sets.get(0).getWeightKg());
        assertNull(sets.get(0).getActualReps());
        assertEquals("BODYWEIGHT", sets.get(1).getLoadMode());
        assertEquals(12, sets.get(1).getActualReps());
        assertEquals("BODYWEIGHT", sets.get(2).getLoadMode());
        assertEquals(10, sets.get(2).getActualReps());
    }
}
