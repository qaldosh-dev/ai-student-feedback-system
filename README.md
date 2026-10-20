# AI Student Feedback System

A full-stack university project for SDU that connects student feedback to verified schedule information and helps teachers understand published feedback through ratings and AI-assisted analysis.

**Browse the catalog → verify your schedule → submit feedback → explore anonymous feedback and analytics.**

Built with **React · TypeScript · Java 21 · Spring Boot · PostgreSQL · Gemini**.

This is an independently developed student MVP, not an official SDU service.

## What the application does

### Public catalog

- Browse and search teachers and courses, with linked teacher/course filters.
- Sort teachers by name, rating, review count, or availability of published feedback.
- Open teacher profiles and course details.
- Explore published anonymous feedback and ratings by course and academic term.

### Student experience

- Register with an SDU email address, verify the mailbox, and select the Student role.
- Upload a **Detailed/Full SDU Schedule PDF** and view extracted course, teacher, group, and term information.
- Match schedule entries conservatively against the catalog; unresolved entries remain pending.
- Review eligible course offerings, track moderation status, correct flagged feedback, and edit published reviews.
- View personal feedback history and retry incomplete moderation when the cooldown permits.

### Teacher experience

- Link an account to a teacher profile through the current MVP claim flow.
- Filter feedback by the linked teacher's courses and academic terms.
- View rating statistics, AI sentiment, common topics/concerns, and a concise AI summary.
- Receive aggregate analytics only when the selected scope meets the privacy threshold.

### Interface

Light/dark themes, persistent theme selection, mobile layouts, clear status badges, toast notifications, loading states, and role-aware navigation.

## How verification and feedback work

1. **Verify an SDU mailbox.** Public registration accepts only `@stu.sdu.edu.kz` and `@sdu.edu.kz`. The email domain does not automatically assign a product role.
2. **Set up the account.** Users can select Student and/or claim a Teacher profile. Admin access is not publicly selectable.
3. **Upload a detailed schedule.** The backend validates the PDF and extracts course code/name, teacher name, group/section, and academic term.
4. **Check catalog relationships.** Confident matches become verified records. Missing, contradictory, or ambiguous data does not automatically create catalog entries or grant review eligibility.
5. **Submit feedback.** The server checks ownership and eligibility. One review is allowed per student, course offering, and exact academic term. Reviewing your own linked teacher profile is forbidden.
6. **Moderate before publication.** Accepted reviews become `PUBLISHED`; flagged reviews become `NEEDS_CORRECTION`. Provider failures leave reviews `SUBMITTED` for a later explicit retry.

Group values such as `15-P` and `04-N` are preserved as complete identifiers; the application does not infer their teaching format.

## Privacy and security

- Server-side HTTP sessions, BCrypt password hashing, CSRF protection, and backend role authorization.
- Uploaded PDFs are stored privately outside the source tree and are not publicly served.
- Student identity is not exposed in teacher/public feedback responses. Review ownership remains stored internally for access control and duplicate prevention.
- Teacher analytics requires at least **3 published reviews** in the selected teacher/course/term scope; smaller scopes are privacy-protected.
- Only published feedback contributes to analytics. Separate sentiment, topics, and summary operations can fail independently.
- Gemini receives feedback text for moderation/analysis, without added account identifiers. Text entered by users may itself contain personal information; AI processing is an external service.

**MVP limitations:** schedule matching is not an authoritative enrollment check against SDU Portal, and teacher claims do not yet include independent institutional identity approval. AI outputs are interpretations, not verified facts.

## Technology

| Area | Stack |
| --- | --- |
| Frontend | React, TypeScript, Vite, React Router, plain CSS |
| Backend | Java 21, Spring Boot, Spring Security, Spring Data JPA |
| Database | PostgreSQL; explicit Flyway migrations for new/adopted databases |
| Schedule processing | Apache PDFBox |
| Catalog tooling | Jsoup; public Moodle catalog analysis and explicit import |
| Email | Spring Mail / SMTP |
| AI | Gemini with a shared HTTP client, bounded requests/responses, and chunked analytics |
| Tests | JUnit, Spring test tooling, Mockito, H2, Node.js test runner |

The backend follows **Controller → Service → Repository → PostgreSQL**, organized by feature. `CourseOffering` connects a teacher, course, semester, and optional section. Schedule-derived verification retains its source records separately.

## Repository structure

```text
ai-student-feedback-system/
├── backend/
│   ├── src/main/java/kz/sdu/feedback/   # Feature packages
│   ├── src/main/resources/             # Configuration and schema migration
│   ├── src/test/                       # Backend tests
│   ├── sql/                            # Database setup and verification guide
│   ├── docs/                           # Gemini processing notes
│   └── pom.xml
├── frontend/
│   ├── src/                            # Pages, components, API clients, styles
│   ├── tests/                          # Frontend tests
│   └── package.json
└── docs/                               # Archived development notes
```

## Run locally

### Requirements

