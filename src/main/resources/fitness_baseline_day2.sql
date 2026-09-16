-- Idempotent baseline for the latest known Day 2 working prescription.
-- Source: the user's logged Day 2 workout used by WorkoutAnalyst.
-- This is a baseline, not an instruction to progress automatically.

WITH target AS (
    SELECT e.id, e.exercise_order
    FROM training_program_exercises e
    JOIN training_program_days d ON d.id = e.program_day_id
    JOIN training_programs p ON p.id = d.program_id
    WHERE p.name = 'Основная программа'
      AND p.version = 1
      AND d.day_type = '2'
      AND p.user_id = (SELECT id FROM user_profiles ORDER BY id LIMIT 1)
),
seed(exercise_order, set_number, weight_kg, reps_min, reps_max, load_mode) AS (
    VALUES
        (1, 1, 40.00, 8, 8, 'TOTAL'),
        (1, 2, 40.00, 8, 8, 'TOTAL'),
        (1, 3, 40.00, 10, 10, 'TOTAL'),
        (2, 1, 80.00, 10, 10, 'TOTAL'),
        (2, 2, 80.00, 10, 10, 'TOTAL'),
        (2, 3, 80.00, 10, 10, 'TOTAL'),
        (3, 1, 30.00, 10, 10, 'TOTAL'),
        (3, 2, 30.00, 10, 10, 'TOTAL'),
        (3, 3, 30.00, 12, 12, 'TOTAL'),
        (4, 1, 9.00, 12, 12, 'TOTAL'),
        (4, 2, 9.00, 12, 12, 'TOTAL'),
        (4, 3, 9.00, 12, 12, 'TOTAL'),
        (5, 1, 25.00, 20, 20, 'TOTAL'),
        (5, 2, 25.00, 20, 20, 'TOTAL'),
        (6, 1, 15.00, 12, 12, 'TOTAL'),
        (6, 2, 15.00, 10, 10, 'TOTAL'),
        (6, 3, 15.00, 10, 10, 'TOTAL'),
        (7, 1, 85.00, 8, 8, 'TOTAL'),
        (7, 2, 87.50, 8, 8, 'TOTAL'),
        (7, 3, 87.50, 8, 8, 'TOTAL'),
        (8, 1, NULL, NULL, NULL, 'MOBILITY'),
        (8, 2, NULL, NULL, NULL, 'MOBILITY'),
        (8, 3, NULL, NULL, NULL, 'MOBILITY'),
        (9, 1, NULL, NULL, NULL, 'MOBILITY'),
        (9, 2, NULL, NULL, NULL, 'MOBILITY')
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
