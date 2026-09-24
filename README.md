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

## Authentication — Phase 1

Аккаунты хранятся отдельно от каталога: `User` → таблица `users`, набор `UserRole`
→ `user_roles`. Поля: id, email, passwordHash, emailVerified, roles,
linkedTeacher (необязательная ссылка), createdAt (Instant). Email приводится к
`strip().toLowerCase(Locale.ROOT)` при создании User; repository принимает уже
нормализованный email. PostgreSQL обеспечивает UNIQUE email и UNIQUE linked_teacher_id.
Роли STUDENT, TEACHER, ADMIN сохраняются строками, у аккаунта может быть несколько ролей.
Обычный User создаётся с emailVerified=false. Ограничения SDU-доменов будут в будущей
регистрации, а не в этой сущности.

Добавлен только `spring-security-crypto` и PasswordEncoder (BCrypt). Фильтров
авторизации нет: каталог остаётся публичным. User API, login, сессии и регистрация
в этой фазе отсутствуют. Нельзя передавать сырой пароль в конструктор User:
сначала используйте PasswordEncoder.encode(...).

### Локальные тестовые аккаунты

Инициализация выключена по умолчанию. Для явного запуска в IntelliJ:
Run → Edit Configurations → FeedbackApplication → Environment variables:

- `SPRING_PROFILES_ACTIVE=dev`
- `DEV_ACCOUNTS_ENABLED=true`
- Необязательно: `DEV_STUDENT_PASSWORD`, `DEV_TEACHER_PASSWORD`, `DEV_ADMIN_PASSWORD`.
  Значения задавайте локально в этом окне, не в исходниках/README/чате.
  Не включайте сохранение конфигурации запуска как общего project file.

Затем Run. Создаются только отсутствующие аккаунты:

| Email | Роль | Email verified | Teacher |
|---|---|---|---|
| student@feedback.test | STUDENT | true | нет |
| teacher@feedback.test | TEACHER | true | Aida Sarsenova, исходный mock |
| admin@feedback.test | ADMIN | true | нет |

Если переменная пароля отсутствует/пустая, генерируется случайный секрет из 32
криптографически случайных байтов, сохраняется только BCrypt-хеш, а секрет
отбрасывается без вывода. Такой аккаунт подготовлен для проверки модели, но
известного человеку пароля у него нет. Для известного тестового пароля переменная
должна быть задана ДО первого создания аккаунта. Повторный запуск никогда не
сбрасывает пароль и не меняет роли/ссылки существующего аккаунта. Смена пароля
не реализована в Phase 1.

В конце Phase 1 аккаунты были созданы со случайными отброшенными секретами.
Это не готовый механизм входа. После проверки уберите обе включающие переменные
(или задайте DEV_ACCOUNTS_ENABLED=false). Обычный запуск аккаунты не создаёт.

Seed каталога выполняется раньше seed аккаунтов; все три создания проходят в
одной транзакции. Teacher ищется по точному mock-имени/department и отсутствующему
Moodle ID. При отсутствии или неоднозначности mock seed откатывается. Импортированные
Teacher не изменяются и не связываются с тестовым аккаунтом.

Проверки: `mvn.cmd package`. UserFoundationTest использует отдельную H2 в памяти,
проверяет уникальность, роли, BCrypt, повторный seed, сохранение существующего
аккаунта и rollback при отсутствии mock. Локальная PostgreSQL тестами не используется.

Локальная проверка Phase 1 завершена: PostgreSQL содержит 3 Users / 3 role rows.
Повторный dev-запуск и последующий обычный запуск не изменили аккаунты, хеши или
каталог. Все 506 Teachers / 903 Courses / 1741 CourseOfferings и прежняя схема
каталога совпали с исходным снимком. Проверены 32 сценария API и оба endpoint
без query-параметров, без авторизации. Maven package: 47 тестов, 0 failures/errors.
Первое создание таблиц дало только Hibernate notices «constraint ... does not exist,
skipping» перед созданием новых UNIQUE constraints; фактические ограничения проверены.
Локальный подробный отчёт: `work/auth-phase1-report.md`.

