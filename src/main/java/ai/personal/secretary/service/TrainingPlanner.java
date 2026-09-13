package ai.personal.secretary.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrainingPlanner {

    private final ChatClient.Builder chatClientBuilder;

    public TrainingPlanProposal propose(
            TrainingAnalysisContext context,
            WorkoutAnalysis analysis) {
        ChatClient chatClient = chatClientBuilder.build();

        var options = OpenAiChatOptions.builder()
                .maxTokens(3000)
                .build();

        return chatClient.prompt()
                .options(options)
                .system("""
                        Ты — планировщик силовых тренировок.

                        Составь предложение следующей тренировки на основании:
                        - активной программы;
                        - цели пользователя;
                        - истории предыдущих тренировок этого же дня;
                        - фактически выполненной тренировки;
                        - WorkoutAnalysis.

                        Верни TrainingPlanProposal.

                        Для каждого упражнения верни только:
                        - order;
                        - name;
                        - variant;
                        - sets.

                        Для каждого силового подхода:
                        - setNumber;
                        - weightKg;
                        - repsMin;
                        - repsMax;
                        - loadMode = TOTAL.

                        Для mobility-упражнений используй loadMode = MOBILITY,
                        а weightKg, repsMin и repsMax = null.

                        generalNotes должен содержать не более 2 коротких предложений.
                        rationale для каждого упражнения — не более одной короткой фразы.

                        Правила:
                        - Сохраняй порядок, упражнения и варианты активной программы.
                        - Используй историю и WorkoutAnalysis для выбора нагрузки.
                        - Учитывай одновременно вес и повторения.
                        - Не делай резких изменений без достаточного основания.
                        - При недостатке данных сохраняй последнюю подтверждённую нагрузку.
                        - Не выдумывай данные.
                        - Верни все упражнения программы.
                        - Верни полный и валидный JSON TrainingPlanProposal.
                        """)
                .user("""
                        Данные для планирования:

                        %s

                        Анализ:
                        %s
                        """.formatted(context, analysis))
                .call()
                .entity(TrainingPlanProposal.class);
    }
}
