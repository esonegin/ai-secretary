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