## Authentication — Phase 2

Sign In / Sign Out теперь доступны на существующей Browse page. Регистрации,
email-отправки, teacher claim и ограничений каталога по ролям пока нет.

### Уже настроенные локальные аккаунты

Пароли трёх @feedback.test аккаунтов один раз явно установлены из локальных
переменных DEV_STUDENT_PASSWORD, DEV_TEACHER_PASSWORD, DEV_ADMIN_PASSWORD.
Используйте свой соответствующий пароль, заданный в Windows; его значения в проекте нет.
Обычный FeedbackApplication пароли не меняет. Повторять setup для каждого входа не нужно.

Если впоследствии потребуется явно заменить только эти development-пароли:

1. В IntelliJ создайте отдельную Application run configuration для
   `kz.sdu.feedback.user.seed.DevelopmentPasswordSetupCommand`, module backend, JDK 21.
2. В Program arguments укажите `--local-development`.
3. В Environment variables предоставьте DB_USERNAME, DB_PASSWORD (как для обычного
   backend) и DEV_STUDENT_PASSWORD, DEV_TEACHER_PASSWORD, DEV_ADMIN_PASSWORD.
   DB_URL необязателен; команда разрешает только jdbc:postgresql://localhost:5432/student_feedback.
   Значения вводите локально, не сохраняйте конфигурацию как общий project file.
   Если IntelliJ открыт до создания Windows-переменных, перезапустите IDE либо
   задайте их в этой локальной run configuration.
4. Запустите команду один раз. Она завершится сама. Все три переменные обязательны,
   пустые значения и пароли длиннее 72 байт UTF-8 отклоняются.

Команда не является startup runner. Она отключает оба seed, использует schema
validation и одну транзакцию. Проверяет точные email, роли, verified и mock Teacher
Aida Sarsenova ID 1 без Moodle ID, затем меняет только три хеша BCrypt.
Она не создаёт аккаунты, не меняет роли/связи и не затрагивает другие адреса.
Это локальный инструмент разработки, а не публичный password-reset API.

Обычный запуск: FeedbackApplication с прежними DB-переменными; затем в терминале
из корня `cd frontend`, `npm.cmd run dev`. Откройте http://localhost:5173 и нажмите
Sign In. Для ежедневного запуска команды setup не нужны.

### API и сессии

| Endpoint | Результат |
|---|---|
| GET /api/auth/csrf | 200: headerName и CSRF token; создаёт анонимную сессию при необходимости |
| POST /api/auth/login | JSON email/password + CSRF header; 200 с безопасным User DTO либо 401 |
| GET /api/auth/me | 200 с текущим User DTO; без авторизованной сессии 401 |
| POST /api/auth/logout | CSRF header; 204, сессия инвалидируется, cookie истекает |

DTO: id, email, roles, emailVerified, linkedTeacher (null либо id/name).
passwordHash, session ID и внутренние credentials в JSON не возвращаются.
Неизвестный email, неверный пароль и неподтверждённый аккаунт получают один общий
401: Invalid email or password. Email нормализуется (strip + lowercase).
Отсутствующий/неверный CSRF token даёт 403 ещё до проверки credentials.
GET logout не завершает сессию; публичного GET endpoint для него нет.

Spring Security DaoAuthenticationProvider проверяет BCrypt и verified-состояние.
После входа controller меняет session ID, обновляет CSRF и явно сохраняет
SecurityContext в HttpSessionSecurityContextRepository. Logout выполняет стандартный
Spring Security handler. Несколько независимых сессий допустимы: выход завершает
только текущую сессию.

JSESSIONID: HttpOnly=true, SameSite=Lax, idle timeout=30 минут, tracking только через
cookie. Для локального HTTP Secure=false; для production HTTPS задайте
SESSION_COOKIE_SECURE=true. Сессии хранятся на сервере; refresh страницы сохраняет
вход, но сохранение входа между перезапусками backend не гарантируется.

