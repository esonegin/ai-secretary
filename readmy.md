# AI Personal Coach 🧠

Персональный AI-коуч на базе **Anthropic Claude** + **Spring Boot 3** + **pgvector**.

Следит за несколькими направлениями твоей жизни одновременно. Каждое направление — отдельный коуч со своим характером, памятью и целями. Новые направления добавляются через API без изменения кода.

---

## Стек

| Компонент    | Технология                        |
|--------------|-----------------------------------|
| AI-модель    | Anthropic Claude Sonnet 4.6       |
| Framework    | Spring Boot 3.3.5 + Spring AI 1.0 |
| База данных  | PostgreSQL 16 + pgvector          |
| Язык         | Java 21                           |

---

## Быстрый старт

### 1. Запусти PostgreSQL (с автоматической инициализацией)

```bash
docker-compose up -d
```

`init.sql` запустится автоматически и создаст схему + 8 стартовых доменов.

### 2. Установи API ключ Anthropic

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

Получить ключ: https://console.anthropic.com

### 3. Запусти приложение

```bash
./mvnw spring-boot:run
```

### 4. Проверь

```bash
# Посмотри список доменов
curl http://localhost:8080/api/coach/1/domains

# Поговори с коучем (роутер определит домен сам)
curl -X POST http://localhost:8080/api/coach/1/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "сегодня сделал пробежку 5км, чувствую себя отлично"}'
```

---

## Архитектура

```
Сообщение пользователя
        │
        ▼
  DomainRouterService          ← Claude определяет домен (sport/yoga/meta/...)
        │
        ▼
  CoachService.chat()
        │
        ├── buildSystemPrompt()
        │       ├── domain.systemPrompt    (из БД — меняется без рекомпиляции)
        │       ├── активные цели домена
        │       └── последние 7 активностей
        │
        ├── buildHistory()                 (последние 30 сообщений домена)
        │
        └── ChatClient → Anthropic API → ответ
                │
                └── сохраняем в chat_messages (domain_id привязан)
```

### Почему разделение по доменам критично

Без разделения коуч путается: спортивный контекст мешает разговору об отношениях,
история о книгах забивает контекст разговора о питании.

С разделением: каждый коуч видит только **своё** — свои цели, свою историю, свой стиль общения.

---

## API Reference

### Пользователи

```bash
# Создать профиль
POST /api/users
{
  "name": "Евгений",
  "age": 35,
  "timezone": "Europe/Amsterdam",
  "activityLevel": "moderate",
  "healthNotes": "нет ограничений"
}

# Получить профиль
GET /api/users/1
```

### Чат с коучем

```bash
# Свободный диалог — роутер сам определяет домен
POST /api/coach/{userId}/chat
{ "message": "сегодня не мог сосредоточиться на работе" }

# Диалог с конкретным коучем (без роутинга)
POST /api/coach/{userId}/chat/{domainSlug}
{ "message": "как правильно увеличивать рабочий вес?" }

# Стриминг (SSE) — токен за токеном
POST /api/coach/{userId}/chat/stream
{ "message": "расскажи про технику дыхания в беге" }

# История диалога
GET /api/coach/{userId}/history?domainId=1
```

### Управление доменами

```bash
# Список активных доменов
GET /api/coach/{userId}/domains

# Добавить новый домен (без изменения кода!)
POST /api/coach/{userId}/domains
{
  "slug": "meditation",
  "name": "Медитация",
  "icon": "🧘",
  "description": "Практика осознанности и медитации",
  "systemPrompt": "Ты — коуч по медитации. Помогаешь выстроить регулярную практику..."
}

# Обновить системный промпт коуча
PUT /api/coach/{userId}/domains/{domainId}/prompt
{ "systemPrompt": "Новый характер коуча..." }
```

### Цели

```bash
# Добавить цель в домен
POST /api/coach/{userId}/domains/{domainId}/goals
{
  "title": "Пробежать 10км без остановки",
  "targetDate": "2025-06-01"
}

# Отметить цель выполненной
PATCH /api/coach/{userId}/goals/{goalId}/achieve

# Список целей домена
GET /api/coach/{userId}/domains/{domainId}/goals
```

