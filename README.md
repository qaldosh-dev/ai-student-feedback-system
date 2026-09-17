# AI Student Feedback System — IntelliJ IDEA и локальный PostgreSQL

Проект уже находится в обычной папке Windows:

```text
C:\Users\User\Documents\ChatGPT\Project management main
```

Открывайте именно эту папку: она содержит **backend и frontend**. Переносить,
клонировать или создавать проект заново не нужно.

```text
Project management main/
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/kz/sdu/feedback/
│       │   ├── FeedbackApplication.java
│       │   ├── teacher/     # entity, repository, service, controller, dto
│       │   ├── course/      # entity, repository, service, controller, dto
│       │   ├── offering/    # entity, repository
│       │   └── seed/        # sample data для пустого каталога
│       └── resources/application.properties
└── frontend/
    ├── package.json
    ├── vite.config.ts
    └── src/                # API-запросы, страница каталога, компоненты, CSS
```

## 1. Открыть проект и выбрать Java

1. IntelliJ IDEA → **File → Open** (на стартовом экране — **Open**).
2. Выберите `C:\Users\User\Documents\ChatGPT\Project management main`.
3. Дождитесь индексации. Если Maven не подхватился: правой кнопкой на
   `backend/pom.xml` → **Add as Maven Project**. Альтернатива:
   **View → Tool Windows → Maven → Link Maven Projects (+)** → этот же pom.xml.
4. **File → Project Structure → Project → SDK** → **JDK 21**.
   В pom.xml указана Java 21. На компьютере уже установлен Temurin 21:
   `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`.
   Если его нет в списке: **Add SDK → JDK** → выберите эту папку.
5. В **Project Structure → Modules** для backend оставьте SDK проекта.
   В **Settings → Build, Execution, Deployment → Build Tools → Maven → Runner**
   также выберите JDK 21. Дождитесь загрузки Maven-зависимостей.

Root pom.xml не нужен: Maven-проект находится в backend. Frontend запускается
отдельно через Terminal. Файлы .idea вручную создавать не нужно.

## 2. Создать базу в своём PostgreSQL

Используется ваш существующий сервер: **localhost:5432**, база **student_feedback**.
Служба `postgresql-x64-18` работает, но проверка наличия базы потребовала пароль.
Поэтому существование базы пока не подтверждено. Пароль в чат не отправляйте.

В **pgAdmin**:

1. Откройте **Servers → ваш локальный PostgreSQL**, подключитесь своим паролем.
2. Правой кнопкой по серверу → **Properties → Connection**: проверьте
   **Host name/address = localhost**, **Port = 5432** и **Username**.
   Этот Username используйте и в IntelliJ. Часто это `postgres`, но не всегда.
   Пароль нужен от этого пользователя PostgreSQL, а не master password pgAdmin.
3. Разверните **Databases**, при необходимости нажмите **Refresh**.
4. Если `student_feedback` уже есть, не создавайте её повторно.
5. Иначе правой кнопкой по **Databases → Create → Database…**:
   **Database** = `student_feedback`, **Owner** = ваш пользователь подключения
   (например, `postgres`), затем **Save**.

Если текущему пользователю запрещено создание баз, выполните этот шаг через свой
административный аккаунт PostgreSQL. Другие базы изменять не нужно. Для уже
существующей базы подключайтесь пользователем с правом создавать таблицы в public,
например владельцем именно этой базы.

## 3. Настроить backend и ввести пароль в IntelliJ

1. Откройте `backend/src/main/java/kz/sdu/feedback/FeedbackApplication.java`.
2. **Run → Edit Configurations…** → выберите `FeedbackApplication`.
   Если её нет: **+ → Application** (также подходит **Spring Boot**, если доступен).
3. Заполните конфигурацию:
   - **Name:** `FeedbackApplication`.
   - **Run on:** локальный компьютер.
   - **JRE/JDK:** 21.
   - **Main class:** `kz.sdu.feedback.FeedbackApplication`.
   - **Use classpath of module:** Maven-модуль backend, обычно `student-feedback`.
   - **Working directory:** `C:\Users\User\Documents\ChatGPT\Project management main\backend`.
