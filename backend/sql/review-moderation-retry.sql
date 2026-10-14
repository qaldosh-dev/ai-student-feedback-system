-- Explicit additive schema step for the manual moderation retry story.
-- Run against student_feedback before starting the updated application.
-- No historical timestamps are inferred and no review data or constraints are changed.
BEGIN;
SET LOCAL lock_timeout = '5s';
ALTER TABLE public.reviews ADD COLUMN IF NOT EXISTS last_moderation_attempt_at timestamp(6) with time zone NULL;
COMMIT;
