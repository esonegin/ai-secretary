package ai.personal.secretary.service;

import ai.personal.secretary.model.FitnessGoal;
import ai.personal.secretary.model.TrainingExercise;
import ai.personal.secretary.model.TrainingProgram;
import ai.personal.secretary.model.TrainingProgramExercise;
import ai.personal.secretary.model.TrainingProgramSet;
import ai.personal.secretary.model.TrainingSession;
import ai.personal.secretary.model.TrainingSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingAnalysisContextBuilderTest {

    @Mock
    private FitnessDataService fitnessDataService;

    @InjectMocks
    private TrainingAnalysisContextBuilder builder;

    @Test
    void buildsContextFromWorkoutPlanGoalAndActualResults() {
        var userId = 2L;
        var workoutDate = LocalDate.of(2026, 9, 7);
        var dayType = "DAY_2";

        var session = TrainingSession.builder()
                .id(100L)
                .workoutDate(workoutDate)
                .dayType(dayType)
                .bodyWeightKg(new BigDecimal("84.2"))
                .build();

        var goal = FitnessGoal.builder()
                .goalText("Увеличение силы и мышечной массы")
                .status("ACTIVE")
                .build();

        var program = TrainingProgram.builder()
                .id(10L)
                .name("Основная программа")
                .version(3)
                .status("ACTIVE")
                .build();

        var programExercise = TrainingProgramExercise.builder()
                .id(20L)
                .exerciseOrder(1)
                .exerciseName("Наклонный жим гантелей")
                .exerciseVariant("24,5°")
                .build();

        var plannedSet1 = TrainingProgramSet.builder()
                .id(30L)
                .programExercise(programExercise)
                .setNumber(1)
                .weightKg(new BigDecimal("40"))
                .loadMode("TOTAL")
                .plannedRepsMin(8)
                .plannedRepsMax(10)
                .build();

        var plannedSet2 = TrainingProgramSet.builder()
                .id(31L)
                .programExercise(programExercise)
                .setNumber(2)
                .weightKg(new BigDecimal("40"))
                .loadMode("TOTAL")
                .plannedRepsMin(8)
                .plannedRepsMax(10)
                .build();

        var actualExercise = TrainingExercise.builder()
                .id(40L)
                .session(session)
                .exerciseOrder(1)
                .exerciseName("Наклонный жим гантелей")
                .exerciseVariant("24,5°")
                .build();

        var actualSet1 = TrainingSet.builder()
                .id(50L)
                .exercise(actualExercise)
                .setNumber(1)
                .weightKg(new BigDecimal("40"))
                .actualReps(8)
                .loadMode("TOTAL")
                .build();

        var actualSet2 = TrainingSet.builder()
                .id(51L)
                .exercise(actualExercise)
                .setNumber(2)
                .weightKg(new BigDecimal("40"))
                .actualReps(10)
                .loadMode("TOTAL")
                .build();

        when(fitnessDataService.getWorkout(userId, workoutDate, dayType))
                .thenReturn(Optional.of(session));

        when(fitnessDataService.getActiveGoal(userId))
                .thenReturn(Optional.of(goal));

        when(fitnessDataService.getActiveProgram(userId))
                .thenReturn(Optional.of(program));

        when(fitnessDataService.getProgramExercises(userId, dayType))
                .thenReturn(List.of(programExercise));

        when(fitnessDataService.getTrainingExercises(session.getId()))
                .thenReturn(List.of(actualExercise));

        when(fitnessDataService.getProgramSets(programExercise.getId()))
                .thenReturn(List.of(plannedSet1, plannedSet2));

        when(fitnessDataService.getTrainingSets(actualExercise.getId()))
                .thenReturn(List.of(actualSet1, actualSet2));

        var context = builder.build(userId, workoutDate, dayType);

        assertEquals(workoutDate, context.workoutDate());
        assertEquals(dayType, context.dayType());
        assertEquals(new BigDecimal("84.2"), context.bodyWeightKg());
        assertEquals("Увеличение силы и мышечной массы", context.goal());

        assertEquals("Основная программа", context.program().name());
        assertEquals(3, context.program().version());

        assertEquals(1, context.exercises().size());

        var exercise = context.exercises().get(0);

        assertEquals(1, exercise.order());
        assertEquals("Наклонный жим гантелей", exercise.name());
        assertEquals("24,5°", exercise.variant());

        assertEquals(2, exercise.plannedSets().size());

        assertEquals(1, exercise.plannedSets().get(0).setNumber());
        assertEquals(new BigDecimal("40"), exercise.plannedSets().get(0).weightKg());
        assertEquals(8, exercise.plannedSets().get(0).repsMin());
        assertEquals(10, exercise.plannedSets().get(0).repsMax());
        assertEquals("TOTAL", exercise.plannedSets().get(0).loadMode());

        assertEquals(2, exercise.actualSets().size());

        assertEquals(1, exercise.actualSets().get(0).setNumber());
        assertEquals(new BigDecimal("40"), exercise.actualSets().get(0).weightKg());
        assertEquals(8, exercise.actualSets().get(0).actualReps());
        assertEquals("TOTAL", exercise.actualSets().get(0).loadMode());

        assertEquals(2, exercise.actualSets().get(1).setNumber());
        assertEquals(10, exercise.actualSets().get(1).actualReps());
    }

    @Test
    void buildsContextWithoutGoalAndProgram() {
        var workoutDate = LocalDate.of(2026, 9, 7);
        var dayType = "DAY_2";

        var session = TrainingSession.builder()
                .id(100L)
                .workoutDate(workoutDate)
                .dayType(dayType)
                .bodyWeightKg(new BigDecimal("84.2"))
                .build();

        when(fitnessDataService.getWorkout(2L, workoutDate, dayType))
                .thenReturn(Optional.of(session));

        when(fitnessDataService.getActiveGoal(2L))
                .thenReturn(Optional.empty());

        when(fitnessDataService.getActiveProgram(2L))
                .thenReturn(Optional.empty());

        when(fitnessDataService.getProgramExercises(2L, dayType))
                .thenReturn(List.of());

        when(fitnessDataService.getTrainingExercises(session.getId()))
                .thenReturn(List.of());

        var context = builder.build(2L, workoutDate, dayType);

        assertEquals(workoutDate, context.workoutDate());
        assertEquals(dayType, context.dayType());
        assertEquals(new BigDecimal("84.2"), context.bodyWeightKg());
        assertNull(context.goal());
        assertNull(context.program());
        assertEquals(0, context.exercises().size());
    }
}