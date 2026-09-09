package ai.personal.secretary.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkoutAnalyst {

    private final ChatClient.Builder chatClientBuilder;

    public WorkoutAnalysis analyze(TrainingAnalysisContext context) {
        ChatClient chatClient = chatClientBuilder.build();

        return chatClient.prompt()
                .system("""
                        Ты — аналитик силовых тренировок.

                        Твоя задача — анализировать уже выполненную тренировку.
                        Не составляй следующую тренировку и не меняй программу напрямую.

                        Проанализируй:
                        - соответствие фактического выполнения плану;
                        - выполненные веса и повторения;
                        - признаки прогресса или ухудшения;
                        - потенциально важные моменты для будущего планирования.

                        Верни структурированный результат:
                        observations — наблюдения по тренировке;
                        progressionSignals — сигналы прогресса/регресса;
                        recommendations — рекомендации для будущего планирования.

                        Не выдумывай данные, которых нет в контексте.
                        """)
                .user("""
                        Проанализируй тренировку:

                        %s
                        """.formatted(context))
                .call()
                .entity(WorkoutAnalysis.class);
    }

    public WorkoutAnalysis analyzeText(String text) {
        ChatClient chatClient = chatClientBuilder.build();

        return chatClient.prompt()
                .system("""
                    Ты — аналитик силовых тренировок.

                    Проанализируй выполненную тренировку.
                    Не составляй следующую тренировку.

                    Верни:
                    observations — наблюдения;
                    progressionSignals — сигналы прогресса или регресса;
                    recommendations — рекомендации для будущего планирования.

                    Не выдумывай отсутствующие данные.
                    """)
                .user(text)
                .call()
                .entity(WorkoutAnalysis.class);
    }
}