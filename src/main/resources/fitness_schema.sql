CREATE TABLE IF NOT EXISTS training_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_profiles(id),
    workout_date DATE NOT NULL,
    day_type VARCHAR(20) NOT NULL,
    body_weight_kg DECIMAL(5,2),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, workout_date, day_type)
);

CREATE INDEX IF NOT EXISTS idx_training_sessions_user_date
    ON training_sessions(user_id, workout_date DESC);

CREATE TABLE IF NOT EXISTS training_exercises (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES training_sessions(id) ON DELETE CASCADE,
    exercise_order INTEGER NOT NULL,
    exercise_name VARCHAR(150) NOT NULL,
    exercise_variant VARCHAR(150),
    notes TEXT,
    UNIQUE (session_id, exercise_order)
);

CREATE INDEX IF NOT EXISTS idx_training_exercises_session
    ON training_exercises(session_id, exercise_order);

CREATE TABLE IF NOT EXISTS training_sets (
    id BIGSERIAL PRIMARY KEY,
    exercise_id BIGINT NOT NULL REFERENCES training_exercises(id) ON DELETE CASCADE,
    set_number INTEGER NOT NULL,
    weight_kg DECIMAL(6,2),
    load_mode VARCHAR(20) NOT NULL DEFAULT 'TOTAL',
    actual_reps INTEGER,
    planned_reps_min INTEGER,
    planned_reps_max INTEGER,
    notes TEXT,
    UNIQUE (exercise_id, set_number),
    CHECK (actual_reps IS NULL OR actual_reps > 0),
    CHECK (planned_reps_min IS NULL OR planned_reps_min > 0),
    CHECK (planned_reps_max IS NULL OR planned_reps_max > 0),
    CHECK (
        planned_reps_min IS NULL
        OR planned_reps_max IS NULL
        OR planned_reps_min <= planned_reps_max
    )
);

CREATE INDEX IF NOT EXISTS idx_training_sets_exercise
    ON training_sets(exercise_id, set_number);

CREATE TABLE IF NOT EXISTS physical_measurements (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_profiles(id),
    measured_at TIMESTAMP,
    recorded_at TIMESTAMP NOT NULL DEFAULT NOW(),
    category VARCHAR(30) NOT NULL,
    metric VARCHAR(50) NOT NULL,
    value_numeric DECIMAL(8,2),
    unit VARCHAR(20),
    side VARCHAR(10),
    state VARCHAR(20),
    notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_physical_measurements_user_date
    ON physical_measurements(user_id, measured_at DESC, recorded_at DESC);

CREATE TABLE IF NOT EXISTS fitness_goals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_profiles(id),
    goal_text TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    priority INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    valid_from DATE,
    valid_to DATE,
    notes TEXT,
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_from <= valid_to)
);

CREATE INDEX IF NOT EXISTS idx_fitness_goals_user_status
    ON fitness_goals(user_id, status, priority DESC, created_at DESC);

CREATE TABLE IF NOT EXISTS training_programs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_profiles(id),
    name VARCHAR(150) NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    valid_from DATE,
    valid_to DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    notes TEXT,
    UNIQUE (user_id, name, version),
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_from <= valid_to)
);

CREATE INDEX IF NOT EXISTS idx_training_programs_user_status
    ON training_programs(user_id, status, valid_from DESC, created_at DESC);

CREATE TABLE IF NOT EXISTS training_program_days (
    id BIGSERIAL PRIMARY KEY,
    program_id BIGINT NOT NULL REFERENCES training_programs(id) ON DELETE CASCADE,
    day_type VARCHAR(20) NOT NULL,
    name VARCHAR(150) NOT NULL,
    day_order INTEGER NOT NULL,
    notes TEXT,
    UNIQUE (program_id, day_type),
    UNIQUE (program_id, day_order)
);

CREATE TABLE IF NOT EXISTS training_program_exercises (
    id BIGSERIAL PRIMARY KEY,
    program_day_id BIGINT NOT NULL REFERENCES training_program_days(id) ON DELETE CASCADE,
    exercise_order INTEGER NOT NULL,
    exercise_name VARCHAR(150) NOT NULL,
    exercise_variant VARCHAR(150),
    notes TEXT,
    UNIQUE (program_day_id, exercise_order)
);

CREATE TABLE IF NOT EXISTS training_program_sets (
    id BIGSERIAL PRIMARY KEY,
    program_exercise_id BIGINT NOT NULL REFERENCES training_program_exercises(id) ON DELETE CASCADE,
    set_number INTEGER NOT NULL,
    weight_kg DECIMAL(6,2),
    load_mode VARCHAR(20) NOT NULL DEFAULT 'TOTAL',
    planned_reps_min INTEGER,
    planned_reps_max INTEGER,
    notes TEXT,
    UNIQUE (program_exercise_id, set_number),
    CHECK (planned_reps_min IS NULL OR planned_reps_min > 0),
    CHECK (planned_reps_max IS NULL OR planned_reps_max > 0),
    CHECK (
        planned_reps_min IS NULL
        OR planned_reps_max IS NULL
        OR planned_reps_min <= planned_reps_max
    )
);

CREATE INDEX IF NOT EXISTS idx_training_program_days_program
    ON training_program_days(program_id, day_order);

CREATE INDEX IF NOT EXISTS idx_training_program_exercises_day
    ON training_program_exercises(program_day_id, exercise_order);

CREATE INDEX IF NOT EXISTS idx_training_program_sets_exercise
    ON training_program_sets(program_exercise_id, set_number);

