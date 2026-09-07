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