CSRF включён для state-changing запросов, включая login/logout. React получает
свежий token через /api/auth/csrf перед каждым POST и передаёт его в заголовке,
указанном headerName (X-CSRF-TOKEN). После входа старый token недействителен.
Токен CSRF не является паролем или session ID. Пароли и токены не сохраняются
в localStorage/sessionStorage. Vite /api proxy оставлен без изменений.

AccountArea хранит только currentUser и состояние интерфейса в React state.
На загрузке страницы вызывает /me; 401 означает обычное состояние без входа.
Есть loading, retry при ошибке проверки сессии, форма Email/Password, общий текст
ошибки, email/роли и Sign Out. Поле пароля очищается при отправке.

### Проверки Phase 2

Maven package: BUILD SUCCESS, 57 тестов (47 прежних + 10 AuthSessionTest), без ошибок.
Frontend: npm.cmd run build — успешно. Тесты используют H2 в памяти без live Moodle.
Реальные HTTP-проверки подтвердили все три роли, linkedTeacher ID 1, нормализацию,
CSRF, смену ID сессии, независимые сессии, выход и отклонение старого cookie.
Неподтверждённый пользователь проверен только в изолированной тестовой БД.

Браузер: STUDENT вошёл с локальным паролем, refresh сохранил вход; связанные фильтры
Zhasmin + MDE 153 дали 1/1; Sign Out и следующий refresh сохранили состояние выхода.
Неверный вход показывает общий текст ошибки. Teacher/Admin проверены реальным HTTP.
Каталог остался 506 / 903 / 1741; все его строки и схема сравнивались с исходным
снимком. После setup хеши аккаунтов не менялись при запуске/входе/выходе.

Проверочный скрипт сначала ошибочно требовал Max-Age=0 при logout. Tomcat корректно
удаляет cookie через Expires с датой в прошлом; проверка исправлена, код приложения
не менялся. Cookies jar удаляет cookie, старый cookie получает /me -> 401.
Подробный локальный отчёт: work/auth-phase2-report.md.

## Authentication — Phase 3

Защищённые области: `/student`, `/teacher`, `/admin`. После входа навигация показывает
только области из набора ролей аккаунта. Общий AuthProvider хранит прежнее состояние
сессии для AccountArea, навигации и одного ProtectedRoute; общая шапка доступна на
всех страницах. Каталог по `/` остаётся публичным.

| Пользователь | GET /api/student/home | GET /api/teacher/home | GET /api/admin/home |
|---|---:|---:|---:|
| Без входа | 401 | 401 | 401 |
| STUDENT | 200 | 403 | 403 |
| TEACHER | 403 | 200 | 403 |
| ADMIN | 403 | 403 | 200 |
| STUDENT + TEACHER | 200 | 200 | 403 |

SecurityFilterChain проверяет роли для всего соответствующего `/api/<role>/**`.
ADMIN не получает другие роли автоматически. Это реальная серверная граница
доступа; скрытые ссылки и ProtectedRoute отвечают только за удобство интерфейса.
401/403 возвращаются в JSON без перенаправления на HTML login. Прежняя проверка
CSRF для login/logout сохранена; сообщение при ошибке CSRF отличается от отказа по роли.

Area endpoints возвращают только area, email, roles, linkedTeacher. Для Teacher
связь берётся из текущего аккаунта: teacher@feedback.test связан с Aida Sarsenova,
ID 1. Если связи нет, область доступна по роли, linkedTeacher=null и интерфейс
сообщает об отсутствии связи. Чужой профиль не подставляется. Полноценных
дашбордов и функций этих областей пока нет.

Для проверки откройте http://localhost:5173/student, войдите существующим
student@feedback.test, обновите страницу, затем откройте `/admin`: будет Access Denied.
Sign Out сразу закрывает защищённую область; повторный refresh требует входа.
Аналогично проверяются teacher@feedback.test на `/teacher` и admin@feedback.test
на `/admin`. Локальные пароли не изменены; повторять dev-password setup не нужно.

