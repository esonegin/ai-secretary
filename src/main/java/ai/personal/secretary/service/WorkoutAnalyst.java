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

                        Твоя задача — анализировать уже выполненную тренировку
                        в контексте предыдущих тренировок того же тренировочного дня.

                        Не составляй следующую тренировку.
                        Не назначай конкретные веса или количество повторений.
                        Не изменяй программу напрямую.

                        Для каждого упражнения верни:
                        - order — номер упражнения;
                        - name — название упражнения;
                        - trend — одно из значений:
                          POSITIVE, STABLE, PLATEAU, NEGATIVE, INSUFFICIENT_DATA;
                        - confidence — одно из значений:
                          HIGH, MEDIUM, LOW;
                        - note — краткое объяснение вывода на основании данных.

                        Также верни generalObservations — только общие наблюдения,
                        которые относятся ко всей тренировке.

                        Правила:
                        - История содержит предыдущие тренировки того же dayType.
                        - Для определения тренда сравнивай текущую тренировку
                          прежде всего с историей, а не подходы между собой.
                        - Не называй прогрессом увеличение повторений между подходами
                          одной и той же тренировки.
                        - Учитывай одновременно вес и повторения.
                        - Если история отсутствует или недостаточна для вывода,
                          используй INSUFFICIENT_DATA.
                        - STABLE означает отсутствие существенного изменения.
                        - PLATEAU используй, если результаты длительно остаются
                          примерно на одном уровне и данных истории для этого достаточно.
                        - NEGATIVE означает устойчивое ухудшение относительно истории,
                          а не просто падение повторений внутри одной тренировки.
                        - Не выдумывай данные.
                        - Не превращай предположение в факт.
                        - В note не давай конкретного назначения следующей нагрузки.

                        Верни только структурированный результат:
                        exercises и generalObservations.
                        """)
                .user("""
                        Проанализируй тренировку и её историю:

                        %s
                        """.formatted(context))
                .call()
                .entity(WorkoutAnalysis.class);
    }
}
