-- Read-only V1 compatibility gate; execute with psql -X -v ON_ERROR_STOP=1 -f ...
-- No DDL/DML, temporary objects, baseline or migration execution.
-- Checks physical definitions, not just Hibernate mapping compatibility.
-- flyway_schema_history is excluded; application rows and secrets are never printed.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '30s';
DO $verify$
DECLARE problems text;
BEGIN
    WITH expected(table_name,column_name,type,nullable,default_value,identity_flag,identity_generation) AS (
        VALUES
        ('course_offerings', 'id', 'bigint', 'NO', NULL::text, 'YES', 'BY DEFAULT'),
        ('course_offerings', 'section', 'character varying(255)', 'YES', NULL::text, 'NO', NULL::text),
        ('course_offerings', 'semester', 'character varying(255)', 'NO', NULL::text, 'NO', NULL::text),
        ('course_offerings', 'course_id', 'bigint', 'NO', NULL::text, 'NO', NULL::text),
        ('course_offerings', 'teacher_id', 'bigint', 'NO', NULL::text, 'NO', NULL::text),
        ('course_offerings', 'moodle_course_id', 'bigint', 'YES', NULL::text, 'NO', NULL::text),
        ('courses', 'id', 'bigint', 'NO', NULL::text, 'YES', 'BY DEFAULT'),
        ('courses', 'code', 'character varying(255)', 'NO', NULL::text, 'NO', NULL::text),
        ('courses', 'name', 'character varying(255)', 'NO', NULL::text, 'NO', NULL::text),
        ('email_verifications', 'id', 'bigint', 'NO', NULL::text, 'YES', 'BY DEFAULT'),
        ('email_verifications', 'attempts', 'integer', 'NO', NULL::text, 'NO', NULL::text),
        ('email_verifications', 'code_hash', 'character varying(60)', 'NO', NULL::text, 'NO', NULL::text),
        ('email_verifications', 'created_at', 'timestamp(6) with time zone', 'NO', NULL::text, 'NO', NULL::text),
        ('email_verifications', 'expires_at', 'timestamp(6) with time zone', 'NO', NULL::text, 'NO', NULL::text),
        ('email_verifications', 'used_at', 'timestamp(6) with time zone', 'YES', NULL::text, 'NO', NULL::text),
        ('email_verifications', 'user_id', 'bigint', 'NO', NULL::text, 'NO', NULL::text),
        ('extracted_schedule_entries', 'id', 'bigint', 'NO', NULL::text, 'YES', 'BY DEFAULT'),
        ('extracted_schedule_entries', 'academic_term', 'character varying(64)', 'NO', NULL::text, 'NO', NULL::text),
        ('extracted_schedule_entries', 'course_code', 'character varying(32)', 'NO', NULL::text, 'NO', NULL::text),
        ('extracted_schedule_entries', 'course_name', 'character varying(512)', 'NO', NULL::text, 'NO', NULL::text),
        ('extracted_schedule_entries', 'group_section', 'character varying(32)', 'NO', NULL::text, 'NO', NULL::text),
        ('extracted_schedule_entries', 'teacher_name', 'character varying(255)', 'NO', NULL::text, 'NO', NULL::text),
        ('extracted_schedule_entries', 'schedule_upload_id', 'bigint', 'NO', NULL::text, 'NO', NULL::text),
        ('pending_catalog_verifications', 'id', 'bigint', 'NO', NULL::text, 'YES', 'BY DEFAULT'),
        ('pending_catalog_verifications', 'created_at', 'timestamp(6) with time zone', 'NO', NULL::text, 'NO', NULL::text),
        ('pending_catalog_verifications', 'reason', 'character varying(40)', 'NO', NULL::text, 'NO', NULL::text),
        ('pending_catalog_verifications', 'updated_at', 'timestamp(6) with time zone', 'NO', NULL::text, 'NO', NULL::text),
        ('pending_catalog_verifications', 'source_entry_id', 'bigint', 'NO', NULL::text, 'NO', NULL::text),
        ('review_moderation_categories', 'review_id', 'bigint', 'NO', NULL::text, 'NO', NULL::text),
        ('review_moderation_categories', 'category', 'character varying(40)', 'NO', NULL::text, 'NO', NULL::text),
        ('reviews', 'id', 'bigint', 'NO', NULL::text, 'YES', 'BY DEFAULT'),
        ('reviews', 'academic_term', 'character varying(255)', 'NO', NULL::text, 'NO', NULL::text),
        ('reviews', 'created_at', 'timestamp(6) with time zone', 'NO', NULL::text, 'NO', NULL::text),
        ('reviews', 'rating', 'integer', 'NO', NULL::text, 'NO', NULL::text),
        ('reviews', 'text', 'character varying(2000)', 'NO', NULL::text, 'NO', NULL::text),
        ('reviews', 'author_id', 'bigint', 'NO', NULL::text, 'NO', NULL::text),
        ('reviews', 'course_offering_id', 'bigint', 'NO', NULL::text, 'NO', NULL::text),
        ('reviews', 'status', 'character varying(32)', 'NO', '''SUBMITTED''::character varying', 'NO', NULL::text),
        ('reviews', 'moderated_at', 'timestamp(6) with time zone', 'YES', NULL::text, 'NO', NULL::text),
        ('reviews', 'last_moderation_attempt_at', 'timestamp(6) with time zone', 'YES', NULL::text, 'NO', NULL::text),
        ('schedule_uploads', 'id', 'bigint', 'NO', NULL::text, 'YES', 'BY DEFAULT'),
        ('schedule_uploads', 'original_filename', 'character varying(255)', 'NO', NULL::text, 'NO', NULL::text),
        ('schedule_uploads', 'status', 'character varying(20)', 'NO', NULL::text, 'NO', NULL::text),
        ('schedule_uploads', 'storage_key', 'character varying(40)', 'NO', NULL::text, 'NO', NULL::text),
        ('schedule_uploads', 'uploaded_at', 'timestamp(6) with time zone', 'NO', NULL::text, 'NO', NULL::text),
        ('schedule_uploads', 'user_id', 'bigint', 'NO', NULL::text, 'NO', NULL::text),
        ('schedule_uploads', 'validated_at', 'timestamp(6) with time zone', 'YES', NULL::text, 'NO', NULL::text),
        ('schedule_uploads', 'validation_reason', 'character varying(40)', 'YES', NULL::text, 'NO', NULL::text),
        ('teachers', 'id', 'bigint', 'NO', NULL::text, 'YES', 'BY DEFAULT'),
        ('teachers', 'department', 'character varying(255)', 'YES', NULL::text, 'NO', NULL::text),
        ('teachers', 'name', 'character varying(255)', 'NO', NULL::text, 'NO', NULL::text),
        ('teachers', 'moodle_teacher_id', 'bigint', 'YES', NULL::text, 'NO', NULL::text),
        ('user_roles', 'user_id', 'bigint', 'NO', NULL::text, 'NO', NULL::text),
        ('user_roles', 'role', 'character varying(255)', 'NO', NULL::text, 'NO', NULL::text),
        ('users', 'id', 'bigint', 'NO', NULL::text, 'YES', 'BY DEFAULT'),
        ('users', 'created_at', 'timestamp(6) with time zone', 'NO', NULL::text, 'NO', NULL::text),
        ('users', 'email', 'character varying(255)', 'NO', NULL::text, 'NO', NULL::text),
        ('users', 'email_verified', 'boolean', 'NO', NULL::text, 'NO', NULL::text),
        ('users', 'password_hash', 'character varying(255)', 'NO', NULL::text, 'NO', NULL::text),
        ('users', 'linked_teacher_id', 'bigint', 'YES', NULL::text, 'NO', NULL::text),
        ('verified_courses', 'id', 'bigint', 'NO', NULL::text, 'YES', 'BY DEFAULT'),
        ('verified_courses', 'created_at', 'timestamp(6) with time zone', 'NO', NULL::text, 'NO', NULL::text),
        ('verified_courses', 'course_offering_id', 'bigint', 'NO', NULL::text, 'NO', NULL::text),
        ('verified_courses', 'source_entry_id', 'bigint', 'NO', NULL::text, 'NO', NULL::text)
    ), actual AS (
        SELECT c.table_name,c.column_name,format_type(a.atttypid,a.atttypmod) AS type,
               c.is_nullable AS nullable,c.column_default AS default_value,c.is_identity AS identity_flag,c.identity_generation
        FROM information_schema.columns c
        JOIN pg_class t ON t.relname=c.table_name
        JOIN pg_namespace n ON n.oid=t.relnamespace AND n.nspname=c.table_schema
        JOIN pg_attribute a ON a.attrelid=t.oid AND a.attname=c.column_name
        WHERE c.table_schema='public' AND c.table_name <> 'flyway_schema_history'
    )
    SELECT string_agg(coalesce(e.table_name,a.table_name)||'.'||coalesce(e.column_name,a.column_name), ', ')
    INTO problems FROM expected e FULL JOIN actual a USING(table_name,column_name)
    WHERE e.table_name IS NULL OR a.table_name IS NULL
       OR ROW(e.type,e.nullable,e.default_value,e.identity_flag,e.identity_generation)
          IS DISTINCT FROM ROW(a.type,a.nullable,a.default_value,a.identity_flag,a.identity_generation);
    IF problems IS NOT NULL THEN RAISE EXCEPTION 'V1 column/table mismatch: %', problems; END IF;

    WITH expected(table_name,name,definition) AS (
        VALUES
        ('course_offerings', 'course_offerings_pkey', 'PRIMARY KEY (id)'),
        ('course_offerings', 'fkneiyouykg665f171dkr2h7or3', 'FOREIGN KEY (course_id) REFERENCES courses(id)'),
        ('course_offerings', 'fkpi2rlivg5bl7qherw6y4ia3hh', 'FOREIGN KEY (teacher_id) REFERENCES teachers(id)'),
        ('course_offerings', 'uk_offerings_moodle_teacher_semester', 'UNIQUE (moodle_course_id, teacher_id, semester)'),
        ('course_offerings', 'uke7loucl4tpvp9i246mackny6v', 'UNIQUE (teacher_id, course_id, semester, section)'),
        ('courses', 'courses_pkey', 'PRIMARY KEY (id)'),
        ('courses', 'uk61og8rbqdd2y28rx2et5fdnxd', 'UNIQUE (code)'),
        ('email_verifications', 'email_verifications_pkey', 'PRIMARY KEY (id)'),
        ('email_verifications', 'fkou1xuy3rdeao75p2x1v4v3xs', 'FOREIGN KEY (user_id) REFERENCES users(id)'),
        ('email_verifications', 'uk_email_verifications_user', 'UNIQUE (user_id)'),
        ('extracted_schedule_entries', 'extracted_schedule_entries_pkey', 'PRIMARY KEY (id)'),
        ('extracted_schedule_entries', 'fk4of0rw5ybo6kdilxrhh7g3mqe', 'FOREIGN KEY (schedule_upload_id) REFERENCES schedule_uploads(id)'),
        ('pending_catalog_verifications', 'fkqlxu0hiub3kusaadb8nr0rlj8', 'FOREIGN KEY (source_entry_id) REFERENCES extracted_schedule_entries(id)'),
        ('pending_catalog_verifications', 'pending_catalog_verifications_pkey', 'PRIMARY KEY (id)'),
        ('pending_catalog_verifications', 'pending_catalog_verifications_reason_check', 'CHECK (((reason)::text = ANY ((ARRAY[''COURSE_NOT_FOUND''::character varying, ''TEACHER_NOT_FOUND''::character varying, ''COURSE_AND_TEACHER_NOT_FOUND''::character varying, ''NO_COURSE_OFFERING''::character varying, ''AMBIGUOUS_COURSE''::character varying, ''AMBIGUOUS_TEACHER''::character varying, ''AMBIGUOUS_OFFERING''::character varying, ''COURSE_DATA_CONFLICT''::character varying, ''INCOMPLETE_ENTRY''::character varying])::text[])))'),
        ('pending_catalog_verifications', 'uk_pending_source_entry', 'UNIQUE (source_entry_id)'),
        ('review_moderation_categories', 'review_moderation_categories_review_id_fkey', 'FOREIGN KEY (review_id) REFERENCES reviews(id)'),
        ('review_moderation_categories', 'uk_review_moderation_category', 'UNIQUE (review_id, category)'),
        ('review_moderation_categories', 'ukc9irn435ixskbyyy7k9bxw9xs', 'UNIQUE (review_id, category)'),
        ('reviews', 'fk9rkw93ysf562i9w7cgi2o9px4', 'FOREIGN KEY (course_offering_id) REFERENCES course_offerings(id)'),
        ('reviews', 'fkse5kx11600wtv0jh9jobvrdpi', 'FOREIGN KEY (author_id) REFERENCES users(id)'),
        ('reviews', 'reviews_pkey', 'PRIMARY KEY (id)'),
        ('reviews', 'uk_review_author_offering_term', 'UNIQUE (author_id, course_offering_id, academic_term)'),
        ('schedule_uploads', 'fki59wsf7ua9r13ebdpuruvcpta', 'FOREIGN KEY (user_id) REFERENCES users(id)'),
        ('schedule_uploads', 'schedule_uploads_pkey', 'PRIMARY KEY (id)'),
        ('schedule_uploads', 'schedule_uploads_status_check', 'CHECK (((status)::text = ANY ((ARRAY[''UPLOADED''::character varying, ''VALIDATING''::character varying, ''VALID''::character varying, ''INVALID''::character varying])::text[])))'),
        ('schedule_uploads', 'schedule_uploads_validation_reason_check', 'CHECK (((validation_reason)::text = ANY ((ARRAY[''INVALID_PDF''::character varying, ''UNREADABLE_PDF''::character varying, ''EMPTY_PDF''::character varying, ''NOT_SCHEDULE''::character varying, ''DETAILS_REQUIRED''::character varying, ''VALIDATION_LIMIT''::character varying, ''VALIDATION_UNAVAILABLE''::character varying])::text[])))'),
        ('schedule_uploads', 'ukp49a8drtx00486c3sa83ka5sd', 'UNIQUE (storage_key)'),
        ('teachers', 'teachers_pkey', 'PRIMARY KEY (id)'),
        ('teachers', 'uk_teachers_moodle_teacher_id', 'UNIQUE (moodle_teacher_id)'),
        ('user_roles', 'fkhfh9dx7w3ubf1co1vdev94g3f', 'FOREIGN KEY (user_id) REFERENCES users(id)'),
        ('user_roles', 'uk_user_roles', 'UNIQUE (user_id, role)'),
        ('user_roles', 'user_roles_role_check', 'CHECK (((role)::text = ANY ((ARRAY[''STUDENT''::character varying, ''TEACHER''::character varying, ''ADMIN''::character varying])::text[])))'),
        ('users', 'fk20f7ttctgmw4qkc3giioffgpq', 'FOREIGN KEY (linked_teacher_id) REFERENCES teachers(id)'),
        ('users', 'uk_users_email', 'UNIQUE (email)'),
        ('users', 'uk_users_linked_teacher', 'UNIQUE (linked_teacher_id)'),
        ('users', 'users_pkey', 'PRIMARY KEY (id)'),
        ('verified_courses', 'fk1hqjolgal1dwgkexniyi1t9a1', 'FOREIGN KEY (course_offering_id) REFERENCES course_offerings(id)'),
        ('verified_courses', 'fk5f6jd3wsl1oony3hhlo8eii1g', 'FOREIGN KEY (source_entry_id) REFERENCES extracted_schedule_entries(id)'),
        ('verified_courses', 'uk_verified_source_entry', 'UNIQUE (source_entry_id)'),
        ('verified_courses', 'verified_courses_pkey', 'PRIMARY KEY (id)')
    ), actual AS (
        SELECT t.relname AS table_name,c.conname AS name,pg_get_constraintdef(c.oid) AS definition,
               c.convalidated,c.condeferrable,c.condeferred
        FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
        JOIN pg_namespace n ON n.oid=t.relnamespace
        WHERE n.nspname='public' AND t.relname <> 'flyway_schema_history' AND c.contype <> 'n'
    )
    SELECT string_agg(coalesce(e.table_name,a.table_name)||'.'||coalesce(e.name,a.name), ', ')
    INTO problems FROM expected e FULL JOIN actual a USING(table_name,name)
    -- PostgreSQL may deparse a restored varchar-array CHECK with per-element text
    -- casts instead of one array cast. Accept that exact equivalent spelling only;
    -- keep the operator, column, allowed values, order and constraint name intact.
    WHERE (e.definition IS DISTINCT FROM a.definition AND
           replace(replace(regexp_replace(e.definition,
               '(''[^'']*''::character varying)', '(\1)::text', 'g'),
               '((ARRAY[', '(ARRAY['), '])::text[])', '])') IS DISTINCT FROM a.definition)
       OR NOT a.convalidated OR a.condeferrable OR a.condeferred;
    IF problems IS NOT NULL THEN RAISE EXCEPTION 'V1 constraint mismatch: %', problems; END IF;

    WITH expected(tablename,indexname,indexdef) AS (
        VALUES
        ('course_offerings', 'course_offerings_pkey', 'CREATE UNIQUE INDEX course_offerings_pkey ON public.course_offerings USING btree (id)'),
        ('course_offerings', 'uk_offerings_moodle_teacher_semester', 'CREATE UNIQUE INDEX uk_offerings_moodle_teacher_semester ON public.course_offerings USING btree (moodle_course_id, teacher_id, semester)'),
        ('course_offerings', 'uke7loucl4tpvp9i246mackny6v', 'CREATE UNIQUE INDEX uke7loucl4tpvp9i246mackny6v ON public.course_offerings USING btree (teacher_id, course_id, semester, section)'),
        ('courses', 'courses_pkey', 'CREATE UNIQUE INDEX courses_pkey ON public.courses USING btree (id)'),
        ('courses', 'uk61og8rbqdd2y28rx2et5fdnxd', 'CREATE UNIQUE INDEX uk61og8rbqdd2y28rx2et5fdnxd ON public.courses USING btree (code)'),
        ('email_verifications', 'email_verifications_pkey', 'CREATE UNIQUE INDEX email_verifications_pkey ON public.email_verifications USING btree (id)'),
        ('email_verifications', 'uk_email_verifications_user', 'CREATE UNIQUE INDEX uk_email_verifications_user ON public.email_verifications USING btree (user_id)'),
        ('extracted_schedule_entries', 'extracted_schedule_entries_pkey', 'CREATE UNIQUE INDEX extracted_schedule_entries_pkey ON public.extracted_schedule_entries USING btree (id)'),
        ('extracted_schedule_entries', 'idx_extracted_schedule_upload', 'CREATE INDEX idx_extracted_schedule_upload ON public.extracted_schedule_entries USING btree (schedule_upload_id)'),
        ('pending_catalog_verifications', 'pending_catalog_verifications_pkey', 'CREATE UNIQUE INDEX pending_catalog_verifications_pkey ON public.pending_catalog_verifications USING btree (id)'),
        ('pending_catalog_verifications', 'uk_pending_source_entry', 'CREATE UNIQUE INDEX uk_pending_source_entry ON public.pending_catalog_verifications USING btree (source_entry_id)'),
        ('review_moderation_categories', 'uk_review_moderation_category', 'CREATE UNIQUE INDEX uk_review_moderation_category ON public.review_moderation_categories USING btree (review_id, category)'),
        ('review_moderation_categories', 'ukc9irn435ixskbyyy7k9bxw9xs', 'CREATE UNIQUE INDEX ukc9irn435ixskbyyy7k9bxw9xs ON public.review_moderation_categories USING btree (review_id, category)'),
        ('reviews', 'reviews_pkey', 'CREATE UNIQUE INDEX reviews_pkey ON public.reviews USING btree (id)'),
        ('reviews', 'uk_review_author_offering_term', 'CREATE UNIQUE INDEX uk_review_author_offering_term ON public.reviews USING btree (author_id, course_offering_id, academic_term)'),
        ('schedule_uploads', 'schedule_uploads_pkey', 'CREATE UNIQUE INDEX schedule_uploads_pkey ON public.schedule_uploads USING btree (id)'),
        ('schedule_uploads', 'ukp49a8drtx00486c3sa83ka5sd', 'CREATE UNIQUE INDEX ukp49a8drtx00486c3sa83ka5sd ON public.schedule_uploads USING btree (storage_key)'),
        ('teachers', 'teachers_pkey', 'CREATE UNIQUE INDEX teachers_pkey ON public.teachers USING btree (id)'),
        ('teachers', 'uk_teachers_moodle_teacher_id', 'CREATE UNIQUE INDEX uk_teachers_moodle_teacher_id ON public.teachers USING btree (moodle_teacher_id)'),
        ('user_roles', 'uk_user_roles', 'CREATE UNIQUE INDEX uk_user_roles ON public.user_roles USING btree (user_id, role)'),
        ('users', 'uk_users_email', 'CREATE UNIQUE INDEX uk_users_email ON public.users USING btree (email)'),
        ('users', 'uk_users_linked_teacher', 'CREATE UNIQUE INDEX uk_users_linked_teacher ON public.users USING btree (linked_teacher_id)'),
        ('users', 'users_pkey', 'CREATE UNIQUE INDEX users_pkey ON public.users USING btree (id)'),
        ('verified_courses', 'uk_verified_source_entry', 'CREATE UNIQUE INDEX uk_verified_source_entry ON public.verified_courses USING btree (source_entry_id)'),
        ('verified_courses', 'verified_courses_pkey', 'CREATE UNIQUE INDEX verified_courses_pkey ON public.verified_courses USING btree (id)')
    ), actual AS (
        SELECT tablename,indexname,indexdef FROM pg_indexes
        WHERE schemaname='public' AND tablename <> 'flyway_schema_history'
    )
    SELECT string_agg(coalesce(e.indexname,a.indexname), ', ')
    INTO problems FROM expected e FULL JOIN actual a USING(tablename,indexname)
    WHERE e.indexdef IS DISTINCT FROM a.indexdef;
    IF problems IS NOT NULL THEN RAISE EXCEPTION 'V1 index mismatch: %', problems; END IF;
    IF EXISTS (SELECT 1 FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid
               JOIN pg_namespace n ON n.oid=t.relnamespace
               WHERE n.nspname='public' AND (NOT i.indisvalid OR NOT i.indisready)) THEN
        RAISE EXCEPTION 'Unusable index in public schema';
    END IF;

    WITH expected(sequencename,data_type,start_value,min_value,max_value,increment_by,cycle,cache_size) AS (
        VALUES
        ('course_offerings_id_seq', 'bigint', '1', '1', '9223372036854775807', '1', 'False', '1'),
        ('courses_id_seq', 'bigint', '1', '1', '9223372036854775807', '1', 'False', '1'),
        ('email_verifications_id_seq', 'bigint', '1', '1', '9223372036854775807', '1', 'False', '1'),
        ('extracted_schedule_entries_id_seq', 'bigint', '1', '1', '9223372036854775807', '1', 'False', '1'),
        ('pending_catalog_verifications_id_seq', 'bigint', '1', '1', '9223372036854775807', '1', 'False', '1'),
        ('reviews_id_seq', 'bigint', '1', '1', '9223372036854775807', '1', 'False', '1'),
        ('schedule_uploads_id_seq', 'bigint', '1', '1', '9223372036854775807', '1', 'False', '1'),
        ('teachers_id_seq', 'bigint', '1', '1', '9223372036854775807', '1', 'False', '1'),
        ('users_id_seq', 'bigint', '1', '1', '9223372036854775807', '1', 'False', '1'),
        ('verified_courses_id_seq', 'bigint', '1', '1', '9223372036854775807', '1', 'False', '1')
    ), actual AS (
        SELECT sequencename,data_type::text,start_value::text,min_value::text,max_value::text,
               increment_by::text,CASE WHEN cycle THEN 'True' ELSE 'False' END AS cycle,cache_size::text
        FROM pg_sequences WHERE schemaname='public'
    )
    SELECT string_agg(coalesce(e.sequencename,a.sequencename), ', ')
    INTO problems FROM expected e FULL JOIN actual a USING(sequencename)
    WHERE e.sequencename IS NULL OR a.sequencename IS NULL
       OR ROW(e.data_type,e.start_value,e.min_value,e.max_value,e.increment_by,e.cycle,e.cache_size)
          IS DISTINCT FROM ROW(a.data_type,a.start_value,a.min_value,a.max_value,a.increment_by,a.cycle,a.cache_size);
    IF problems IS NOT NULL THEN RAISE EXCEPTION 'V1 sequence mismatch: %', problems; END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns c
        WHERE c.table_schema='public' AND c.is_identity='YES'
          AND pg_get_serial_sequence('public.'||quote_ident(c.table_name), c.column_name)
              IS DISTINCT FROM 'public.'||c.table_name||'_id_seq'
    ) THEN RAISE EXCEPTION 'V1 identity sequence ownership mismatch'; END IF;

    RAISE NOTICE 'V1 schema verified: 12 tables, 64 columns, 41 constraints, 25 indexes, 10 identities. No changes made.';
END
$verify$;
COMMIT;
