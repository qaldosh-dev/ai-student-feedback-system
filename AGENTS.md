# AI Student Feedback System — Coding Instructions

## Project

AI Student Feedback System for SDU.

## Stack

Backend:
- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA / Hibernate
- Maven
- PostgreSQL

Frontend:
- React
- TypeScript
- Vite
- React Router
- plain CSS

Architecture:
Controller → Service → Repository → PostgreSQL.

Keep implementation beginner-readable.
Do not overengineer.
Do not create future files/features "just in case".

---

## Stable Completed Foundation

Sprint 1 authentication/catalog foundation is complete and should be treated as stable.

Implemented:
- registration
- SDU email verification
- working SMTP
- sign in / sign out
- server-side sessions
- CSRF
- STUDENT / TEACHER / ADMIN authorization
- role selection
- Teacher Claim
- multi-role support
- public Teacher/Course catalog
- Teacher search
- Course search
- linked Teacher/Course filtering
- public Moodle catalog import

Current persistent catalog baseline:

- Teachers: 506
- Courses: 903
- CourseOfferings: 1741

Do not redesign or modify this foundation unless the current User Story requires it.

---

## Schedule Feature Current State

Completed:

US13 — Upload Schedule PDF
US14 — Validate Uploaded Schedule File
US14.1 — Require Detailed/Full Schedule PDF

Schedule upload:
- STUDENT protected
- files stored privately outside source code
- internal UUID filenames
- 10 MB limit
- server determines owner
- CSRF remains enabled

Validation:

Detailed/Full SDU Schedule:
→ VALID

Compact/Short SDU Schedule:
→ DETAILS_REQUIRED

Unrelated PDF:
→ NOT_SCHEDULE

Corrupted/fake PDF:
→ INVALID_PDF

Blank/no-text PDF:
→ EMPTY_PDF

---

## SDU Schedule Semantics

For future extraction, important information is:

- Course Code
- Course Name
- Teacher Name
- Group/Section
- Academic Term

Examples such as:

15-P
04-N
01-N
03-P

must be treated as complete group/section identifiers.

DO NOT interpret:
P = practice
N = lecture

unless an authoritative source is introduced later.

A Course may appear multiple times with different Teachers and/or groups.
Do not incorrectly collapse such entries.

Day, time, room and building are NOT required for enrollment verification and should not be extracted unless a future User Story explicitly needs them.

Only Detailed/Full schedules should proceed to structured extraction.

---

## Local Schedule Samples

Local ignored samples may exist under:

work/schedule-samples/

For example:

Schedule-detailed.pdf
Schedule-compact.pdf

These files:
- are local test data
- must not be committed
- must not be moved to application resources
- must not be hardcoded into production logic

---

## Security Rules

Never:
- expose password hashes
- expose verification-code hashes
- expose session IDs
- expose SMTP secrets
- commit environment-variable secret values
- trust client-supplied user IDs for ownership
- disable CSRF globally
- scrape private SDU Portal credentials/sessions

Teacher identity verification limitation is accepted for MVP.

---

## Development Scope Rule

Implement ONLY the requested User Story.

Do not automatically start the next User Story.

Do not add:
- Schedule features from later stories
- Reviews
- AI
- analytics
- admin features

unless explicitly requested.

---

## Testing Policy

Quality is important, but avoid unnecessary repeated regression work.

For each User Story:

1. inspect relevant existing code
2. implement the smallest correct change
3. add focused automated tests
4. test important edge/security cases
5. if a bug is found, fix it and rerun focused tests
6. run the full backend test suite ONCE at the end
7. run frontend build if frontend changed
8. use relevant real local sample when useful
9. run targeted regression for affected functionality
10. do not rerun full auth/catalog/browser suites unless those areas were actually changed

Full-project regression should be reserved for:
- end of sprint
- major architectural/security changes
- release/checkpoint verification

---

## Process Management

The user wants to control normal application startup manually.

Codex may start temporary backend/frontend processes for testing, but:

- stop every temporary process before finishing
- do not leave ports 8080 or 5173 occupied
- report that cleanup was performed

---

## Git / Files

Never commit:
- work/
- local uploaded PDFs
- uploaded schedule storage
- SMTP secrets
- environment secrets

Preserve existing .gitignore protections.

---

## Final Response for Each Task

Keep the final report concise.

Include:
- files changed
- implemented behavior
- focused tests
- build result
- real sample result if relevant
- regression issues if any
- confirmation next User Story was not started

Do not produce a huge report unless explicitly requested.