Сборка backend: `cd backend`, `mvn.cmd package` (JDK 21).
Сборка frontend: `cd frontend`, `npm.cmd run build`.
Обычный запуск FeedbackApplication и `npm.cmd run dev` остаётся прежним.
Новые проверки ролей работают offline на изолированном H2, включая multi-role
пользователя и преподавателя без связи; дополнительные аккаунты в PostgreSQL
не создаются. Подробные результаты: `work/auth-phase3-report.md` (локальный,
игнорируемый Git отчёт). Moodle crawling/import в этой фазе не запускается.

## Authentication — Phase 4: registration and email verification

> История Phase 4. Правила назначения ролей и статус SMTP ниже описывают тот этап.
> Актуальное поведение изменено в Phase 4.1 (раздел ниже): оба домена подтверждают
> только email, новые роли не назначаются. Реальная доставка уже подтверждена пользователем.

Откройте `/register`. Публичная регистрация принимает только точные домены
`stu.sdu.edu.kz` и `sdu.edu.kz`: пробелы по краям удаляются, регистр понижается,
синтаксис email проверяется. Сторонние домены, поддомены, поддельные суффиксы и
новые `@feedback.test` адреса отклоняются. Старые dev-аккаунты продолжают входить.

Поток: Register → Verify Email → Sign In. Пароль: минимум 8 символов,
максимум 72 UTF-8 байта (ограничение BCrypt; не-ASCII символы могут занимать
несколько байт). Confirm Password проверяется интерфейсом; backend повторно
проверяет собственную политику пароля. Выбора роли нет.

| POST endpoint | JSON fields | Результат |
|---|---|---|
| `/api/auth/register` | email, password | 202 с безопасным message; до подтверждения roles=[], emailVerified=false, linkedTeacher=null |
| `/api/auth/verify-email` | email, code | 200: verified=true, accountType=STUDENT либо STAFF |
| `/api/auth/resend-verification` | email | 202 с безопасным message; отправка только подходящему неподтверждённому аккаунту вне cooldown |

Все три POST требуют прежний CSRF header из `/api/auth/csrf`. Неверный домен,
пароль, лишние поля запроса или код дают 400; отсутствующий/неверный CSRF — 403.
Ошибка отправки письма — 503. Конкурирующий INSERT с конфликтом уникального
email даёт безопасный 409. Хеши и коды никогда не возвращаются API.

После правильного кода студент получает только STUDENT. Staff подтверждается
с пустым набором ролей и без linkedTeacher; вход и `/me` доступны, а все role
areas возвращают 403. Интерфейс объясняет, что настройка Teacher ещё не выполнена.
Автоматического входа, TEACHER/ADMIN назначения или Teacher Claim нет.

Шестизначный код создаётся SecureRandom. Таблица `email_verifications` хранит
только BCrypt-хеш, время создания/истечения/использования и число попыток.
Срок — 10 минут, максимум 5 неверных попыток, resend — не чаще 60 секунд.
Для пользователя хранится одна запись; resend заменяет challenge и делает старый
код недействительным. Параллельные verify/resend сериализуются блокировкой User
в транзакции. Неверные попытки сохраняются, а успешный код используется один раз.

Повторная регистрация не меняет существующий пароль, роли или аккаунт и не
отправляет повторное письмо. Можно перейти в Continue email verification и
запросить resend. Для verified/unknown адреса и во время cooldown resend отвечает
тем же безопасным 202, но ничего не отправляет. Поэтому 202 не является
подтверждением доставки или существования адреса. При SMTP-ошибке регистрация
откатывается; неуспешный resend сохраняет предыдущий challenge.

### SMTP: требуется локальная настройка

