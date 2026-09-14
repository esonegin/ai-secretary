-- Idempotent baseline for the current Day 1 prescription.
-- This records the latest known working baseline so the planner never starts from NULL.

WITH target AS (
    SELECT e.id, e.exercise_order, e.exercise_name
    FROM training_program_exercises e
    JOIN training_program_days d ON d.id = e.program_day_id
    JOIN training_programs p ON p.id = d.program_id
    WHERE p.name = 'Основная программа'
      AND p.version = 1
      AND d.day_type = '1'
      AND p.user_id = (SELECT id FROM user_profiles ORDER BY id LIMIT 1)
),
seed(exercise_order, weight_kg, reps_min, reps_max, load_mode) AS (
    VALUES
        (1, 92.50, 10, 10, 'TOTAL'),
        (2, 102.50, 8, 8, 'TOTAL'),
        (3, 60.00, 10, 12, 'TOTAL'),
        (4, 50.00, 10, 12, 'TOTAL'),
        (5, 30.50, 8, 10, 'TOTAL'),
        (6, 60.00, 10, 12, 'TOTAL'),
        (7, 65.00, 15, 15, 'TOTAL'),
        (8, 25.00, 20, 20, 'TOTAL'),
        (9, NULL, 12, 12, 'MOBILITY')
),
sets AS (
    SELECT t.id AS exercise_id,
           s.exercise_order,
           gs.set_number,
           s.weight_kg,
           s.reps_min,
           s.reps_max,
           s.load_mode
    FROM target t
    JOIN seed s ON s.exercise_order = t.exercise_order
    CROSS JOIN LATERAL generate_series(
        1,
        CASE s.exercise_order
            WHEN 8 THEN 2
            WHEN 9 THEN 2
            ELSE 3
        END
    ) AS gs(set_number)
)
INSERT INTO training_program_sets (
    program_exercise_id,
    set_number,
    weight_kg,
    load_mode,
    planned_reps_min,
    planned_reps_max
)
SELECT exercise_id, set_number, weight_kg, load_mode, reps_min, reps_max
FROM sets
ON CONFLICT (program_exercise_id, set_number) DO UPDATE
SET weight_kg = EXCLUDED.weight_kg,
    load_mode = EXCLUDED.load_mode,
    planned_reps_min = EXCLUDED.planned_reps_min,
    planned_reps_max = EXCLUDED.planned_reps_max;