4. Найдите **Environment variables**. Если поле скрыто:
   **Modify options → Environment variables**. Кнопкой справа откройте редактор
   и добавьте три отдельные строки:

   | Name | Value |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://localhost:5432/student_feedback` |
   | `DB_USERNAME` | `postgres` или ваш Username из pgAdmin |
   | `DB_PASSWORD` | ваш настоящий пароль PostgreSQL — вводите только здесь |

   Вводите значения без кавычек. Таблица удобнее одной строки, особенно если
   пароль содержит `;`. Это **Environment variables**, не Program arguments
   и не VM options. Во frontend этот пароль не нужен.
5. Оставьте **Store as project file** выключенным. IntelliJ сохранит настройки
   локально в `.idea/workspace.xml`, исключённом из Git. Это локальное хранение,
   не обещание шифрования пароля. `.run/` и `*.run.xml` также исключены из Git.
   Настоящий пароль не вставляйте в исходники, README или общие конфигурации.
6. **Apply → OK** → выберите `FeedbackApplication` в верхнем списке запуска →
   зелёная кнопка **Run ▶**. В дальнейшем используйте эту сохранённую конфигурацию.

Настройка backend уже использует переменные окружения:

```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/student_feedback}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD}
```

Hibernate создаёт недостающие таблицы через `ddl-auto=update`. Seed одной
транзакцией добавляет **6 teachers, 6 courses и 10 course offerings**, только
если все три таблицы пусты. Существующие записи не меняет и не дублирует.
Это учебные примеры, не подтверждённые данные университета.

При успешном запуске в Run появятся `HikariPool-1 - Start completed` и
`Started FeedbackApplication`. Backend слушает порт **8080**.

## 4. Запустить frontend

IntelliJ → **View → Tool Windows → Terminal**. Из корня проекта:

```powershell
cd frontend
npm install
npm run dev
```

Зависимости уже установлены, node_modules и package-lock.json существуют;
повторный npm install обычно не нужен. На компьютере установлены Node 24 и npm.
Если PowerShell блокирует npm.ps1, используйте `npm.cmd install` и
`npm.cmd run dev`, не меняя политику выполнения PowerShell.

Если Terminal открыт в другой папке, сначала выполните:

```powershell
cd 'C:\Users\User\Documents\ChatGPT\Project management main'
```

Откройте **http://localhost:5173/**. Существующий Vite proxy передаёт `/api`
на `http://localhost:8080`; дополнительная CORS-настройка не требуется.
Frontend останавливается через **Ctrl+C** в Terminal, backend — через **Stop ■**.

## 5. Проверить свой запуск

| Адрес | Ожидаемый результат после настройки БД |
|---|---|
| http://localhost:5173/ | Teachers и Courses, по 6 sample-карточек |
| http://localhost:8080/api/teachers | JSON-массив преподавателей |
| http://localhost:8080/api/courses | JSON-массив курсов |
| http://localhost:5173/api/teachers | Тот же JSON через Vite proxy |

Адрес backend — http://localhost:8080, но страницы `/` там нет: 404 по корню
ожидаема. Для проверки используйте `/api/teachers` и `/api/courses`.

В pgAdmin выберите `student_feedback` → **Query Tool**, выполните:

```sql
SELECT 'teachers' AS entity, count(*) FROM teachers
UNION ALL
SELECT 'courses', count(*) FROM courses
UNION ALL
SELECT 'course_offerings', count(*) FROM course_offerings;
```

Для новой базы ожидаются **6 / 6 / 10**. Остановите и снова запустите backend:
количества должны остаться прежними. Если база была заполнена раньше, её
содержимое сохраняется, поэтому количества могут отличаться.

При ошибке сначала посмотрите Run backend:

- `password authentication failed` — проверьте Username и пароль в IntelliJ.
- `database "student_feedback" does not exist` — выполните шаг 2.
- `Connection refused` — проверьте службу PostgreSQL и порт 5432.
- Не задан DB_PASSWORD — заполните Environment variables нужной конфигурации.
- Порт 8080/5173 занят — остановите предыдущий запуск этого приложения.