Реальная доставка **не тестировалась**: SMTP не настроен. Тесты используют mock
JavaMailSender, не отправляют писем и перехватывают код только в памяти теста.
Обычный backend без SMTP запускается, каталог и существующий вход работают;
новая регистрация возвращает понятную ошибку отправки, не оставляя аккаунт в БД.

Нужные имена переменных:

- `SMTP_HOST`
- `SMTP_PORT`
- `SMTP_USERNAME`
- `SMTP_PASSWORD`
- `SMTP_FROM`
- `SMTP_AUTH`
- `SMTP_STARTTLS_ENABLED`

Когда будете готовы к живой проверке, получите параметры у выбранного SMTP
провайдера и задайте их **локально** в IntelliJ: Run → Edit Configurations →
FeedbackApplication → Environment variables. Существующие DB-переменные оставьте.
Не сохраняйте секреты в исходниках, README, общей run configuration или чате.
Перезапустите backend, затем выполните одну регистрацию на своём доступном SDU
ящике и введите полученный код только в приложении. Отправитель может иметь
любой домен: подтверждается получение письма именно адресатом SDU.

Реализация использует Spring Mail/JavaMailSender, ограниченные SMTP timeouts,
STARTTLS с обязательным согласованием при включении, без mail-debug и без
логирования содержимого письма. Письмо содержит код и срок действия, не пароль.
SMTP и PostgreSQL не образуют общую транзакцию: редкий сбой commit после принятия
письма SMTP может оставить доставленный, но недействительный код; повторная
регистрация/resend выполняется через обычный интерфейс. Outbox/очередь не добавлялись.

### Проверки Phase 4

Maven package: 105 тестов, 0 failures/errors/skipped (63 прежних + 42 новых).
Student/Staff, empty roles, BCrypt, запрет клиентских ролей/связей, повторная
регистрация, expiry/attempts/resend, rollback, конкуренция, CSRF и вход проверены
offline с H2 и mock mail sender. Frontend build прошёл.
Некорректный JSON регистрации/verify/resend получает общий 400 без логирования
ошибки парсера: её подробности могли бы содержать фрагменты входных секретов.

Локально прошли 17 HTTP-проверок регистрации без SMTP, 65 прежних проверок
сессий/CSRF, 46 проверок role access и 64 проверки каталога. Каталог остался
506 Teachers / 903 Courses / 1741 CourseOfferings, аккаунты и хеши не изменились.
Новая таблица email_verifications пуста; реальных SDU-аккаунтов пока не создано.

Браузер: формы, домен, несовпадающие пароли, ошибка отсутствующего SMTP, ошибка
кода, resend countdown, переход к Sign In и публичный поиск проверены. Успешный
email round-trip и success-экраны после реальной доставки пока не проверены в
браузере; backend-сценарии успеха проверены fake mail sender. Подробный отчёт:
`work/auth-phase4-report.md`. Teacher Claim в этой фазе не реализован.

## Authentication — Phase 4.1: email does not determine a role

Оба точных домена `stu.sdu.edu.kz` и `sdu.edu.kz` по-прежнему разрешены. Домен
подтверждает только доступ к SDU-почте, а не статус студента или преподавателя.

После правильного кода новый аккаунт имеет `emailVerified=true`, `roles=[]`,
`linkedTeacher=null` для **обоих** доменов. `POST /api/auth/verify-email` теперь
возвращает только `{"verified":true}`: поле accountType удалено, поскольку оно
ошибочно классифицировало пользователя по домену. Метод User.verifyEmail() меняет
только флаг подтверждения и не очищает/не назначает роли. Уже существующие роли,
связи и dev-аккаунты не меняются; миграций и startup-пересчёта ролей нет.

Подтверждённый пользователь без ролей может войти и получить `/api/auth/me` с
пустым массивом roles. Student/Teacher/Admin endpoints возвращают 403 до отдельной
настройки ролей. Страницы регистрации, успешного подтверждения и информация об
аккаунте больше не определяют Student/Staff по домену. Успех: “Email verified
successfully. Your account is verified. Role setup is the next step.” Затем Sign In.

