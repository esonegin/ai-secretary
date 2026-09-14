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

                Для каждого упражнения верни:
                - order;
                - action: KEEP, PROGRESS, REGRESS, CHANGE_REPS, CHANGE_VOLUME или DELOAD;
                - sets только если action не KEEP;
                - для каждого подхода: setNumber, weightKg, repsMin, repsMax, loadMode.

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
                - Для силовых подходов используй loadMode=TOTAL.
                - Для mobility используй loadMode=MOBILITY и null для weightKg/repsMin/repsMax.
                - Верни ровно по одному решению на каждое упражнение программы.
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

                            List<TrainingPlanProposal.SetProposal> sets;
                            if (mobility) {
                                sets = source.plannedSets().stream()
                                        .map(set -> new TrainingPlanProposal.SetProposal(
                                                set.setNumber(), null, null, null, "MOBILITY"))
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
                                if (item.sets() == null || item.sets().isEmpty()) {
                                    throw new IllegalArgumentException(
                                            "AI returned " + item.action()
                                                    + " without sets for exercise order: " + item.order());
                                }

                                sets = item.sets().stream()
                                        .map(set -> new TrainingPlanProposal.SetProposal(
                                                set.setNumber(),
                                                set.weightKg(),
                                                set.repsMin(),
                                                set.repsMax(),
                                                set.loadMode()))
                                        .toList();
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
