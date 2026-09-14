package ai.personal.secretary.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingPlanner {

    private final ChatClient.Builder chatClientBuilder;
    private final TrainingStateCalculator trainingStateCalculator;

    public TrainingPlanProposal propose(TrainingAnalysisContext context) {
        return proposeInternal(context, null);
    }

    public TrainingPlanProposal propose(
            TrainingAnalysisContext context,
            WorkoutAnalysis analysis) {
        return proposeInternal(context, analysis);
    }

    private TrainingPlanProposal proposeInternal(
            TrainingAnalysisContext context,
            WorkoutAnalysis analysis) {
        ChatClient chatClient = chatClientBuilder.build();
        TrainingState state = trainingStateCalculator.calculate(context);

        var options = OpenAiChatOptions.builder()
                .maxTokens(1200)
                .build();

        String systemPrompt = """
                Ты — планировщик силовых тренировок.

                Прими решение по следующей тренировке на основании программы,
                цели, истории этого дня, фактически выполненной тренировки,
                WorkoutAnalysis и вычисленного TrainingState.

                Если WorkoutAnalysis отсутствует, это означает, что план строится
                ДО начала тренировки. В этом случае actual sets текущей тренировки
                могут быть пустыми; опирайся на план, историю и TrainingState.

                TrainingState — детерминированный источник числовых фактов.
                Не заменяй его догадками.

                Для каждого упражнения верни только:
                - order;
                - action: KEEP, PROGRESS, REGRESS, CHANGE_REPS, CHANGE_VOLUME или DELOAD;
                - weightKg, repsMin, repsMax, setCount — только если action не KEEP.

                Поля изменения описывают общую новую схему упражнения:
                - weightKg — новый вес для рабочих подходов; null означает сохранить текущие веса;
                - repsMin/repsMax — новый диапазон повторений; null означает сохранить текущий диапазон;
                - setCount — новое количество подходов; null означает сохранить количество подходов.

                Правила принятия решения:
                - KEEP: текущий план уже адекватен, изменений не требуется.
                - PROGRESS: есть достаточные основания для увеличения тренировочного стимула.
                - REGRESS: текущая нагрузка чрезмерна или выполнение ухудшилось.
                - CHANGE_REPS: меняй диапазон повторений без необходимости резко менять вес.
                - CHANGE_VOLUME: меняй количество подходов, если это оправдано состоянием тренинга.
                - DELOAD: снижай тренировочный стресс при необходимости восстановления или в запланированную разгрузочную неделю.
                - Не увеличивай вес или объём автоматически после каждой тренировки.
                - Прогрессия должна учитывать одновременно выполненные повторения, вес, объём,
                  тренд упражнения, общую динамику тренировки и фазу блока.
                - Учитывай currentWeek, plannedWeeks, deloadWeek, blockGoal и blockPhase.
                - Если данных недостаточно для уверенного изменения, используй KEEP.
                - Не меняй порядок, упражнения и варианты программы.
                - Не выдумывай упражнения или отсутствующие факты.
                - Для mobility всегда используй KEEP, если нет явной причины изменить количество подходов.
                - Для mobility не задавай weightKg, repsMin или repsMax.
                - Верни ровно по одному решению на каждое упражнение программы.
                - Для KEEP все поля изменения должны быть null.
                - Для DELOAD допустимо одновременно снизить вес, повторения и/или количество подходов.
                - generalNotes: не более 2 коротких предложений.
                - Верни полный валидный JSON TrainingPlanDecision.
                """;

        String userPrompt = """
                ПРОГРАММА И ТЕКУЩАЯ ТРЕНИРОВКА:
                %s

                WORKOUT ANALYSIS:
                %s

                TRAINING STATE:
                %s
                """.formatted(context, analysis == null ? "нет — тренировка ещё не выполнена" : analysis, state);

        TrainingPlanDecision decision = chatClient.prompt()
                .options(options)
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(TrainingPlanDecision.class);

        return mergeDecision(context, decision);
    }

    private TrainingPlanProposal mergeDecision(
            TrainingAnalysisContext context,
            TrainingPlanDecision decision) {
        Map<Integer, TrainingAnalysisContext.ExerciseContext> programExercises =
                context.exercises().stream()
                        .collect(Collectors.toMap(
                                TrainingAnalysisContext.ExerciseContext::order,
                                Function.identity()));

        List<TrainingPlanProposal.ExerciseProposal> exercises =
                decision.exercises().stream()
                        .map(item -> {
                            TrainingAnalysisContext.ExerciseContext source = programExercises.get(item.order());
                            if (source == null) {
                                throw new IllegalArgumentException(
                                        "AI returned unknown exercise order: " + item.order());
                            }

                            boolean mobility = source.plannedSets().stream()
                                    .anyMatch(set -> "MOBILITY".equalsIgnoreCase(set.loadMode()))
                                    || source.actualSets().stream()
                                    .anyMatch(set -> "MOBILITY".equalsIgnoreCase(set.loadMode()));

                            if (mobility) {
                                List<TrainingPlanProposal.SetProposal> sets = source.plannedSets().stream()
                                        .map(set -> new TrainingPlanProposal.SetProposal(
                                                set.setNumber(), null, null, null, "MOBILITY"))
                                        .toList();
                                return new TrainingPlanProposal.ExerciseProposal(
                                        source.order(), source.name(), source.variant(), sets);
                            }

                            List<TrainingPlanProposal.SetProposal> sets = buildSets(context, source, item);
                            return new TrainingPlanProposal.ExerciseProposal(
                                    source.order(), source.name(), source.variant(), sets);
                        })
                        .toList();

        if (exercises.size() != context.exercises().size()) {
            throw new IllegalArgumentException(
                    "AI returned " + exercises.size()
                            + " exercises, expected " + context.exercises().size());
        }

        return new TrainingPlanProposal(exercises, decision.generalNotes());
    }

    private List<TrainingPlanProposal.SetProposal> buildSets(
            TrainingAnalysisContext context,
            TrainingAnalysisContext.ExerciseContext source,
            TrainingPlanDecision.ExerciseDecision decision) {
        if ("KEEP".equalsIgnoreCase(decision.action())) {
            return source.plannedSets().stream()
                    .map(set -> new TrainingPlanProposal.SetProposal(
                            set.setNumber(),
                            effectiveWeight(context, source, set),
                            effectiveRepsMin(context, source, set),
                            effectiveRepsMax(context, source, set),
                            set.loadMode()))
                    .toList();
        }

        int originalSetCount = source.plannedSets().size();
        int setCount = decision.setCount() != null ? decision.setCount() : originalSetCount;
        if (setCount <= 0) {
            throw new IllegalArgumentException(
                    "AI returned invalid setCount for exercise order: " + source.order());
        }

        var result = new ArrayList<TrainingPlanProposal.SetProposal>(setCount);
        for (int i = 0; i < setCount; i++) {
            TrainingAnalysisContext.PlannedSetContext sourceSet = source.plannedSets().get(
                    Math.min(i, originalSetCount - 1));

            result.add(new TrainingPlanProposal.SetProposal(
                    i + 1,
                    decision.weightKg() != null
                            ? decision.weightKg()
                            : effectiveWeight(context, source, sourceSet),
                    decision.repsMin() != null
                            ? decision.repsMin()
                            : effectiveRepsMin(context, source, sourceSet),
                    decision.repsMax() != null
                            ? decision.repsMax()
                            : effectiveRepsMax(context, source, sourceSet),
                    sourceSet.loadMode()));
        }
        return result;
    }

    private java.math.BigDecimal effectiveWeight(
            TrainingAnalysisContext context,
            TrainingAnalysisContext.ExerciseContext source,
            TrainingAnalysisContext.PlannedSetContext plannedSet) {
        if (plannedSet.weightKg() != null) {
            return plannedSet.weightKg();
        }
        return findHistoricalSet(context, source, plannedSet.setNumber())
                .map(TrainingAnalysisContext.ActualSetContext::weightKg)
                .orElse(null);
    }

    private Integer effectiveRepsMin(
            TrainingAnalysisContext context,
            TrainingAnalysisContext.ExerciseContext source,
            TrainingAnalysisContext.PlannedSetContext plannedSet) {
        if (plannedSet.repsMin() != null) {
            return plannedSet.repsMin();
        }
        return findHistoricalSet(context, source, plannedSet.setNumber())
                .map(TrainingAnalysisContext.ActualSetContext::actualReps)
                .orElse(null);
    }

    private Integer effectiveRepsMax(
            TrainingAnalysisContext context,
            TrainingAnalysisContext.ExerciseContext source,
            TrainingAnalysisContext.PlannedSetContext plannedSet) {
        if (plannedSet.repsMax() != null) {
            return plannedSet.repsMax();
        }
        return findHistoricalSet(context, source, plannedSet.setNumber())
                .map(TrainingAnalysisContext.ActualSetContext::actualReps)
                .orElse(null);
    }

    private java.util.Optional<TrainingAnalysisContext.ActualSetContext> findHistoricalSet(
            TrainingAnalysisContext context,
            TrainingAnalysisContext.ExerciseContext source,
            Integer setNumber) {
        return context.history().stream()
                .filter(workout -> workout.exercises().stream().anyMatch(exercise ->
                        java.util.Objects.equals(exercise.order(), source.order())
                                && java.util.Objects.equals(exercise.name(), source.name())))
                .findFirst()
                .flatMap(workout -> workout.exercises().stream()
                        .filter(exercise -> java.util.Objects.equals(exercise.order(), source.order())
                                && java.util.Objects.equals(exercise.name(), source.name()))
                        .findFirst())
                .flatMap(exercise -> exercise.actualSets().stream()
                        .filter(set -> java.util.Objects.equals(set.setNumber(), setNumber))
                        .findFirst());
    }
}