- **JDK 21** and **Maven** available in your terminal.
- **Node.js 22.12+** with npm (the installed Vite version also supports Node 20.19+).
- A running **PostgreSQL** server. Schema migration verification was performed with PostgreSQL 18.1.
- SMTP credentials for public registration emails; a Gemini API key for live moderation and AI analytics.

Commands below use **Windows PowerShell**. On macOS/Linux, use `mvn` and `npm` instead of `mvn.cmd` and `npm.cmd`.

### 1. Clone the project

```powershell
git clone https://github.com/qaldosh-dev/ai-student-feedback-system.git
cd ai-student-feedback-system
```

### 2. Configure your environment

Set these variables locally. In IntelliJ, use **Run → Edit Configurations → FeedbackApplication → Environment variables**. Terminal commands need the variables in their own environment too; they do not inherit an IntelliJ Run Configuration.

| Variable | Purpose |
| --- | --- |
| `DB_URL` | JDBC URL, for example `jdbc:postgresql://localhost:5432/student_feedback` |
| `DB_USERNAME` | Your PostgreSQL username |
| `DB_PASSWORD` | Your PostgreSQL password |
| `SMTP_HOST`, `SMTP_PORT` | Mail server address and port |
| `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` | Mail credentials and sender address |
| `SMTP_AUTH`, `SMTP_STARTTLS_ENABLED` | SMTP authentication/TLS settings; both default to `true` |
| `GEMINI_API_KEY` | Backend-only Gemini credential |
| `GEMINI_MODEL` | Model identifier; default is defined in backend configuration |
| `SCHEDULE_UPLOAD_DIR` | Optional private PDF directory; defaults to `${user.home}/.student-feedback/schedule-uploads` |

No secrets are included in this repository. A `.env` file is not automatically loaded by this backend. The frontend must never receive database, SMTP, or Gemini credentials.

Without SMTP, public registration cannot complete email delivery. Without Gemini, reviews remain submitted and AI analytics is unavailable. Public browsing and existing-account login do not require those providers.

### 3. Prepare PostgreSQL

**New installation:** create an empty PostgreSQL database, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` to that database, then run:

```powershell
cd backend
mvn.cmd -Pdb-migrations flyway:migrate
```

Follow the [database setup guide](backend/sql/README.md) for schema verification and required privileges.

**Existing database:** use the guide's existing-database adoption procedure instead. Do not run the fresh-install sequence or baseline an existing database without checking compatibility and making a backup.

Migrations create the schema only. They do not include the development database's users, reviews, schedules, or imported Moodle catalog. Moodle crawling/import never runs automatically at application startup.

### 4. Start the backend

From `backend`, after migrating a new database:

```powershell
mvn.cmd spring-boot:run "-Dspring-boot.run.profiles=db-managed"
```

The `db-managed` profile validates the schema and disables automatic sample/dev-account seeding. An empty catalog is expected on a fresh installation.

For a **local sample catalog** on an empty migrated database, use this command instead:

```powershell
mvn.cmd spring-boot:run "-Dspring-boot.run.profiles=db-managed" "-Dspring-boot.run.arguments=--app.seed.enabled=true"
```

This adds 6 fictional teachers, 6 courses, and 10 offerings only when all three catalog tables are empty. It does not create login accounts or import real Moodle data.

**IntelliJ alternative:** open the repository root, import `backend/pom.xml` as Maven, select JDK 21, configure the environment variables and active profile `db-managed`, and run `FeedbackApplication`. Existing unadopted local databases should follow the database guide before switching profiles.

### 5. Start the frontend

Open a second terminal at the repository root:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Open **http://localhost:5173**. The Vite development server forwards `/api` requests to the backend at **http://localhost:8080**.

Public API checks:

- `GET http://localhost:8080/api/teachers`
- `GET http://localhost:8080/api/courses`

To use student features, configure SMTP, register with an accessible SDU mailbox, verify the email, sign in, and select the Student role. Detailed schedule verification requires corresponding records in your local catalog.

## Tests and production builds

Backend, from `backend`:

```powershell
mvn.cmd package
```

This runs the backend test suite and creates the application JAR. Normal tests use isolated test data and mocked external providers; they do not send live email or call Gemini. PostgreSQL migration verification is a separate opt-in procedure described in the database guide.

Frontend, from `frontend`:

```powershell
node --test tests/*.test.mjs
npm.cmd run build
```

The frontend production output is written to `frontend/dist/`. The local Vite proxy is a development convenience; a deployed frontend needs `/api` routed to the backend by its hosting configuration.

## Further documentation

- [PostgreSQL setup, migrations, and existing-database adoption](backend/sql/README.md)
- [Gemini transport limits and analytics chunking](backend/docs/gemini-processing.md)
- [Historical implementation notes and local verification records](docs/development-history.md) — archived, primarily in Russian; not the current setup sequence.

Local `work/` artifacts, uploaded PDFs, environment files, and build outputs are intentionally excluded from Git. The repository provides source code and schema setup, not a copy of a student's personal data or the populated development database.
