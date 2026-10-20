# PostgreSQL schema setup — OPT-09

Schema setup creates tables/constraints. Catalog import and sample/dev-account seeding
are separate data operations. No migration inserts accounts, catalog rows, PDFs or reviews.

## What is authoritative

- `../src/main/resources/db/migration/V1__current_schema.sql` is the complete schema snapshot.
- `verify-v1-schema.sql` is a read-only compatibility gate. It raises an error on any
  expected column/type/default/nullability/identity, constraint or index difference.
- `db-migrations` is an explicit Maven profile, **not** an application dependency.
  Normal `mvn test`, `mvn package`, and application startup do not execute Flyway.
- Flyway/plugin/PostgreSQL module are pinned to **12.4.0**, JDBC to **42.7.13**,
  matching this project's Spring Boot 4.1.1 dependency management. The plugin has its
  own dependencies; none are added to the runtime application.
- Flyway uses `public`, `baselineOnMigrate=false`, `cleanDisabled=true`, and does not
  create schemas. The PostgreSQL database and its `public` schema must already exist.
- Use the Spring profile **db-managed** for every new/adopted database. It sets
  Hibernate to `validate` and disables both seeds. The old default `update` is retained
  only for the existing unadopted local setup; it is not the post-adoption workflow.

### Inspected differences, deliberately preserved

V1 was derived from read-only PostgreSQL **18.1** metadata on 2026-10-17, then compared
with the JPA mappings and recorded README SQL. It contains 12 application tables,
64 columns, 41 PK/FK/UNIQUE/CHECK constraints (excluding column NOT NULL), 25 indexes
(including constraint indexes) and 10 identity sequences.

The historical moderation table has two equivalent UNIQUE constraints:
`uk_review_moderation_category` and `ukc9irn435ixskbyyy7k9bxw9xs`. Both are retained.
The real `reviews.status` and `review_moderation_categories.category` columns have
no enum CHECK, although a new Hibernate-generated schema can differ here. V1 does
not add those CHECKs or a new rating CHECK. The schedule status/reason, pending
reason and user-role CHECKs do exist and are retained with their current values.
The exact `uk_review_author_offering_term` name is retained because exception
handling uses it. Nullable department/section/source IDs and review timestamps
remain nullable. Sequence **configuration**, not current production sequence values,
is included. No existing sequence is reset.

The verifier intentionally checks exact current definitions/names, including the
historical duplicate UNIQUE. For the existing enum-like CHECKs it also accepts the
equivalent per-element text casts PostgreSQL emits after restoring an array cast;
the column, operator and allowed values must still match exactly.
A different PostgreSQL version or semantically equivalent
renamed constraint may require investigation; do not baseline merely to bypass an error.
Flyway's `validate` checks migration history/checksums, not every physical constraint.
Hibernate `validate` also does not replace this schema verifier.

## Connection setup

Open a terminal in `backend`. Supply these variables locally, without putting their
values in source, commands recorded in documentation, or chat:

- `DB_URL` — full JDBC URL for the explicitly chosen database; required by the Maven profile.
- `DB_USERNAME`
- `DB_PASSWORD`

The migration profile has no fallback to the main database. Variables saved only in
IntelliJ's Run Configuration are not automatically available to its terminal. Restart
the terminal after changing Windows environment settings. Never use Maven `-X` or
print effective configuration with credentials present. Do not place credentials in URLs.

For PostgreSQL CLI tools, set process-local `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`
to the same target as DB_URL, and `PGPASSWORD` from the already configured DB_PASSWORD.
Do not pass the password as a command argument. These tools use PG* variables, whereas
Flyway uses DB_* through the Maven profile. Verify both targets explicitly before use.

```powershell
$env:PGUSER = $env:DB_USERNAME
$env:PGPASSWORD = $env:DB_PASSWORD
psql -X -w -v ON_ERROR_STOP=1 -c "SELECT current_database(), current_user, inet_server_addr(), inet_server_port();"
```

## A. Existing developer database — do NOT migrate it as if empty

The user's `student_feedback` has **not** been baselined by OPT-09.
Its read-only inspection is not approval to baseline it automatically.

1. Back up the full database and a schema-only snapshot into private ignored storage.
   Run from `backend` after creating `../work/db-backup` locally and confirming PGDATABASE:

   ```powershell
   pg_dump -w --format=custom --file=../work/db-backup/full.dump
   pg_dump -w --schema-only --no-owner --no-privileges --file=../work/db-backup/schema.sql
   ```

   Check exit codes. Backups contain private data; do not commit/share them. Preserve
   uploaded PDFs separately from `SCHEDULE_UPLOAD_DIR` (default:
   `%USERPROFILE%/.student-feedback/schedule-uploads`). Preserve required database-role
   ownership/grants as part of the restore plan; a database dump is not a roles backup.