SecureRandom, BCrypt-хеш кода, срок 10 минут, 5 попыток, resend 60 секунд, CSRF,
транзакции, блокировки и отправка писем не менялись. Пользователь подтвердил
успешную **реальную** SMTP-доставку до Phase 4.1. Эта коррекция не перенастраивает
SMTP и не требует повторной отправки; регрессия отправки проверяется mock sender.

Следующий отдельно согласуемый этап — Phase 5: Role Selection + Teacher Claim.
Выбор Student назначит STUDENT; Teacher Claim должен связать аккаунт с конкретным
существующим преподавателем и назначить TEACHER по согласованным правилам защиты
от присвоения чужого профиля. Кнопки выбора, claim endpoints и выдача ролей в
Phase 4.1 **не реализованы**. Подробный отчёт: `work/auth-phase4-1-report.md`.

Проверка Phase 4.1: Maven package — 106 тестов без ошибок; frontend build прошёл.
После перезапуска в IntelliJ прошли 46 role-access, 65 session/CSRF и 64 catalog
HTTP-проверки. Все существующие аккаунты/роли и каталог 506 / 903 / 1741 сохранены.

## Authentication — Phase 5: Role Selection + Teacher Claim

Текущий flow: Register → Verify Email → Sign In → **Account setup**.
Оба SDU-домена по-прежнему дают только подтверждённую почту и пустые роли.
На `/account-setup` пользователь отдельно выбирает Student и/или Teacher.
Выбор роли Admin отсутствует.

Оба запроса требуют текущую сессию, подтверждённую почту и CSRF-токен из
`GET /api/auth/csrf`:

- `POST /api/account/roles/student` с `{}` (или без тела): добавляет STUDENT,
  сохраняя остальные роли. Повторный запрос возвращает текущее состояние.
- `POST /api/account/teacher-claim` с `{"teacherId":123}`: связывает аккаунт
  с существующим Teacher и добавляет TEACHER. Поиск использует прежний
  `GET /api/teachers?q=...`. В UI перед запросом показывается подтверждение.

Ответ 200 — существующий CurrentUser DTO: id, email, roles, emailVerified,
linkedTeacher (id/name или null). Посторонние поля запроса отклоняются (400).
Нет сессии → 401; email не подтверждён → 403; отсутствующий Teacher → 404;
профиль занят или попытка сменить уже связанный профиль → 409. Без CSRF POST
возвращает 403 ещё до контроллера. Владелец занятого профиля не раскрывается.

Изменения выполняются транзакционно: сначала блокируется текущий User, затем
выбранный Teacher. UNIQUE `users.linked_teacher_id` остаётся последней защитой
от двух владельцев. Блокировка Teacher не меняет данные каталога. Повторный
claim того же Teacher разрешён, смена/unclaim не реализованы.

После commit обновляются authorities текущей HTTP-сессии, её ID и CSRF-токен;
frontend обновляет общий AuthContext из ответа. Повторный вход не требуется.
Другие уже открытые независимые сессии получают новые authorities при следующем
входе или успешном запросе setup в той сессии. STUDENT + TEACHER дают доступ
к обоим разделам, но не к Admin Area. Роль STUDENT сама по себе не подтверждает
Enrollment и не даёт будущего права оставлять отзывы о произвольных курсах.

**Принятое ограничение MVP:** подтверждённая SDU-почта не доказывает принадлежность
Teacher-профиля. Пользователь может выбрать чужой свободный профиль. В этой фазе
нет Admin approval, SSO или официального подтверждения сотрудника; для production
нужен отдельный доверенный способ проверки личности.

Тесты claim/конкурентности используют только H2. Браузерный сценарий проверяется
на текущем backend и собранном frontend с отдельными временными H2-аккаунтами;
реальные профили из PostgreSQL не присваиваются. SMTP/verification и Moodle import
не менялись. Подробные результаты: `work/auth-phase5-report.md` (локальный отчёт).
