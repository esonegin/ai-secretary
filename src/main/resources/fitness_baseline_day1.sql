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
seed(exercise_order, set_number, weight_kg, reps_min, reps_max, load_mode) AS (
    VALUES
        (1, 1, 92.50, 10, 10, 'TOTAL'),
        (1, 2, 92.50, 10, 10, 'TOTAL'),
        (1, 3, 92.50, 10, 10, 'TOTAL'),
        (2, 1, 102.50, 8, 8, 'TOTAL'),
        (2, 2, 100.00, 8, 10, 'TOTAL'),
        (2, 3, 100.00, 8, 10, 'TOTAL'),
        (3, 1, 60.00, 10, 12, 'TOTAL'),
        (3, 2, 60.00, 10, 12, 'TOTAL'),
        (3, 3, 60.00, 10, 12, 'TOTAL'),
        (4, 1, 50.00, 10, 12, 'TOTAL'),
        (4, 2, 50.00, 10, 12, 'TOTAL'),
        (4, 3, 50.00, 10, 12, 'TOTAL'),
        (5, 1, 30.50, 8, 10, 'TOTAL'),
        (5, 2, 30.50, 8, 10, 'TOTAL'),
        (5, 3, 30.50, 8, 10, 'TOTAL'),
        (6, 1, 60.00, 10, 12, 'TOTAL'),
        (6, 2, 60.00, 10, 12, 'TOTAL'),
        (6, 3, 60.00, 10, 12, 'TOTAL'),
        (7, 1, 65.00, 15, 15, 'TOTAL'),
        (7, 2, 65.00, 15, 15, 'TOTAL'),
        (7, 3, 65.00, 15, 15, 'TOTAL'),
        (8, 1, 25.00, 20, 20, 'TOTAL'),
        (8, 2, 25.00, 20, 20, 'TOTAL'),
        (9, 1, NULL, 12, 12, 'MOBILITY'),
        (9, 2, NULL, 12, 12, 'MOBILITY')
)
INSERT INTO training_program_sets (
    program_exercise_id,
    set_number,
    weight_kg,
    load_mode,
    planned_reps_min,
    planned_reps_max
)
SELECT t.id, s.set_number, s.weight_kg, s.load_mode, s.reps_min, s.reps_max
FROM target t
JOIN seed s ON s.exercise_order = t.exercise_order
ON CONFLICT (program_exercise_id, set_number) DO UPDATE
SET weight_kg = EXCLUDED.weight_kg,
    load_mode = EXCLUDED.load_mode,
    planned_reps_min = EXCLUDED.planned_reps_min,
    planned_reps_max = EXCLUDED.planned_reps_max;