Сборки из Terminal: в backend — `mvn.cmd package`, во frontend —
`npm.cmd run build`. Пароль для сборки не нужен, но успешная сборка сама по себе
не подтверждает соединение с БД или работу endpoint.

Инструкции сверены с документацией
[IntelliJ: Maven](https://www.jetbrains.com/help/idea/maven-support.html),
[IntelliJ: run configuration](https://www.jetbrains.com/help/idea/run-debug-configuration-java-application.html),
[IntelliJ: environment variables](https://www.jetbrains.com/help/idea/program-arguments-and-environment-variables.html),
[pgAdmin: database](https://www.pgadmin.org/docs/pgadmin4/latest/database_dialog.html),
[pgAdmin: connection](https://www.pgadmin.org/docs/pgadmin4/latest/server_dialog.html).

## Moodle: Phase 2A — предпросмотр страниц 0–4 без импорта

В IntelliJ перезагрузите Maven после изменения `backend/pom.xml`, откройте
`backend/src/main/java/kz/sdu/feedback/moodle/MoodleCatalogClient.java`
и нажмите зелёную кнопку Run рядом с его `main` (JDK 21, модуль backend).
Это отдельный консольный запуск: Spring Boot и PostgreSQL не запускаются,
переменные `DB_*` и пароль не нужны. Основное приложение по-прежнему запускается
через `FeedbackApplication`.

Клиент делает ровно пять последовательных GET к публичной категории
<https://moodle.sdu.edu.kz/course/index.php?categoryid=4&lang=ru>, добавляя
`browse=courses&perpage=20&page=0` … `page=4`, с секундной паузой между запросами.
Команда предпросмотра по-прежнему ограничена страницами 0–4;
`fetchFirstPage` из Phase 1 сохранён. Общий `fetchPage` теперь допускает
неотрицательные номера для обнаруженного полного диапазона Phase 2B.
Нет cookies, входа, редиректов, повторов запросов и записи в БД.
При ошибке загрузки или структуры выводится номер страницы и выполнение
останавливается: неполный результат не объявляется полным.

Консоль показывает число карточек, число связей, пропуски, предупреждения,
уникальные Moodle ID (отдельно среди всех карточек и среди валидных записей),
преподавателей, коды курсов, пары преподаватель/код, пять уровней анализа повторов,
до десяти групп примеров и до четырёх записей с каждой страницы.
Повторы не удаляются. Точный логический повтор определяется одинаковыми
teacherName + courseCode + courseName + semester; остальные группы отдельно
показывают разные ID, нескольких преподавателей и несколько курсов.

Парсер рассчитан на проверенную тему Moodle Alpha: карточки с
`data-region="course-content"`, заголовки `.coursename`, преподаватели в соседнем
блоке `.rui-course-card-text`. Семестр проверяется по заголовку категории.
Суффикс с именем удаляется только при совпадении с контактом преподавателя;
смысловые скобки сохраняются. `MoodleCourseRecord` дополнительно хранит
`moodleCourseId` (Long), `sourcePage` (нумерация с нуля) и `sourceTitle` (полное
исходное название с суффиксами). ID берётся из `data-course-id` и сверяется
со ссылкой курса, если она есть. Некорректный/отсутствующий/противоречивый ID
даёт null и предупреждение, а валидная связь сохраняется. Карточка без публичного
контакта преподавателя пропускается: имя из названия не подставляется.
KZ/RU пока считаются наблюдаемыми суффиксами, а не подтверждённым полем языка.
Языковые варианты могут дать одинаковые записи; они не объединяются.
Отделение и section не извлекаются. При изменении структуры нужен пересмотр
парсера; неизвестные карточки выводятся как пропущенные.

Проверка парсера без сети и БД: из `backend` выполните `mvn.cmd test`.

## Moodle: Phase 2B — полный снимок и автономный анализ

Полный обход — отдельная явная команда `MoodleCatalogCrawl.main`, а не часть
`FeedbackApplication`. Она определяет число страниц по пагинации первой страницы,
сохраняет каждую страницу в локальный кэш, ждёт секунду между запросами и не делает
автоматических повторов. Ошибка записывается в `crawl.properties` вместе с номером
страницы и `complete=false`. Повторный обход в существующий каталог запрещён,
чтобы случайно не скачать весь Moodle заново. Кэш и отчёты находятся под `work/`,
который уже исключён из Git.

Снимок Fall 2026 уже сохранён. Для обычной повторной проверки **используйте кэш**:
откройте `MoodleCatalogExport.java`, создайте обычную Application Run Configuration
для его `main`, JDK 21, модуль backend. В **Program arguments** укажите:

```text
"C:\Users\User\Documents\ChatGPT\Project management main\work\moodle-cache\fall-2026" "C:\Users\User\Documents\ChatGPT\Project management main\work\moodle-analysis"
```

Эта команда читает только локальные файлы, не использует HTTP или PostgreSQL и
обновляет `fall-2026-records.csv`, `fall-2026-cards.csv`, `fall-2026-summary.md`
и `fall-2026-diagnostics.txt`. Исходные HTML остаются неизменными. Неполный кэш
или отсутствующая страница останавливают анализ. CSV хранит все связи, а отдельный
CSV карточек — в том числе причины пропусков. Ничего не объединяется навсегда.

Только для отдельно согласованного нового снимка можно запустить
`MoodleCatalogCrawl.java` с аргументами `crawl "<новый пустой каталог кэша>"`.
При штатном анализе существующего снимка запускать эту команду не нужно.

В памяти дополнительно хранится `moodleTeacherId`, полученный из публичной ссылки
контакта `/user/profile.php?id=...` или `/user/view.php?id=...`; сами профили не
запрашиваются. Совпавшие имена с разными ID остаются отдельными контактами.
Отсутствие ID не даёт права объединять людей по имени. Группы для проверки
считаются по ID источника; приведение регистра/пробелов используется только в
отдельных сравнительных метриках. Суффиксы KZ/RU и похожие на имена скобки —
признаки для ручной проверки, не подтверждённые поля языка или преподавателя.

Все тесты остаются автономными: `mvn.cmd package` не обращается к Moodle и не
запускает БД. Сущности, seed, API и frontend в Phase 2B не изменяются.

## Moodle: Phase 3 — модель и полный offline dry-run

`Teacher.department` и `CourseOffering.section` теперь допускают null в JPA.
В Teacher добавлен nullable `Long moodleTeacherId` с уникальностью ненулевых ID.
В CourseOffering добавлен nullable `Long moodleCourseId`; уникальна комбинация
`moodle_course_id + teacher_id + semester`, а не Moodle course ID отдельно.
Старая уникальность teacher/course/semester/section сохранена. В PostgreSQL null
section позволяет хранить несколько разных Moodle карточек одной логической связи.
Если позднее назначать им одинаковый section, потребуется отдельное решение по
согласованию этих связей. `Course.code` остаётся уникальным, Course не менялся.
Старые конструкторы seed сохранены; публичные DTO не включают source ID.

Для повторного dry-run откройте `MoodleImportDryRun.java` и запустите его `main`
в IntelliJ (Application, JDK 21, модуль backend). Program arguments:

```text
"C:\Users\User\Documents\ChatGPT\Project management main\work\moodle-cache\fall-2026" "C:\Users\User\Documents\ChatGPT\Project management main\work\moodle-analysis\fall-2026-phase3-dry-run.md"
```

Spring Boot, пароль и PostgreSQL этой команде не нужны. Она читает 88 сохранённых
HTML-страниц и перезаписывает только указанный отчёт под игнорируемым `work/`.
Повторного crawl нет. Mapper не использует JPA entities, repositories или save.
Кандидаты — план соответствий источника, **не список новых строк после сравнения
с существующей БД**. У существующего seed могут совпасть коды, но отличаться названия.

Правила: Teacher по Moodle teacher ID с проверкой имени; Course по коду после
нормализации регистра/пробелов с проверкой названия; Offering по Moodle course ID,
teacher ID и semester. Разные Moodle course ID сохраняются отдельно. Department
и section не выдумываются. Неоднозначные записи исключаются из Offering candidates.
Три проверенных личных суффикса (LAW 190/916, TFL 371/722, CSS 416/51) удаляются
только при точном совпадении ID карточки, контакта, кода и текста. Исходный title
сохраняется. Это дополнение к прежнему правилу удаления совпадающего имени контакта,
а не универсальное распознавание имён/псевдонимов. IBCTL 201 (PYP/MYP) остаётся
неразрешённым: обе его связи требуют ручной проверки.

### Физическая схема PostgreSQL: требуется отдельная проверка

Изменение nullable в Java **не доказывает**, что существующие столбцы уже допускают
null. В используемом Hibernate 7.4.5 `StandardTableMigrator` добавляет отсутствующие
столбцы и меняет тип/длину, но не снимает NOT NULL с существующих department/section.
В Phase 3 приложение не перезапускалось для изменения схемы; SQL ниже не выполнялся.

В pgAdmin выберите свою БД `student_feedback` → Tools → Query Tool. Сначала выполните
только проверку (пароль вводится в pgAdmin, не в код или чат):

```sql
SELECT table_name, column_name, is_nullable, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('teachers', 'courses', 'course_offerings')
ORDER BY table_name, ordinal_position;

SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid IN ('public.teachers'::regclass, 'public.courses'::regclass,
                  'public.course_offerings'::regclass);

SELECT 'teachers' AS table_name, count(*) FROM public.teachers
UNION ALL SELECT 'courses', count(*) FROM public.courses
UNION ALL SELECT 'course_offerings', count(*) FROM public.course_offerings;
```

Для старой схемы минимальное изменение nullability (после проверки, при остановленном
backend) — эти две команды; они не удаляют значения department/section:

```sql
BEGIN;
ALTER TABLE public.teachers ALTER COLUMN department DROP NOT NULL;
ALTER TABLE public.course_offerings ALTER COLUMN section DROP NOT NULL;
COMMIT;
```

Новые nullable-столбцы и именованные UNIQUE-ограничения описаны в JPA и должны
появиться при следующем запуске с `ddl-auto=update`; проверьте результат запросом
выше. Если их нет, эквивалентные ручные команды приведены ниже. Выполняйте только
отсутствующие изменения, после проверки отсутствия конфликтующих ненулевых ID;
не удаляйте существующие ограничения и не пересоздавайте таблицы:

```sql
ALTER TABLE public.teachers ADD COLUMN IF NOT EXISTS moodle_teacher_id bigint;
ALTER TABLE public.course_offerings ADD COLUMN IF NOT EXISTS moodle_course_id bigint;
ALTER TABLE public.teachers ADD CONSTRAINT uk_teachers_moodle_teacher_id
    UNIQUE (moodle_teacher_id);
ALTER TABLE public.course_offerings ADD CONSTRAINT uk_offerings_moodle_teacher_semester
    UNIQUE (moodle_course_id, teacher_id, semester);
```

Обычная UNIQUE в PostgreSQL допускает несколько null, поэтому seed не требует
фиктивных source ID. После изменений повторите проверку: department/section и
source ID должны иметь `is_nullable=YES`; seed — 6/6/10 строк. Импорта при запуске
FeedbackApplication нет. Реальный импорт и согласование seed отложены до Phase 4.

### Phase 4A — физическая схема проверена 17 сентября 2026

На `localhost:5432/student_feedback` выполнена проверка PostgreSQL 18.1.
Оба Moodle BIGINT-столбца уже существовали, допускали null и имели нужные UNIQUE.
Потребовались только следующие изменения, выполненные одной транзакцией:

```sql
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15s';
ALTER TABLE public.teachers ALTER COLUMN department DROP NOT NULL;
ALTER TABLE public.course_offerings ALTER COLUMN section DROP NOT NULL;
COMMIT;
```

Теперь department, section и оба source ID физически допускают null.
Сохранены `uk_teachers_moodle_teacher_id` — UNIQUE (moodle_teacher_id),
`uk_offerings_moodle_teacher_semester` — UNIQUE (moodle_course_id, teacher_id, semester),
`uke7loucl4tpvp9i246mackny6v` — UNIQUE (teacher_id, course_id, semester, section),
`uk61og8rbqdd2y28rx2et5fdnxd` — UNIQUE (code). PK/FK и индексы сохранены.

Текущий собранный backend успешно запущен с обычным `ddl-auto=update`; сравнение
снимков до/после запуска не выявило дополнительных изменений схемы. Все строки
seed полностью совпали: 6 Teachers, 6 Courses, 10 CourseOfferings; source ID null.
Maven package и 28 тестов прошли. Выполнены 19 API-проверок напрямую и 19 через
Vite proxy; HTML и React-модуль доступны. Визуальная проверка браузером не проводилась.
Moodle не запрашивался, импорт не выполнялся. Повторять ALTER для этой БД не нужно.
Подробные локальные свидетельства находятся под игнорируемым `work/phase4a-*`.

## Полный утверждённый импорт Fall 2026

17 сентября 2026 выполнен полный импорт из существующего локального кэша:
500 Moodle Teachers, 898 Course candidates, 1731 Moodle CourseOfferings.
Создано 500 Teachers, 897 Courses, 1731 Offerings; существующий CSS 465 —
Project Management переиспользован. Seed сохранён. Итог: **506 / 903 / 1741**.
Второй запуск создал **0 / 0 / 0** строк. IBCTL 201 (две связи), 30 карточек без
контакта и одна нераспознанная карточка остались исключены. Запросов к Moodle нет.

Импорт запускается **только явно** через `MoodleImportCommand.main`, отдельно от
обычного `FeedbackApplication`. В IntelliJ создайте Application-конфигурацию:
main class `kz.sdu.feedback.moodle.MoodleImportCommand`, JDK 21, модуль backend,
те же локальные DB_USERNAME/DB_PASSWORD и при необходимости DB_URL.
Пароль вводится только в Environment variables, не в Program arguments.

Сначала Program arguments для проверки без записи:

```text
preflight "C:\Users\User\Documents\ChatGPT\Project management main\work\moodle-cache\fall-2026" "C:\Users\User\Documents\ChatGPT\Project management main\work\moodle-analysis"
```

Для явно разрешённого импорта замените только `preflight` на `import`.
В данной БД импорт уже выполнен; повторный запуск должен только переиспользовать записи.
Каждый запуск создаёт отдельный `fall-2026-preflight-*.txt` или `fall-2026-import-*.txt`
под игнорируемым work/. Команда проверяет утверждённые количества кандидатов,
читает кэш, отключает seed и использует `ddl-auto=validate` без изменения схемы.

Путь выполнения: явная команда → MoodleCatalogImportService → существующие JPA
repositories. Преподаватели сопоставляются по Moodle teacher ID, курсы — по коду
с проверкой названия по правилам Phase 3, Offering — по Moodle card ID/teacher/semester.
По имени seed-преподаватели не объединяются. Существующие значения не обновляются.
Неожиданные конфликты останавливают импорт до записи. Сервис повторяет preflight
внутри одной SERIALIZABLE-транзакции; ошибка вставки откатывает весь импорт и
сообщает источник ошибки. Автоматических повторов после ошибки нет.

Обычный старт FeedbackApplication ничего не импортирует. Перед сборкой нового JAR
остановите запущенный из него backend: Windows может удерживать файл.
Проверка: `mvn.cmd package`. Добавленные JPA-тесты используют H2 только в памяти
(test dependency), включая реальный rollback после ошибки второй вставки;
рабочая PostgreSQL и live Moodle в тестах не используются.

Post-import проверка завершена: все 1731 связи совпали с кэшем, нормальный запуск
не изменил БД, прошли 64 API-проверки напрямую и через Vite. В браузере проверены
полный каталог 506/903, реальные имена/коды, связанные фильтры, очистка и empty states;
ошибок консоли нет. Итоговая сборка: 36 тестов, BUILD SUCCESS.
Полный локальный отчёт: `work/moodle-analysis/fall-2026-full-import-report.md`.
Известное косметическое замечание: старая надпись Sample catalog осталась в UI,
хотя каталог теперь содержит Moodle + seed. Frontend в рамках импорта не менялся.
