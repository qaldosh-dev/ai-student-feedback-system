-- Manual, once-only schema step. Not run at application startup.
-- First inspect these groups and decide which legacy records to retain. No cleanup is performed here.
SELECT course_offering_id, academic_term, count(*) AS review_count,
       string_agg(id::text || ':' || status, ', ' ORDER BY id) AS reviews
FROM public.reviews
GROUP BY author_id, course_offering_id, academic_term
HAVING count(*) > 1;

BEGIN;
SET LOCAL lock_timeout = '5s';
LOCK TABLE public.reviews IN SHARE ROW EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.reviews
               GROUP BY author_id, course_offering_id, academic_term HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Review uniqueness not installed: resolve existing duplicate groups first. No rows were changed.';
    END IF;
END $$;
ALTER TABLE public.reviews ADD CONSTRAINT uk_review_author_offering_term
    UNIQUE (author_id, course_offering_id, academic_term);
COMMIT;