2. Record row counts and secure content fingerprints for all 12 tables, and sequence
   state. Quiesce application writes for the final before/after comparison.
3. Inspect columns, defaults, nullability, constraints, validated FKs, indexes and
   sequences. Run the read-only gate:

   ```powershell
   psql -X -w -v ON_ERROR_STOP=1 -f sql/verify-v1-schema.sql
   ```

   Expected UNIQUE constraints must be backed by valid indexes, and FKs must be
   validated. This also establishes the database-enforced duplicate/orphan rules.
   If the gate fails, **STOP**: do not baseline, change rows or replay old ALTERs.
   Review a separate reconciliation plan for the specific mismatch.

4. Restore the backup to a separately named COPY and rehearse this procedure there.
   Never restore over the working database. The synthetic opt-in test below is not
   a substitute for checking that your real backup restores successfully.
5. After a compatible copy succeeds and the existing database adoption is explicitly
   approved, target the existing database and execute once:

   ```powershell
   mvn.cmd -Pdb-migrations flyway:baseline "-Dflyway.baselineVersion=1"
   mvn.cmd -Pdb-migrations flyway:info flyway:validate
   ```

   Baseline writes only Flyway history: "this database already corresponds to V1".
   It skips V1; it does not execute CREATE statements over existing tables and does
   not prove compatibility by itself. Never enable automatic baseline.
6. Start FeedbackApplication with active Spring profile `db-managed` (IntelliJ program
   argument `--spring.profiles.active=db-managed` also works). Expect Hibernate validation,
   no migration, no seed, and no import. Keep that profile on subsequent starts.
7. Repeat the schema gate and compare table counts/content/sequence state. Only the
   new `flyway_schema_history` should be added by adoption.

## B. Brand-new deployment database

1. Create a dedicated EMPTY PostgreSQL database with public schema and appropriate
   ownership/DDL privileges. Do not reuse the user's existing `student_feedback`.
2. Set DB_* and PG* to this new target. Check its identity as above. Disable any
   unrelated Flyway overrides in local settings/environment before running tooling.
3. From `backend` run:

   ```powershell
   mvn.cmd -Pdb-migrations flyway:migrate
   psql -X -w -v ON_ERROR_STOP=1 -f sql/verify-v1-schema.sql
   mvn.cmd -Pdb-migrations flyway:info flyway:validate
   ```

   **Do not baseline an empty database.** V1 must actually execute there.
   Running migrate a second time should apply zero migrations.
4. Start the packaged backend using the managed profile:

   ```powershell
   java -jar target/student-feedback-0.0.1-SNAPSHOT.jar --spring.profiles.active=db-managed
   ```

5. Empty catalog results are expected. Any approved catalog/seed/import operation is
   separate. Migrations never reconstruct the main database's 506/903/1741 data.
   Keep the managed profile active for explicit import/preflight commands as well.

Ship the pinned Maven profile and SQL migration files with the release/deployment
checkout used to run migrations. A running application JAR alone does not execute them.
Future changes use V2, V3, etc. Never edit a V1 already applied to any database.

## Historical scripts

`us28-review-uniqueness.sql` and `review-moderation-retry.sql` remain historical/reference
files. Their final effects are already in V1. **Do not manually replay them after
adoption or on a fresh migrated database.** Earlier SQL snippets in the
[development archive](../../docs/development-history.md) describe historical actions,
not the current deployment sequence.

## Tests and failure safety

Normal Maven tests remain H2/create-drop; they do not execute PostgreSQL V1. Run the
full backend package once when changing mappings/migrations. Separately, on a local
test server where the supplied user may CREATE DATABASE:

```powershell
mvn.cmd package
./sql/Test-Migrations.ps1 -Run
```

This opt-in PowerShell test creates three uniquely named `opt09_check_*` databases.
It never connects to the main database. It tests fresh migrate, second migrate,
empty application tables, all verifier definitions, explicit baseline of a synthetic
compatible schema, refusal of unbaselined/incompatible schemas, and packaged startup
with Hibernate validate over a read-only connection. No live SMTP/Gemini/Moodle calls.
Logs go to ignored `work/`. A temporary non-web Java process is closed before return.
The test databases are **retained**, since destructive cleanup is prohibited; they
contain only empty schema or clearly synthetic fixtures. No test runs during ordinary
Maven lifecycle unless explicitly invoked through this script.

V1 uses transactional PostgreSQL DDL. If it fails, inspect the error and actual state;
do not force success with cleanup, repair or baseline. Do not use Flyway clean,
DROP/TRUNCATE, automatic duplicate deletion, or a destructive restore. Do not edit
an already-applied migration. Future corrections require a new reviewed migration.
If adoption startup fails, keep the old application/data intact and investigate the
schema discrepancy. Returning to an older artifact is not permission to let Hibernate
update mutate the schema; use validation and restore only via an approved recovery plan.
