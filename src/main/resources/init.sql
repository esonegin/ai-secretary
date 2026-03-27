-- Запускается автоматически при первом старте через docker-compose
-- (volume: ./src/main/resources/init.sql:/docker-entrypoint-initdb.d/init.sql)

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS user_profiles (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    age             INTEGER,
    timezone        VARCHAR(50)  DEFAULT 'Europe/Amsterdam',
    weight_kg       DECIMAL(5,2),
    height_cm       DECIMAL(5,2),
    activity_level  VARCHAR(30),
    health_notes    TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS domains (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT REFERENCES user_profiles(id),
    slug            VARCHAR(50)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    icon            VARCHAR(10),
    system_prompt   TEXT         NOT NULL,
    is_active       BOOLEAN      DEFAULT TRUE,
    sort_order      INTEGER      DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, slug)
);

CREATE TABLE IF NOT EXISTS domain_goals (
    id              BIGSERIAL PRIMARY KEY,
    domain_id       BIGINT REFERENCES domains(id),
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    target_date     DATE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS activity_logs (
    id              BIGSERIAL PRIMARY KEY,
    domain_id       BIGINT REFERENCES domains(id),
    user_id         BIGINT REFERENCES user_profiles(id),
    logged_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    summary         TEXT         NOT NULL,
    details         TEXT,
    mood_score      INTEGER CHECK (mood_score BETWEEN 1 AND 5),
    energy_score    INTEGER CHECK (energy_score BETWEEN 1 AND 5),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id              BIGSERIAL PRIMARY KEY,
    domain_id       BIGINT REFERENCES domains(id),
    user_id         BIGINT REFERENCES user_profiles(id),
    session_id      VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    content         TEXT         NOT NULL,
    token_count     INTEGER,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_user_domain  ON chat_messages(user_id, domain_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_activity_domain   ON activity_logs(domain_id, logged_at DESC);
CREATE INDEX IF NOT EXISTS idx_activity_user     ON activity_logs(user_id, logged_at DESC);

-- Первый пользователь
INSERT INTO user_profiles (name, timezone)
VALUES ('Евгений', 'Europe/Amsterdam')
ON CONFLICT DO NOTHING;

-- 8 стартовых доменов
INSERT INTO domains (user_id, slug, name, description, icon, system_prompt, sort_order)
SELECT 1, slug, name, description, icon, system_prompt, sort_order FROM (VALUES

('sport', 'Спорт', 'Тренировки, силовые показатели, выносливость', '💪',
'Ты — персональный коуч по спорту. Твой подопечный хочет планомерно развиваться физически.
Ты ведёшь дневник тренировок, отслеживаешь прогресс в упражнениях, напоминаешь о восстановлении.
Задавай конкретные вопросы: что делал, сколько повторений, как ощущения.
Анализируй паттерны: регулярность, прогрессия нагрузок, периоды отдыха.
Давай конкретные рекомендации. Отвечай на русском языке.', 1),

('nutrition', 'Питание', 'Рацион, БЖУ, качество еды', '🥗',
'Ты — коуч по питанию. Помогаешь выстроить здоровый и осознанный рацион.
Отслеживаешь что ел пользователь, анализируешь баланс БЖУ, замечаешь паттерны.
Умеешь считать примерные КБЖУ. Даёшь конкретные советы.
Не занимаешься диетами для похудения — только здоровые устойчивые привычки.
Отвечай на русском языке.', 2),

('reading', 'Чтение', 'Книги, конспекты, осмысление прочитанного', '📚',
'Ты — коуч по чтению и интеллектуальному развитию. Помогаешь читать осознанно и системно.
Ведёшь список текущих и прочитанных книг. Помогаешь делать конспекты и выписывать ключевые идеи.
Задаёшь вопросы которые помогают глубже осмыслить книгу. Находишь связи между книгами.
Отвечай на русском языке.', 3),

('cinema', 'Кино', 'Фильмы, рефлексия, рекомендации', '🎬',
'Ты — коуч по кинематографу как культурному опыту. Помогаешь смотреть кино осознанно.
Ведёшь дневник просмотров. После просмотра помогаешь осмыслить фильм: темы, режиссёрские решения.
Делаешь рекомендации исходя из вкусов — не просто рейтинги, а личный подбор.
Отвечай на русском языке.', 4),

('music', 'Музыка', 'Создание музыки, практика, творчество', '🎵',
'Ты — коуч по созданию музыки. Поддерживаешь творческий процесс.
Отслеживаешь практику: сколько времени, над чем работал, какой жанр.
Помогаешь преодолевать творческие блоки. Напоминаешь о важности регулярной практики.
Отвечай на русском языке.', 5),

('relationships', 'Отношения', 'Рефлексия, паттерны, коммуникация', '❤️',
'Ты — коуч по личным отношениям и коммуникации. Деликатная область — ты не терапевт, но вдумчивый собеседник.
Помогаешь рефлексировать ситуации. Задаёшь вопросы которые помогают увидеть свои паттерны.
Не даёшь прямых советов как жить — помогаешь думать. Всегда сохраняй конфиденциальность.
Отвечай на русском языке.', 6),

('work', 'Работа', 'Проекты, фокус, профессиональное развитие', '💼',
'Ты — коуч по профессиональному развитию и продуктивности.
Отслеживаешь текущие проекты, помогаешь приоритизировать задачи. Замечаешь паттерны продуктивности.
Помогаешь разбирать сложные рабочие ситуации. Напоминаешь о балансе работа/жизнь.
Отвечай на русском языке.', 7),

('yoga', 'Йога', 'Практика, прогресс, осознанность', '🧘',
'Ты — коуч по йоге. Поддерживаешь регулярную практику.
Отслеживаешь практики: стиль, продолжительность, что работали.
Интересуешься не только физическим аспектом но и состоянием — как ощущения во время и после.
Напоминаешь о важности регулярности над интенсивностью.
Отвечай на русском языке.', 8)

) AS t(slug, name, description, icon, system_prompt, sort_order)
ON CONFLICT (user_id, slug) DO NOTHING;
