package ai.personal.secretary.service;

import ai.personal.secretary.model.Domain;
import ai.personal.secretary.repository.DomainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Сервис публикации конспектов и эссе в Telegram-канал.
 *
 * Типы публикаций:
 *  - книга:   ключевые идеи + цитаты + личный вывод
 *  - фильм:   тема + режиссёрские решения + резонанс
 *  - музыка:  стиль + настроение + рекомендация
 *  - мысль:   свободное эссе на любую тему
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublishService {

    private final OpenAiChatModel chatModel;
    private final DomainRepository domainRepository;

    @Value("${coach.channel-id:-1003786886469}")
    private String channelId;

    /**
     * Генерирует пост без публикации — для предварительного просмотра.
     *
     * @param type    "sport"/"nutrition"/"reading"/"cinema"/"music" или null
     * @param content описание от пользователя
     */
    public String generatePost(String type, String content) {
        // Маппинг slug → читаемый тип
        String localType = switch (type != null ? type.toLowerCase() : "") {
            case "reading"       -> "книга";
            case "cinema"        -> "фильм";
            case "music"         -> "музыка";
            case "relationships" -> "мысль";
            case "work"          -> "мысль";
            default              -> "мысль";
        };
        return generatePost(localType, content, true);
    }

    /**
     * Генерирует и публикует пост в канал.
     */
    public String generateAndPublish(String type, String content,
                                     TelegramClient telegramClient) {
        String post = generatePost(type, content, false);
        publishToChannel(post, telegramClient);
        return post;
    }

    private String generatePost(String type, String content, boolean previewOnly) {
        String prompt = switch (type.toLowerCase()) {
            case "книга" -> String.format("""
                Напиши конспект-эссе для Telegram-канала о книге.
                Информация от читателя: %s
                
                Структура поста:
                📚 *Название книги* — автор
                
                Одно-два предложения о чём книга.
                
                *Ключевые идеи:*
                • идея 1
                • идея 2
                • идея 3
                
                *Что резонирует лично:*
                2-3 предложения от первого лица о личном впечатлении.
                
                *Кому читать:*
                Одно предложение.
                
                Пиши живо, без академизма. Только Markdown для Telegram.
                Длина: 200-300 слов.
                """, content);

            case "фильм" -> String.format("""
                Напиши рецензию-эссе для Telegram-канала о фильме.
                Информация от зрителя: %s
                
                Структура поста:
                🎬 *Название фильма* (год) — режиссёр
                
                Одно предложение — жанр и атмосфера.
                
                *О чём на самом деле:*
                2-3 предложения о главной теме (не пересказ сюжета).
                
                *Что запомнилось:*
                Конкретная сцена, решение, деталь — и почему.
                
                *Личный вывод:*
                2 предложения от первого лица.
                
                Пиши живо, без спойлеров сюжета. Только Markdown для Telegram.
                Длина: 200-300 слов.
                """, content);

            case "музыка" -> String.format("""
                Напиши пост для Telegram-канала о музыке/треке/альбоме.
                Информация: %s
                
                Структура поста:
                🎵 *Название* — исполнитель
                
                Одно предложение — жанр и настроение.
                
                *Что внутри:*
                2-3 предложения о звучании, атмосфере, особенностях.
                
                *Когда слушать:*
                Одно предложение — контекст, настроение, время суток.
                
                *Личный резонанс:*
                2 предложения от первого лица.
                
                Пиши образно и ёмко. Только Markdown для Telegram.
                Длина: 150-250 слов.
                """, content);

            default -> String.format("""
                Напиши эссе-размышление для Telegram-канала.
                Тема и мысли автора: %s
                
                Структура поста:
                💭 *Заголовок — цепляющий, ёмкий*
                
                Основная мысль — 3-4 абзаца, живо и лично.
                Пиши от первого лица, с конкретными примерами.
                Заверши открытым вопросом или выводом.
                
                Только Markdown для Telegram. Длина: 250-350 слов.
                """, content);
        };

        String result = ChatClient.builder(chatModel).build()
                .prompt(prompt)
                .call()
                .content();

        log.info("Post generated: type={} length={}", type, result.length());
        return result;
    }

    private void publishToChannel(String text, TelegramClient telegramClient) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(channelId)
                    .text(text)
                    .parseMode("Markdown")
                    .build());
            log.info("Published to channel {}", channelId);
        } catch (TelegramApiException e) {
            log.error("Failed to publish to channel: {}", e.getMessage());
            throw new RuntimeException("Не удалось опубликовать в канал: " + e.getMessage());
        }
    }
}