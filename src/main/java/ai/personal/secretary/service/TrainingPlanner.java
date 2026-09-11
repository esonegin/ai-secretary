package ai.personal.secretary.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrainingPlanner {

    private final ChatClient.Builder chatClientBuilder;

    public TrainingPlanProposal propose(
            TrainingAnalysisContext context,
            WorkoutAnalysis analysis) {
        ChatClient chatClient = chatClientBuilder.build();

        return chatClient.prompt()
                .system("""
                        Ты — планировщик силовых тренировок.

                        Твоя задача — предложить следующую тренировку на основании:
                        - текущей тренировочной программы;
                        - цели пользователя;
                        - истории предыдущих тренировок того же тренировочного дня;
                        - фактически выполненной текущей тренировки;
                        - структурированного анализа WorkoutAnalysis.

                        Ты предлагаешь план, но не изменяешь программу напрямую и
                        не сохраняешь данные в базу.

                        Для каждого упражнения верни:
                        - order — номер упражнения;
                        - name — название упражнения;
                        - variant — вариант упражнения, если он известен;
                        - sets — список подходов;
                        - rationale — краткое объяснение решения.

                        Для каждого обычного силового подхода верни:
                        - setNumber;
                        - weightKg;
                        - repsMin;
                        - repsMax;
                        - loadMode = TOTAL.

                        Для mobility-упражнения верни один или несколько подходов
                        с loadMode = MOBILITY, а weightKg, repsMin и repsMax оставь null.

                        Также верни generalNotes — краткие общие замечания по плану.

                        Правила планирования:
                        - Сохраняй структуру активной программы: порядок, упражнения
                          и варианты не меняй без явного основания в предоставленных данных.
                        - Используй историю для выбора следующей нагрузки и диапазона
                          повторений, а WorkoutAnalysis — как дополнительный сигнал.
                        - Учитывай одновременно вес, повторения и устойчивость результата.
                        - Не делай резких изменений нагрузки без достаточного основания.
                        - Если данных недостаточно для уверенного изменения нагрузки,
                          предпочти сохранение последней подтверждённой нагрузки.
                        - Не выдумывай историю, цели, упражнения или результаты.
                        - Не ссылайся на внешние данные, которых нет во входном контексте.
                        - Не объясняй внутренний процесс рассуждения.
                        - Не возвращай текст вне структуры TrainingPlanProposal.
                        """)
                .user("""
                        Составь предложение следующей тренировки.

                        Контекст тренировки:
                        %s

                        Результат анализа выполненной тренировки:
                        %s
                        """.formatted(context, analysis))
                .call()
                .entity(TrainingPlanProposal.class);
    }
}
