package ai.personal.secretary.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingPlanner {

    private final ChatClient.Builder chatClientBuilder;
    private final TrainingStateCalculator trainingStateCalculator;

    public TrainingPlanProposal propose(
            TrainingAnalysisContext context,
            WorkoutAnalysis analysis) {
        ChatClient chatClient = chatClientBuilder.build();
        TrainingState state = trainingStateCalculator.calculate(context);

        var options = OpenAiChatOptions.builder()
                .maxTokens(1200)
                .build();

        TrainingPlanDecision decision = chatClient.prompt()
                .options(options)
                .system("""
                        Ты — планировщик силовых тренировок.

                        Прими решение по следующей тренировке на основании программы,
                        цели, истории этого дня, фактически выполненной тренировки,
                        WorkoutAnalysis и вычисленного TrainingState.

                        TrainingState — это детерминированно рассчитанное состояние тренинга.
                        Используй его как источник числовых фактов и не переопределяй его своими догадками.

                        Верни TrainingPlanDecision.
                        Для каждого упражнения программы верни только:
                        - order;
                        - action = KEEP или CHANGE;
                        - sets только если action = CHANGE.

                        Для CHANGE верни полный набор подходов упражнения.
                        Силовой подход: setNumber, weightKg, repsMin, repsMax, loadMode=TOTAL.
                        Mobility: setNumber, weightKg=null, repsMin=null, repsMax=null, loadMode=MOBILITY.

                        Правила:
                        - Не меняй порядок, упражнения и варианты программы.
                        - KEEP означает оставить текущие запланированные подходы без изменений.
                        - CHANGE используй только при обоснованной необходимости по TrainingState,
                          истории и WorkoutAnalysis.
                        - Учитывай одновременно вес, повторения, объём и фазу тренировочного блока.
                        - Не увеличивай нагрузку автоматически после каждой тренировки.
                        - Учитывай длительность блока и текущую неделю при решении о прогрессии.
                        - Не делай резких изменений без достаточного основания.
                        - При недостатке данных используй KEEP.
                        - Не выдумывай упражнения и данные.
                        - Верни ровно по одному решению на каждое упражнение программы.
                        - generalNotes: не более 2 коротких предложений.
                        - Верни полный валидный JSON TrainingPlanDecision.
                        """)
                .user("""
                        ПРОГРАММА И ТЕКУЩАЯ ТРЕНИРОВКА:
                        %s

                        WORKOUT ANALYSIS:
                        %s

                        TRAINING STATE:
                        %s
                        """.formatted(context, analysis, state))
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

                            boolean mobility = source.actualSets().stream()
                                    .anyMatch(set -> "MOBILITY".equalsIgnoreCase(set.loadMode()));

                            List<TrainingPlanProposal.SetProposal> sets;
                            if (mobility) {
                                sets = source.plannedSets().stream()
                                        .map(set -> new TrainingPlanProposal.SetProposal(
                                                set.setNumber(),
                                                null,
                                                null,
                                                null,
                                                "MOBILITY"))
                                        .toList();
                            } else if ("CHANGE".equalsIgnoreCase(item.action())) {
                                if (item.sets() == null || item.sets().isEmpty()) {
                                    throw new IllegalArgumentException(
                                            "AI returned CHANGE without sets for exercise order: " + item.order());
                                }

                                sets = item.sets().stream()
                                        .map(set -> new TrainingPlanProposal.SetProposal(
                                                set.setNumber(),
                                                set.weightKg(),
                                                set.repsMin(),
                                                set.repsMax(),
                                                set.loadMode()))
                                        .toList();
                            } else if ("KEEP".equalsIgnoreCase(item.action())) {
                                sets = source.plannedSets().stream()
                                        .map(set -> new TrainingPlanProposal.SetProposal(
                                                set.setNumber(),
                                                set.weightKg(),
                                                set.repsMin(),
                                                set.repsMax(),
                                                set.loadMode()))
                                        .toList();
                            } else {
                                throw new IllegalArgumentException(
                                        "AI returned unsupported action: " + item.action());
                            }

                            return new TrainingPlanProposal.ExerciseProposal(
                                    source.order(),
                                    source.name(),
                                    source.variant(),
                                    sets);
                        })
                        .toList();

        if (exercises.size() != context.exercises().size()) {
            throw new IllegalArgumentException(
                    "AI returned " + exercises.size()
                            + " exercises, expected " + context.exercises().size());
        }

        return new TrainingPlanProposal(exercises, decision.generalNotes());
    }
}