### Активность

```bash
# Залогировать активность
POST /api/coach/{userId}/domains/{domainSlug}/activity
{
  "summary": "Утренняя пробежка",
  "details": "5.2 км за 28 минут, темп 5:23/км, пульс средний 148",
  "moodScore": 5,
  "energyScore": 4
}

# Последние активности домена
GET /api/coach/{userId}/domains/{domainSlug}/activity?limit=10

# Еженедельный отчёт (по всем доменам)
GET /api/coach/{userId}/summary
```

---

## Стартовые домены

При первом запуске автоматически создаются 8 доменов:

| Домен        | Slug           | Что отслеживает                    |
|--------------|----------------|------------------------------------|
| 💪 Спорт      | `sport`        | Тренировки, прогресс, восстановление |
| 🥗 Питание    | `nutrition`    | Рацион, БЖУ, пищевые привычки      |
| 📚 Чтение     | `reading`      | Книги, конспекты, идеи             |
| 🎬 Кино       | `cinema`       | Фильмы, рефлексия, рекомендации    |
| 🎵 Музыка     | `music`        | Создание, практика, творчество     |
| ❤️ Отношения  | `relationships`| Рефлексия, паттерны, коммуникация  |
| 💼 Работа     | `work`         | Проекты, фокус, продуктивность     |
| 🧘 Йога       | `yoga`         | Практика, прогресс, осознанность   |

---

## Автоматические check-in

Планировщик запускается автоматически:

- **09:00 каждый день** — утренний check-in: что планируешь сегодня?
- **21:00 каждый день** — вечерний итог: что сделал, как прошёл день?
- **20:00 каждое воскресенье** — еженедельный анализ паттернов

Сейчас результаты пишутся в лог. Когда добавишь Telegram-бот — замени
`log.info()` в `CheckInScheduler` на `telegramBotService.sendToUser(userId, text)`.

Можно изменить расписание в `application.yml`:
```yaml
coach:
  checkin-morning-cron: "0 0 9 * * *"
  checkin-evening-cron: "0 0 21 * * *"
```

---

## Структура проекта

```
src/main/java/ai/personal/ai_secretary/
├── AiSecretaryApplication.java
├── config/
│   └── AiConfig.java                  # ChatClient bean
├── controller/
│   ├── CoachController.java           # /api/coach/** endpoints
│   └── UserProfileController.java     # /api/users CRUD
├── dto/
│   └── Dtos.java                      # Request/Response DTOs
├── model/
│   ├── UserProfile.java               # Профиль пользователя
│   ├── Domain.java                    # Домен развития (ключевая сущность)
│   ├── DomainGoal.java                # Цели в домене
│   ├── ActivityLog.java               # Лог активностей
│   └── ChatMessage.java               # История диалога (привязана к домену)
├── repository/
│   ├── UserProfileRepository.java
│   ├── DomainRepository.java
│   ├── DomainGoalRepository.java
│   ├── ActivityLogRepository.java
│   └── ChatMessageRepository.java
├── service/
│   ├── CoachService.java              # Основная логика коуча
│   ├── DomainRouterService.java       # Claude-маршрутизатор по доменам
│   ├── DomainService.java             # CRUD доменов и целей
│   └── ActivityService.java          # Лог активностей
└── scheduler/
    └── CheckInScheduler.java          # Утренний/вечерний/еженедельный
```

---

## Следующие шаги (roadmap)

- [ ] **Telegram Bot** — основной интерфейс, замена `log.info()` в check-in планировщике
- [ ] **Авто-логирование активности** — коуч сам определяет активность из диалога и сохраняет
- [ ] **RAG-память** — pgvector для семантического поиска по истории
- [ ] **Публикация в Telegram-канал** — конспекты книг, рефлексии, еженедельные итоги
- [ ] **Голосовые заметки** — транскрипция через Whisper API
- [ ] **Cross-domain инсайты** — коуч замечает связи ("пропустил 3 тренировки — настроение упало")