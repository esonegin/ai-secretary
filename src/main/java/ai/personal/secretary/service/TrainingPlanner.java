package ai.personal.secretary.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrainingPlanner {

    private final ChatClient.Builder chatClientBuilder;

    public TrainingPlanProposal propose(
            TrainingAnalysisContext context,
            WorkoutAnalysis analysis) {
        ChatClient chatClient = chatClientBuilder.build();

        var options = OpenAiChatOptions.builder()
                .maxTokens(1500)
                .build();

        String contextText = context.toString();
        String analysisText = analysis.toString();
        String userPrompt = """
                Данные для планирования:

                %s

                Анализ:
                %s
                """.formatted(contextText, analysisText);

        log.info(
                "TrainingPlanner prompt sizes: context={} chars, analysis={} chars, user={} chars",
                contextText.length(),
                analysisText.length(),
                userPrompt.length()
        );

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

                        Для каждого упражнения верни:
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
                .user(userPrompt)
                .call()
                .entity(TrainingPlanProposal.class);
    }
}
