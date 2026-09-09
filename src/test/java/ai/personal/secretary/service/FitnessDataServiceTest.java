package ai.personal.secretary.service;

import ai.personal.secretary.model.TrainingSession;
import ai.personal.secretary.model.FitnessGoal;
import ai.personal.secretary.model.TrainingProgram;
import ai.personal.secretary.model.TrainingProgramDay;
import ai.personal.secretary.model.TrainingProgramExercise;
import ai.personal.secretary.repository.FitnessGoalRepository;
import ai.personal.secretary.repository.TrainingProgramDayRepository;
import ai.personal.secretary.repository.TrainingProgramExerciseRepository;
import ai.personal.secretary.repository.TrainingProgramRepository;
import ai.personal.secretary.repository.TrainingProgramSetRepository;
import ai.personal.secretary.repository.TrainingSessionRepository;
import ai.personal.secretary.repository.UserProfileRepository;
import ai.personal.secretary.model.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ai.personal.secretary.model.TrainingExercise;
import ai.personal.secretary.model.TrainingSet;
import ai.personal.secretary.repository.TrainingExerciseRepository;
import ai.personal.secretary.repository.TrainingSetRepository;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private TrainingProgramExerciseRepository trainingProgramExerciseRepository;

    @Mock
    private TrainingProgramSetRepository trainingProgramSetRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private FitnessDataService fitnessDataService;

    @Mock
    private TrainingExerciseRepository trainingExerciseRepository;

    @Mock
    private TrainingSetRepository trainingSetRepository;

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
    void saveGoalCreatesActiveGoalForUser() {
        Long userId = 1L;
        String goalText = "Увеличить силу";
        UserProfile user = new UserProfile();
        FitnessGoal savedGoal = new FitnessGoal();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fitnessGoalRepository.save(any(FitnessGoal.class))).thenReturn(savedGoal);

        FitnessGoal result = fitnessDataService.saveGoal(userId, goalText);

        assertSame(savedGoal, result);
        verify(userProfileRepository).findById(userId);
        verify(fitnessGoalRepository).save(FitnessGoal.builder()
                .user(user).goalText(goalText).status("ACTIVE").build());
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

    @Test
    void getProgramExercisesReturnsExercisesForActiveProgramDay() {
        Long userId = 1L;
        String dayType = "1";
        TrainingProgram program = TrainingProgram.builder().id(10L).build();
        TrainingProgramDay day = TrainingProgramDay.builder().id(20L).build();
        List<TrainingProgramExercise> exercises = List.of(new TrainingProgramExercise());
        when(trainingProgramRepository.findFirstByUserIdAndStatusOrderByValidFromDescCreatedAtDesc(userId, "ACTIVE"))
                .thenReturn(Optional.of(program));
        when(trainingProgramDayRepository.findByProgramIdAndDayType(10L, dayType)).thenReturn(Optional.of(day));
        when(trainingProgramExerciseRepository.findByProgramDayIdOrderByExerciseOrder(20L))
                .thenReturn(exercises);

        List<TrainingProgramExercise> result = fitnessDataService.getProgramExercises(userId, dayType);

        assertSame(exercises, result);
        verify(trainingProgramExerciseRepository).findByProgramDayIdOrderByExerciseOrder(20L);
    }

    @Test
    void getProgramExercisesReturnsEmptyListWhenThereIsNoActiveProgram() {
        when(trainingProgramRepository.findFirstByUserIdAndStatusOrderByValidFromDescCreatedAtDesc(1L, "ACTIVE"))
                .thenReturn(Optional.empty());

        List<TrainingProgramExercise> result = fitnessDataService.getProgramExercises(1L, "1");

        org.junit.jupiter.api.Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void getProgramSetCountDelegatesToRepository() {
        when(trainingProgramSetRepository.countByProgramExerciseId(1L)).thenReturn(3L);

        long result = fitnessDataService.getProgramSetCount(1L);

        org.junit.jupiter.api.Assertions.assertEquals(3L, result);
        verify(trainingProgramSetRepository).countByProgramExerciseId(1L);
    }

    @Test
    void recordWorkoutResultUpdatesExistingSetsAndBodyWeight() {
        var workoutDate = LocalDate.of(2026, 9, 7);
        var dayType = "2";

        var session = TrainingSession.builder()
                .id(100L)
                .workoutDate(workoutDate)
                .dayType(dayType)
                .build();

        var exercise = TrainingExercise.builder()
                .id(200L)
                .session(session)
                .exerciseOrder(1)
                .exerciseName("Наклонный жим гантелей")
                .build();

        var set1 = TrainingSet.builder()
                .id(301L)
                .exercise(exercise)
                .setNumber(1)
                .loadMode("TOTAL")
                .build();

        var set2 = TrainingSet.builder()
                .id(302L)
                .exercise(exercise)
                .setNumber(2)
                .loadMode("TOTAL")
                .build();

        var set3 = TrainingSet.builder()
                .id(303L)
                .exercise(exercise)
                .setNumber(3)
                .loadMode("TOTAL")
                .build();

        when(trainingSessionRepository
                .findByUserIdAndWorkoutDateAndDayType(1L, workoutDate, dayType))
                .thenReturn(Optional.of(session));

        when(trainingExerciseRepository
                .findBySessionIdOrderByExerciseOrder(session.getId()))
                .thenReturn(List.of(exercise));

        when(trainingSetRepository
                .findByExerciseIdOrderBySetNumber(exercise.getId()))
                .thenReturn(List.of(set1, set2, set3));

        var result = new WorkoutResultParser.WorkoutResult(List.of(
                new WorkoutResultParser.ExerciseResult(
                        1,
                        "Наклонный жим гантелей",
                        List.of(
                                new WorkoutResultParser.SetResult(
                                        new BigDecimal("40"), 8, "TOTAL"),
                                new WorkoutResultParser.SetResult(
                                        new BigDecimal("40"), 8, "TOTAL"),
                                new WorkoutResultParser.SetResult(
                                        new BigDecimal("40"), 10, "TOTAL")
                        )
                )
        ));

        when(trainingSessionRepository.save(session)).thenReturn(session);

        var saved = fitnessDataService.recordWorkoutResult(
                1L,
                workoutDate,
                dayType,
                new BigDecimal("84.2"),
                result
        );

        assertEquals(new BigDecimal("84.2"), saved.getBodyWeightKg());

        assertEquals(new BigDecimal("40"), set1.getWeightKg());
        assertEquals(8, set1.getActualReps());

        assertEquals(new BigDecimal("40"), set2.getWeightKg());
        assertEquals(8, set2.getActualReps());

        assertEquals(new BigDecimal("40"), set3.getWeightKg());
        assertEquals(10, set3.getActualReps());

        verify(trainingSessionRepository).save(session);
    }

    @Test
    void recordWorkoutResultSupportsBodyweightAndStretching() {
        var workoutDate = LocalDate.of(2026, 9, 7);
        var dayType = "2";

        var session = TrainingSession.builder()
                .id(200L)
                .workoutDate(workoutDate)
                .dayType(dayType)
                .build();

        var stretching = TrainingExercise.builder()
                .id(201L)
                .session(session)
                .exerciseOrder(8)
                .exerciseName("Растяжка грудных")
                .build();

        var wallSlides = TrainingExercise.builder()
                .id(202L)
                .session(session)
                .exerciseOrder(9)
                .exerciseName("Wall Slides")
                .build();

        var stretchingSet = TrainingSet.builder()
                .id(301L)
                .exercise(stretching)
                .setNumber(1)
                .loadMode("TOTAL")
                .build();

        var wallSet1 = TrainingSet.builder()
                .id(302L)
                .exercise(wallSlides)
                .setNumber(1)
                .loadMode("TOTAL")
                .build();

        var wallSet2 = TrainingSet.builder()
                .id(303L)
                .exercise(wallSlides)
                .setNumber(2)
                .loadMode("TOTAL")
                .build();

        when(trainingSessionRepository
                .findByUserIdAndWorkoutDateAndDayType(1L, workoutDate, dayType))
                .thenReturn(Optional.of(session));

        when(trainingExerciseRepository
                .findBySessionIdOrderByExerciseOrder(session.getId()))
                .thenReturn(List.of(stretching, wallSlides));

        when(trainingSetRepository
                .findByExerciseIdOrderBySetNumber(stretching.getId()))
                .thenReturn(List.of(stretchingSet));

        when(trainingSetRepository
                .findByExerciseIdOrderBySetNumber(wallSlides.getId()))
                .thenReturn(List.of(wallSet1, wallSet2));

        var result = new WorkoutResultParser.WorkoutResult(List.of(
                new WorkoutResultParser.ExerciseResult(
                        8,
                        "Растяжка грудных",
                        List.of(new WorkoutResultParser.SetResult(
                                null, null, "STRETCHING"))
                ),
                new WorkoutResultParser.ExerciseResult(
                        9,
                        "Wall Slides",
                        List.of(
                                new WorkoutResultParser.SetResult(
                                        null, 12, "BODYWEIGHT"),
                                new WorkoutResultParser.SetResult(
                                        null, 12, "BODYWEIGHT")
                        )
                )
        ));

        fitnessDataService.recordWorkoutResult(
                1L,
                workoutDate,
                dayType,
                new BigDecimal("84.2"),
                result
        );

        assertNull(stretchingSet.getWeightKg());
        assertNull(stretchingSet.getActualReps());
        assertEquals("STRETCHING", stretchingSet.getLoadMode());

        assertNull(wallSet1.getWeightKg());
        assertEquals(12, wallSet1.getActualReps());
        assertEquals("BODYWEIGHT", wallSet1.getLoadMode());

        assertNull(wallSet2.getWeightKg());
        assertEquals(12, wallSet2.getActualReps());
        assertEquals("BODYWEIGHT", wallSet2.getLoadMode());
    }

    @Test
    void shouldRecordBodyweightAndStretchingResults() {
        var session = TrainingSession.builder()
                .id(200L)
                .workoutDate(LocalDate.of(2026, 9, 7))
                .dayType("2")
                .build();

        var stretchingExercise = TrainingExercise.builder()
                .id(300L)
                .exerciseOrder(8)
                .exerciseName("Растяжка грудных")
                .session(session)
                .build();

        var wallSlidesExercise = TrainingExercise.builder()
                .id(301L)
                .exerciseOrder(9)
                .exerciseName("Wall Slides")
                .session(session)
                .build();

        var stretchingSet = TrainingSet.builder()
                .id(400L)
                .exercise(stretchingExercise)
                .setNumber(1)
                .build();

        var wallSlidesSet1 = TrainingSet.builder()
                .id(401L)
                .exercise(wallSlidesExercise)
                .setNumber(1)
                .build();

        var wallSlidesSet2 = TrainingSet.builder()
                .id(402L)
                .exercise(wallSlidesExercise)
                .setNumber(2)
                .build();

        when(trainingSessionRepository
                .findByUserIdAndWorkoutDateAndDayType(
                        1L,
                        LocalDate.of(2026, 9, 7),
                        "2"))
                .thenReturn(Optional.of(session));

        when(trainingExerciseRepository
                .findBySessionIdOrderByExerciseOrder(200L))
                .thenReturn(List.of(stretchingExercise, wallSlidesExercise));

        when(trainingSetRepository
                .findByExerciseIdOrderBySetNumber(300L))
                .thenReturn(List.of(stretchingSet));

        when(trainingSetRepository
                .findByExerciseIdOrderBySetNumber(301L))
                .thenReturn(List.of(wallSlidesSet1, wallSlidesSet2));

        var result = new WorkoutResultParser.WorkoutResult(List.of(
                new WorkoutResultParser.ExerciseResult(
                        8,
                        "Растяжка грудных",
                        List.of(new WorkoutResultParser.SetResult(
                                null,
                                null,
                                "STRETCHING"
                        ))
                ),
                new WorkoutResultParser.ExerciseResult(
                        9,
                        "Wall Slides",
                        List.of(
                                new WorkoutResultParser.SetResult(
                                        null,
                                        12,
                                        "BODYWEIGHT"
                                ),
                                new WorkoutResultParser.SetResult(
                                        null,
                                        12,
                                        "BODYWEIGHT"
                                )
                        )
                )
        ));

        fitnessDataService.recordWorkoutResult(
                1L,
                LocalDate.of(2026, 9, 7),
                "2",
                new BigDecimal("84.2"),
                result
        );

        assertNull(stretchingSet.getWeightKg());
        assertNull(stretchingSet.getActualReps());
        assertEquals("STRETCHING", stretchingSet.getLoadMode());

        assertNull(wallSlidesSet1.getWeightKg());
        assertEquals(12, wallSlidesSet1.getActualReps());
        assertEquals("BODYWEIGHT", wallSlidesSet1.getLoadMode());

        assertNull(wallSlidesSet2.getWeightKg());
        assertEquals(12, wallSlidesSet2.getActualReps());
        assertEquals("BODYWEIGHT", wallSlidesSet2.getLoadMode());

        assertEquals(
                new BigDecimal("84.2"),
                session.getBodyWeightKg()
        );
    }
}