-- Эталонная программа для первого существующего пользователя.
INSERT INTO training_programs (user_id, name, version, status)
SELECT id, 'Основная программа', 1, 'ACTIVE'
FROM user_profiles
ORDER BY id
LIMIT 1
ON CONFLICT (user_id, name, version) DO NOTHING;

INSERT INTO training_program_days (program_id, day_type, name, day_order)
SELECT p.id, seed.day_type, seed.name, seed.day_order
FROM training_programs p
JOIN (
    VALUES
        ('1', 'базовая сила + верх тела', 1),
        ('2', 'верх груди + задняя цепь + ширина', 2),
        ('3', 'спина + ноги + грудь/руки', 3)
) AS seed(day_type, name, day_order) ON TRUE
WHERE p.user_id = (SELECT id FROM user_profiles ORDER BY id LIMIT 1)
  AND p.name = 'Основная программа'
  AND p.version = 1
ON CONFLICT (program_id, day_type) DO NOTHING;

DELETE FROM training_program_exercises e
USING training_program_days d, training_programs p
WHERE e.program_day_id = d.id
  AND d.program_id = p.id
  AND p.user_id = (SELECT id FROM user_profiles ORDER BY id LIMIT 1)
  AND p.name = 'Основная программа'
  AND p.version = 1
  AND d.day_type = '2'
  AND (e.exercise_name = 'Вис на перекладине' OR e.exercise_order = 10);

INSERT INTO training_program_exercises (
    program_day_id, exercise_order, exercise_name, exercise_variant
)
SELECT d.id, seed.exercise_order, seed.exercise_name, seed.exercise_variant
FROM training_program_days d
JOIN training_programs p ON p.id = d.program_id
JOIN (
    VALUES
        ('1', 1, 'Приседания со штангой', NULL),
        ('1', 2, 'Жим штанги лёжа', NULL),
        ('1', 3, 'Тяга верхнего блока', NULL),
        ('1', 4, 'Плечи сидя', NULL),
        ('1', 5, 'Подъём штанги на бицепс', NULL),
        ('1', 6, 'Разгибание на трицепс', NULL),
        ('1', 7, 'Пресс «молитва»', NULL),
        ('1', 8, 'Face Pull', NULL),
        ('1', 9, 'Wall Slides', NULL),
        ('2', 1, 'Наклонный жим гантелей', NULL),
        ('2', 2, 'Румынская тяга', NULL),
        ('2', 3, 'Вертикальная тяга', NULL),
        ('2', 4, 'Отведения гантелей в стороны', NULL),
        ('2', 5, 'Face Pull', NULL),
        ('2', 6, 'Молотки сидя с гантелями', NULL),
        ('2', 7, 'Жим штанги узким хватом', NULL),
        ('2', 8, 'Растяжка грудных', NULL),
        ('2', 9, 'Wall Slides', NULL),
        ('3', 1, 'Тяга нижнего блока', NULL),
        ('3', 2, 'Жим ногами', NULL),
        ('3', 3, 'Брусья с дополнительным весом', NULL),
        ('3', 4, 'One-Arm Cable Rear Delt Fly', NULL),
        ('3', 5, 'Подъём на бицепс в блоке сидя под углом', NULL),
        ('3', 6, 'Разгибание на трицепс', NULL),
        ('3', 7, 'Пресс «молитва»', NULL),
        ('3', 8, 'Face Pull', NULL),
        ('3', 9, 'Wall Slides', NULL),
        ('3', 10, 'Растяжка грудных', NULL)
) AS seed(day_type, exercise_order, exercise_name, exercise_variant)
    ON seed.day_type = d.day_type
WHERE p.user_id = (SELECT id FROM user_profiles ORDER BY id LIMIT 1)
  AND p.name = 'Основная программа'
  AND p.version = 1
ON CONFLICT (program_day_id, exercise_order) DO UPDATE
SET exercise_name = EXCLUDED.exercise_name,
    exercise_variant = EXCLUDED.exercise_variant;

INSERT INTO training_program_sets (program_exercise_id, set_number)
SELECT e.id, set_number
FROM training_program_exercises e
JOIN training_program_days d ON d.id = e.program_day_id
JOIN training_programs p ON p.id = d.program_id
JOIN (
    VALUES
        ('1', 1, 3), ('1', 2, 3), ('1', 3, 3), ('1', 4, 3), ('1', 5, 3),
        ('1', 6, 3), ('1', 7, 3), ('1', 8, 2), ('1', 9, 2),
        ('2', 1, 3), ('2', 2, 3), ('2', 3, 3), ('2', 4, 3), ('2', 5, 2),
        ('2', 6, 3), ('2', 7, 3), ('2', 8, 3), ('2', 9, 2),
        ('3', 1, 3), ('3', 2, 3), ('3', 3, 3), ('3', 4, 3), ('3', 5, 3),
        ('3', 6, 3), ('3', 7, 3), ('3', 8, 2), ('3', 9, 2), ('3', 10, 3)
) AS seed(day_type, exercise_order, set_count)
    ON seed.day_type = d.day_type AND seed.exercise_order = e.exercise_order
CROSS JOIN LATERAL generate_series(1, seed.set_count) AS set_number
WHERE p.user_id = (SELECT id FROM user_profiles ORDER BY id LIMIT 1)
  AND p.name = 'Основная программа'
  AND p.version = 1
ON CONFLICT (program_exercise_id, set_number) DO NOTHING;
