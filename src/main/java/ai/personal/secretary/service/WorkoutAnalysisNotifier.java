package ai.personal.secretary.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkoutAnalysisNotifier {

    private final TrainingAnalysisContextBuilder contextBuilder;
    private final WorkoutAnalyst workoutAnalyst;

    @Value("${telegram.bot.owner-chat-id:0}")
    private Long ownerChatId;

    @Value("${telegram.bot.token}")
    private String botToken;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkoutRecorded(WorkoutRecordedEvent event) {
        if (ownerChatId == null || ownerChatId == 0) {
            return;
        }

        try {
            var context = contextBuilder.build(
                    event.userId(),
                    event.workoutDate(),
                    event.dayType());

            var analysis = workoutAnalyst.analyze(context);
            send(format(event, analysis));
        } catch (Exception e) {
            log.error("Failed to analyze completed workout {} {}: {}",
                    event.workoutDate(), event.dayType(), e.getMessage(), e);
        }
    }

    private String format(WorkoutRecordedEvent event, WorkoutAnalysis analysis) {
        var response = new StringBuilder()
                .append("📊 Оценка тренировки ")
                .append(event.workoutDate().toString())
                .append("\n")
                .append("💪 ")
                .append(event.dayType())
                .append("\n\n");

        if (analysis.exercises() != null) {
            for (var exercise : analysis.exercises()) {
                response.append(exercise.order())
                        .append(". ")
                        .append(exercise.name())
                        .append(" — ")
                        .append(exercise.trend());

                if (exercise.note() != null && !exercise.note().isBlank()) {
                    response.append("\n   ")
                            .append(exercise.note());
                }
                response.append("\n\n");
            }
        }

        if (analysis.generalObservations() != null && !analysis.generalObservations().isEmpty()) {
            response.append("📝 Итог\n")
                    .append(String.join(" ", analysis.generalObservations()));
        }

        return response.toString();
    }

    private void send(String text) throws TelegramApiException {
        TelegramClient client = new OkHttpTelegramClient(botToken);
        client.execute(SendMessage.builder()
                .chatId(ownerChatId)
                .text(text)
                .build());
    }
}
