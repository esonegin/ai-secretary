# AI Personal Coach 🧠

Персональный AI-коуч в Telegram на базе **Anthropic Claude** + **Spring Boot 3.3.5** + **pgvector**.

---

## Стек

| Что             | Версия / технология                          |
|-----------------|----------------------------------------------|
| Java            | 21 (Temurin — не Oracle JDK)                 |
| Spring Boot     | 3.3.5                                        |
| Spring AI       | 1.0.0 GA (Maven Central, без доп. репо)      |
| Telegram Bot    | telegrambots 7.10.0 (SpringLongPollingBot)   |
| БД              | PostgreSQL 16 + pgvector                     |
| Сборка          | Maven (mvnw в репозитории)                   |

---

## Подготовка (macOS Catalina / IntelliJ 2023.3.x)

### 1. Java 21 через Temurin (не Oracle JDK)
```bash
# Homebrew
brew install --cask temurin@21

# Проверка
java -version
# должно быть: openjdk version "21..."
```

В IntelliJ: `File → Project Structure → SDK` → добавить Temurin 21 из `/Library/Java/JavaVirtualMachines/temurin-21.jdk`.

### 2. Docker Desktop для PostgreSQL
```bash
# Запустить PostgreSQL с pgvector и авто-инициализацией схемы
docker-compose up -d

# Проверить что схема создалась
docker exec ai-postgres psql -U postgres -d ai_secretary -c "\dt"
```

### 3. Создать Telegram-бота
1. Написать `@BotFather` → `/newbot`
2. Получить токен (`bot_token`)
3. Узнать свой `chat_id`: написать боту `/start`, токен появится в логах или через `@userinfobot`

### 4. Переменные окружения
```bash
export ANTHROPIC_API_KEY=sk-ant-...
export TELEGRAM_BOT_TOKEN=1234567890:AAF...
export TELEGRAM_BOT_USERNAME=my_coach_bot
export TELEGRAM_OWNER_CHAT_ID=123456789   # твой chatId
```

В IntelliJ: `Run → Edit Configurations → Environment variables` → добавить те же переменные.

### 5. Запуск
```bash
./mvnw spring-boot:run
```

---

## Команды бота

| Команда             | Действие                                    |
|---------------------|---------------------------------------------|
| `/start`            | Приветствие                                 |
| `/domains`          | Выбрать направление (инлайн-кнопки)        |
| `/free`             | Авто-режим — коуч определяет тему сам      |
| `/goals`            | Посмотреть активные цели                    |
| `/log sport текст`  | Записать активность вручную                 |
| `/summary`          | Итоги недели от мета-коуча                  |
| `/help`             | Справка                                     |
| _любой текст_       | Свободный диалог с авто-роутингом           |

---

## Направления (домены)

Создаются автоматически при первом `docker-compose up`:

`sport` 💪 · `nutrition` 🥗 · `reading` 📚 · `cinema` 🎬 · `music` 🎵 · `relationships` ❤️ · `work` 💼 · `yoga` 🧘

### Добавить новое направление
```bash
curl -X POST http://localhost:8080/api/coach/1/domains \
  -H "Content-Type: application/json" \
  -d '{
    "slug": "meditation",
    "name": "Медитация",
    "icon": "🧘",
    "systemPrompt": "Ты — коуч по медитации. Помогаешь выстроить регулярную практику..."
  }'
```

---

## Автоматические check-in

| Время         | Что происходит                          |
|---------------|-----------------------------------------|
| 09:00 каждый день | Утренний check-in — планы на день  |
| 21:00 каждый день | Вечерний итог                       |
| 20:00 воскресенье | Еженедельный анализ                 |

Расписание настраивается в `application.yml` (`coach.checkin-*-cron`).

---

## Структура проекта

```
src/main/java/ai/personal/secretary/
├── AiSecretaryApplication.java
├── bot/
│   └── CoachBot.java              ← Telegram-бот, все команды и диалог
├── config/
│   └── AiConfig.java              ← ChatClient bean
├── model/                         ← JPA entities
│   ├── UserProfile.java
│   ├── Domain.java
│   ├── DomainGoal.java
│   ├── ActivityLog.java
│   └── ChatMessage.java           ← enum MessageRole { USER, ASSISTANT }
├── repository/                    ← Spring Data JPA
│   ├── UserProfileRepository.java
│   ├── DomainRepository.java
│   ├── DomainGoalRepository.java
│   ├── ActivityLogRepository.java
│   └── ChatMessageRepository.java
├── service/
│   ├── CoachService.java          ← основная логика + Claude API
│   ├── DomainRouterService.java   ← определяет домен по тексту
│   ├── DomainService.java         ← CRUD доменов и целей
│   └── ActivityService.java       ← запись активностей
└── scheduler/
    └── CheckInScheduler.java      ← утро / вечер / неделя
```

---

## Диагностика

**Бот не отвечает** → проверить `TELEGRAM_BOT_TOKEN` и что приложение запущено.

**`owner-chat-id: 0`** → check-in не работает. Узнай chatId через `@userinfobot` и задай `TELEGRAM_OWNER_CHAT_ID`.

**pgvector not found** → убедись что запущен `docker-compose up -d` с образом `pgvector/pgvector:pg16`.

**Spring AI dependency not found** → убедись что используется `./mvnw`, а не системный Maven. Версия 1.0.0 в Maven Central.
