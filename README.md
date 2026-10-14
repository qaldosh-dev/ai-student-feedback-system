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
## Sprint 2 — US13: Upload Schedule PDF

В Student Area (`/student`) добавлена форма **Upload Schedule**: выбор одного PDF,
Upload, состояния загрузки, успеха и ошибки. STUDENT + TEACHER также разрешён;
TEACHER/ADMIN без STUDENT не могут загружать файл. Пустой выбор блокирует кнопку.

`POST /api/student/schedule-uploads` принимает `multipart/form-data` с единственным
полем `file`. Нужны существующая HTTP-сессия STUDENT и CSRF-заголовок из
`GET /api/auth/csrf`. Не отправляйте userId/email/другие поля — они отклоняются.
Владелец определяется сервером по сессии. Ответ 201 содержит только:

```json
{
  "id": 1,
  "originalFilename": "Schedule.pdf",
  "uploadedAt": "2026-09-24T16:35:49Z",
  "status": "UPLOADED"
}
```

Новая таблица `schedule_uploads`: id, user_id (FK users), original_filename,
storage_key (UNIQUE), uploaded_at и status. Hibernate создаёт её при обычном запуске;
существующие Users/Teachers/Courses/CourseOfferings не изменяются.

Файлы хранятся отдельно от кода и статических ресурсов. Значение по умолчанию:
`${user.home}/.student-feedback/schedule-uploads` — на этом Windows-компьютере
`C:\Users\User\.student-feedback\schedule-uploads`. Оно не зависит от того,
запускается backend из корня проекта или из папки backend.
Необязательная переменная `SCHEDULE_UPLOAD_DIR` задаёт другую **приватную** папку;
не размещайте её в frontend/public, src/main/resources или публичной web-папке.
Для смены папки существующие файлы нужно сохранить и перенести отдельно;
автоматической миграции файлов здесь нет.

Внутреннее имя — UUID.pdf, исходное имя используется только как метаданные
(без Windows/Unix префиксов пути). CREATE_NEW не перезаписывает файлы.
Папки `schedule-uploads/` и `work/` игнорируются Git. Реальный образец
`work/schedule-samples/Schedule.pdf` не входит в исходники или сборку.
Нет публичного endpoint скачивания и нет статического доступа к файлам.

Лимиты: файл **10 MiB** (10 MB в UI), весь multipart-запрос **11 MiB**.
Backend проверяет наличие ровно одного файла, непустое содержимое, размер и
суффикс `.pdf`; MIME/расширение не считаются доказательством настоящего PDF.
400 — ошибочные поля/пустой файл/имя; 413 — превышение лимита;
401 — нет входа при корректном CSRF; 403 — неверная роль или CSRF;
503 — ошибка локального хранения. Форма использует прежний CSRF-механизм,
а multipart Content-Type/boundary выставляет браузер.

При обычной ошибке записи/откате транзакции файл удаляется. Файловая система и
PostgreSQL не являются одной атомарной транзакцией: аварийное завершение процесса
может оставить файл без записи; автоматическая очистка таких файлов пока не добавлена.

UPLOADED означает только сохранение файла. Проверки подлинности PDF/SDU Schedule,
парсинга, OCR, извлечения курсов/преподавателей, Enrollment и matching **нет**.
Это следующие отдельные истории; US14/15 автоматически не выполняются.

Проверки: `mvn.cmd package` и `npm.cmd run build`. Upload-тесты используют H2 и
временную папку, без личного Schedule.pdf. Локальный отчёт: `work/us13-report.md`.

## Sprint 2 — US14: Validate Uploaded Schedule File

Эта секция описывает текущее поведение и расширяет исторический US13 выше.
Тот же POST `/api/student/schedule-uploads` сохраняет файл и синхронно проверяет
его через Apache PDFBox 3.0.8. Дополнительных endpoint, очередей или workers нет.

Статусы: `UPLOADED → VALIDATING → VALID / INVALID`. UPLOADED по-прежнему означает
только сохранённый файл. VALIDATING — промежуточное состояние внутри одной
транзакции; API возвращает итоговый результат, без polling. Прежние загрузки
автоматически не проверяются и сохраняют UPLOADED.

Проверяются существование/размер файла, открытие PDF без режима восстановления,
отсутствие шифрования, разрешение на чтение текста, наличие страниц и непустого
текста. Расширение/MIME/исходное имя не доказывают пригодность PDF.
Для schedule-like проверки нужны все четыре признака: слово Schedule,
Day/Hour (допускаются пробелы), учебный год с term, код из 2–8 заглавных латинских
букв и 3–4 цифр с необязательной буквенной концовкой. Конкретные курсы, преподаватели,
год, комнаты, секции и часы не заданы. Признаки могут находиться на разных страницах.
Текст используется только в памяти; структурированного извлечения/сохранения данных нет.

Предохранители: 10 MiB файла (как US13), до 50 страниц, до 200 000 символов текста,
проверка 10-секундного срока во время обработки страниц/символов. Это кооперативный
лимит, а не гарантированное прерывание каждой операции PDF-парсера. Шифрованные
PDF и сканы без текстового слоя не принимаются; OCR не добавлен.

В ScheduleUpload добавлены nullable `validation_reason` и `validated_at`.
Причины: INVALID_PDF, UNREADABLE_PDF, EMPTY_PDF, NOT_SCHEDULE, VALIDATION_LIMIT,
VALIDATION_UNAVAILABLE. INVALID сохраняет файл и метаданные. Неожиданная ошибка
проверки оставляет UPLOADED с безопасным сообщением, не выдавая ложный VALID.
Ошибка транзакции по-прежнему вызывает компенсационное удаление нового файла.

Успешное сохранение, включая INVALID, возвращает HTTP 201 с id, originalFilename,
uploadedAt, status, validationMessage (null для VALID), validatedAt (время завершения
для VALID/INVALID). Пустой файл отклоняется до сохранения с 400, превышение размера —
413. Не возвращаются путь, полный текст, диагностические исключения.
Диагностика PDFBox/FontBox отключена, чтобы не журналировать содержимое документа.

В текущей локальной PostgreSQL прежний CHECK разрешал только UPLOADED. Выполнено:

```sql
BEGIN;
ALTER TABLE public.schedule_uploads DROP CONSTRAINT schedule_uploads_status_check;
ALTER TABLE public.schedule_uploads ADD CONSTRAINT schedule_uploads_status_check
  CHECK (status IN ('UPLOADED', 'VALIDATING', 'VALID', 'INVALID'));
COMMIT;
```

Новые nullable-колонки и CHECK для validation_reason созданы Hibernate при старте.
SQL выше — запись выполненного изменения этой БД; на другой БД сначала проверьте
фактическое имя/определение ограничения. Старые строки и каталог не изменялись.

Student Area показывает Uploading and validating, затем Valid либо Invalid с причиной.
Разрешения, владелец из сессии, CSRF, UUID-хранение и приватность файлов сохранены.
VALID означает только читаемый документ с общими признаками расписания: это НЕ
доказательство подлинности, enrollment или разрешение оставлять отзывы.

Тесты используют синтетические PDF и H2; реальный личный образец остаётся в ignored
work/. Регрессия: `mvn.cmd package` (176 тестов), `npm.cmd run build`, локальные
HTTP и браузерные проверки. Подробности: `work/us14-report.md`.
US15 (структурированное извлечение) не реализован.

## US14.1 — Detailed/Full Schedule required

Текущая проверка расширяет US14: общих маркеров Schedule/Day-Hour/term/course code
теперь недостаточно для VALID. После них валидатор проверяет наличие хотя бы
одного связного блока: код → буквенное название предмета → необязательные сведения
о кредитах → группа/секция в квадратных скобках → имя из нескольких слов →
двоеточие перед сведениями о помещении. Пробелы и переносы нормализуются.
Никакие значения не захватываются и не сохраняются; это проверка наличия деталей,
а не разбор строк расписания. Общий academic term проверяется прежним правилом.

Если общие признаки есть, но блока деталей нет: status = INVALID,
validation_reason = DETAILS_REQUIRED. Отдельный workflow-статус не добавлялся.
Frontend показывает безопасное сообщение из ответа:

> This schedule version does not contain enough course and teacher details. Please download and upload the Detailed/Full Schedule version.

Форма также сразу просит Detailed/Full PDF. Compact не объявляется повреждённым
или чужим документом. Assignment по-прежнему NOT_SCHEDULE; повреждённый/поддельный
PDF — INVALID_PDF; PDF без текста — EMPTY_PDF. Прежние лимиты и авторизация сохранены.

Группы `15-P`, `04-N` и другие рассматриваются целиком как section/group.
Буквы N/P НЕ интерпретируются как лекция/практика. Тесты включают другие суффиксы,
имена, названия, коды и учебные годы. Реальные Detailed/Compact остаются только в
ignored `work/schedule-samples/`, вне ресурсов и production-артефактов.

Для существующей локальной БД фактически расширен CHECK причины; строки не менялись:

```sql
BEGIN;
ALTER TABLE public.schedule_uploads DROP CONSTRAINT schedule_uploads_validation_reason_check;
ALTER TABLE public.schedule_uploads ADD CONSTRAINT schedule_uploads_validation_reason_check
  CHECK (validation_reason IN ('INVALID_PDF','UNREADABLE_PDF','EMPTY_PDF','NOT_SCHEDULE',
                               'DETAILS_REQUIRED','VALIDATION_LIMIT','VALIDATION_UNAVAILABLE'));
COMMIT;
```

На другой БД сначала проверьте имя и определение ограничения. Новых колонок/таблиц
US14.1 не добавляет. Все прежние загрузки остаются неизменными; массовой повторной
валидации нет. При будущем US15 старый статус VALID от US14 сам по себе недостаточен:
перед извлечением старого файла надо повторить актуальную проверку достаточности.

Это эвристика структуры наблюдаемого экспорта, не подтверждение подлинности и не
гарантия полноты каждой строки. Неизвестный формат может потребовать уточнения.
US15 должен разбирать/проверять каждую извлекаемую запись отдельно, сохраняя секцию
целиком; matching/enrollment в эту коррекцию не входят.

Проверки: 189 backend-тестов, Maven package и frontend build — PASS;
два реальных образца проверены валидатором и через upload API на PostgreSQL.
Локальные результаты: `work/us141-report.md`.

## US15 — Automatically extract schedule information

После VALID-загрузки синхронно выполняется внутренний `ScheduleExtractionService`.
Он блокирует и проверяет исходную ScheduleUpload, повторно читает файл через
актуальный валидатор US14.1 и разбирает именно проверенный текст. Legacy VALID
не является обходом: Compact получает DETAILS_REQUIRED и не извлекается.
Отдельного endpoint извлечения/списка, очередей или фоновых задач нет.

Новая таблица `extracted_schedule_entries`: id, schedule_upload_id (FK),
course_code, course_name, teacher_name, group_section, academic_term.
Других полей/отношений нет. Владелец определяется через исходную загрузку.
Hibernate создаёт таблицу при старте; данные Users и каталога не меняются.

Парсер собирает блоки от кода курса, допускает переносы кода/названия/имени
и несколько страниц. Убирает служебные значения кредитов/нагрузки из названия,
печатные колонтитулы и обнаруженную подпись степени Doctor of Science после
имени; сведения о помещении используются только как граница, не сохраняются.
Section/group остаётся строкой целиком: N/P не означают тип занятия.

Нормализация: trim и свёртка пробелов с сохранением регистра/смысла; скобки вокруг
группы сняты. Повторы удаляются по всем пяти полям в пределах одной загрузки.
Разные преподаватели/группы остаются разными строками. Явные перечисления полных
имён через запятую создают отдельные записи; неизвестные/неполные блоки пропускаются.
Отсутствующий или неоднозначный term не угадывается.

Ответ прежнего upload POST содержит дополнительный объект `extraction`:
`totalCandidates`, `extractedCount`, `skippedCount`, `duplicateCount`.
Сумма последних трёх равна totalCandidates. Кандидаты — распознанные отношения
в блоках (один блок с несколькими именами может дать несколько кандидатов),
не число физических строк PDF. `extraction=null` — извлечение не выполнялось.
Полный список записей и исходный текст не возвращаются. Frontend не менялся;
просмотр и отображение результатов — US16, в этой задаче не реализованы.

Записи и upload сохраняются в одной транзакции. Сбой откатывает новые данные и
удаляет новый файл прежним механизмом компенсации. Повтор внутреннего вызова для
той же загрузки атомарно заменяет только её extracted-записи под блокировкой,
не накапливая дубликаты. Прежние загрузки массово не извлекаются.

Ограничения: parser ориентирован на наблюдаемый Detailed-экспорт с кодом в начале
текстовой строки, группой в скобках и двоеточием после имени. Новые варианты
колонтитулов/степеней/порядка таблицы могут потребовать уточнения; автоматического
угадывания преподавателей или терма нет. VALID означает пригодность по эвристике,
не подтверждение подлинности/обучения. Старые PDF-лимиты US14 сохранены.

Проверки US15: focused schedule-тесты, затем один полный `mvn.cmd package`.
Реальный ignored Detailed PDF: 15 кандидатов → 10 логических записей, 0 пропусков,
5 повторов. Результат исправлен после обнаруженных на образце degree/page-break
особенностей, для которых добавлены синтетические регрессионные тесты.
Ни matching, ни enrollment, ни US16 не реализованы.

## US16 — View extracted schedule information

Student Area now lists the current student's uploads (newest first). Each source
keeps its filename, upload date, status, academic term(s) and stored entry count.
Valid uploads link to `/student/schedules/:uploadId`; refresh fetches the data again.
The list also refreshes after an upload. No client-side PDF parsing or reprocessing.

Read-only endpoints, protected by the existing STUDENT role:
- `GET /api/student/schedule-uploads`
- `GET /api/student/schedule-uploads/{uploadId}/extracted`

The server resolves the current verified Student from the session. Ownership is
included in the upload query; another owner's ID and a missing ID both return 404.
Teacher/Admin without STUDENT receive 403, anonymous requests 401. Multi-role users
with STUDENT retain access to their own sources. No client userId controls ownership.

List DTO: id, originalFilename, uploadedAt, status, validationMessage, academicTerms,
extractedCount. Detail DTO: upload (same safe metadata) and entries containing only
id, courseCode, courseName, teacherName, groupSection, academicTerm. No storage key,
raw text, account credentials or catalog/entity relations are exposed.

Entries are ordered by term, code, course name, teacher, group and id. Distinct
teachers/groups remain separate. Past terms remain viewable under their own uploads;
N/P are displayed as part of Group / Section, with no interpretation.

Invalid/unfinished uploads return no entries, even if stale rows existed. Legacy
VALID with no rows displays “No extracted schedule information is available for
this upload.” GET never invokes validation/extraction or changes stored status.
The page shows loading, empty, error/retry states and cancels stale requests.

No schema change. No editing, matching, enrollment or US17. This is a small list,
without pagination; metadata currently reads entries per VALID upload to derive
stored counts/terms. Large histories may need a later query optimization.

## US17 — View Verified Courses

`GET /api/student/verified-courses` lazily reconciles catalog matches for the
session's verified STUDENT only. `/student/verified-courses` is a separate page;
US16's source view stays available. Multi-role students retain access; anonymous
requests return 401 and Teacher/Admin without STUDENT return 403.

Matching requires a VALID upload and complete extracted row, exactly one course
with the normalized code, equivalent academic name, exactly one teacher with the
normalized full name, and exactly one existing offering connecting them. Only
whitespace and letter case are normalized. Multiple teachers or offerings fail
closed; no fuzzy match, arbitrary Moodle shell choice or new catalog records.

The catalog has semester/section fields, but imported Moodle offerings currently
have Fall 2026 and a NULL section. Matching confirms only the course/teacher
relationship, not independent enrollment or historical term/group coverage.
Academic term and the complete group identifier come from the source schedule.
Different teachers, groups, terms and uploads stay distinguishable.

The new `verified_courses` table references one unique source entry and one
existing offering, plus createdAt. Ownership is derived through entry → upload →
user. Repeated reads reuse the same rows; reads reconcile stale matches. They do
not change source entries or read PDFs. Re-extraction deletes derived matches
before replacing source rows in the same upload-locked transaction. The next
verified read recreates only valid matches. No automatic processing at startup.

Catalog data is loaded once per request for this small MVP; verification locks
owned uploads in ID order. Larger histories/catalogs may need later optimization.
This is not the final enrollment/review-eligibility model. US18/19 are not included.

## US18 — Handle unknown teacher or course

The existing `VerifiedCourseService` matcher now returns one internal structured
outcome: an existing offering, or a pending reason. Exact code/name/full-teacher-name
rules are unchanged. It still runs lazily through the existing student-owned US17
request; there is no new public pending endpoint or frontend status page.

`pending_catalog_verifications` stores id, source_entry_id (foreign key + UNIQUE),
reason, createdAt and updatedAt. Names, term, group and ownership remain available
through the source entry and upload; they are not duplicated. Hibernate adds this
table without changing catalog tables.

Reasons: COURSE_NOT_FOUND, TEACHER_NOT_FOUND, COURSE_AND_TEACHER_NOT_FOUND,
NO_COURSE_OFFERING, AMBIGUOUS_COURSE, AMBIGUOUS_TEACHER, AMBIGUOUS_OFFERING,
COURSE_DATA_CONFLICT and INCOMPLETE_ENTRY. Incomplete fields take precedence;
both missing identities are detected together, followed by missing/ambiguous
course, conflicting course name, missing/ambiguous teacher, and offering checks.
No fuzzy matching, inferred identities or catalog creation.

The same upload lock and transaction reconcile both outcome tables. Unchanged
pending rows keep their ID and timestamps. A changed reason updates the same row.
A confident match deletes the old pending row before saving/reusing VerifiedCourse;
a lost match deletes VerifiedCourse before saving pending. Re-extraction or failed
current validation cleans both tables before replacing/removing extracted rows,
with rollback on failure. Invalid uploads cannot retain active pending outcomes.
Unique source keys prevent duplicates within each table; mutual exclusion between
tables is maintained by this single locked service transaction, not a cross-table
SQL constraint. Direct repository writes are not an alternative processing path.

US19 status presentation, US38 admin handling and Reviews are not implemented.

## US19 — View verification status

The existing `GET /api/student/schedule-uploads/{uploadId}/extracted` adds only
`verificationStatus` and `verificationReason` to each safe entry DTO. After the
existing Student/owner checks, it reads persisted outcomes for that upload:
VerifiedCourse → VERIFIED with no reason; otherwise PendingCatalogVerification →
PENDING with its US18 enum reason; otherwise NOT_CHECKED with no reason. Defensive
precedence chooses VERIFIED if inconsistent historical data has both outcomes;
GET does not repair it. US18 remains responsible for the one-active-outcome rule.

This detail GET stays read-only. It never invokes matching, PDF parsing, extraction,
verification writes or catalog changes. Unprocessed/new/legacy entries may remain
NOT_CHECKED until the existing US17 verification flow processes them. No new trigger,
endpoint, database column, review permission or admin action was introduced.

The existing extracted-information page labels PDF validation separately from
per-entry Verification. It shows all source rows, a verified/pending/not-checked
summary, and short explanations for every current US18 reason. Pending is normal
page content, not an API failure screen. The separate Verified Courses page and its
CourseOffering + term grouping are unchanged: 10 real source rows have 10 statuses,
while the grouped view has 6 logical courses.

Ownership, anonymous 401, wrong-role 403, foreign/missing upload 404 and multi-role
Student access are unchanged. No pending entity IDs, storage paths, raw text or
private entity graphs are added to responses. No US20, Reviews or US38 workflow.

## US20 — Review eligibility boundary

`GET /api/student/review-eligibilities` returns the authenticated Student's own
persisted eligible targets. `ReviewEligibilityService.list`, `canReview` and
`requireReviewEligibility` share the same read-only rule. Future review submission
must call the guard again; frontend highlighting is never authorization.

Identity is Student + CourseOffering + exact academicTerm. CourseOffering fixes
both Teacher and Course. Duplicate uploads and sections collapse into one target;
distinct trimmed groupSections are sorted supporting metadata. Past terms remain
eligible. No entitlement table, Review entity, form or submission endpoint exists.

Evidence must be a persisted VerifiedCourse from an owned VALID upload with no
pending outcome for that source entry. Incomplete evidence or a contradiction with
its already-linked course/teacher fails closed. This integrity check does not
search catalog candidates or invoke the US17 matcher. Reading never repairs or
changes verification state. If evidence becomes stale, US17/18 reconciliation is
still responsible for it. A future submission must enforce eligibility within its
own transaction and coordinate with re-extraction if concurrent writes matter.

The DTO includes safe courseId and teacherId so browse cards use stable identities,
not name comparisons. Student-only highlighting lists the exact related course or
teacher and term. The public catalog APIs, existing US17 grouping and US19 source
statuses remain unchanged. US21–US24 are not implemented.

## US21 — Select a review target

Student Area now links to `/student/reviews/new`. This protected Student page reads
only the existing US20 eligibility API. It displays one option per server-provided
CourseOffering + academicTerm, with aggregated groups as context. Backend logic,
API contracts and persistence are unchanged.

Selection is represented by `courseOfferingId` and `academicTerm` query parameters.
Opening, refreshing or changing that URL loads the authenticated student's current
eligibilities again before displaying a selected summary. Missing, malformed,
duplicate, ambiguous or unavailable identity values produce a safe unavailable
message. No course/teacher names or ownership values from the URL are trusted.
Account/URL changes remount the request state and abort stale requests.

This is selection only: no Review table, rating, text input or submission. Future
submission must still call the US20 backend eligibility guard again. The existing
Verified Courses page, schedule status and public browse highlights are unchanged.

## US22 — Give a rating

The URL-only draft behavior below describes US22; US23 supersedes draft storage.

The selected US21 target now shows one overall rating with five keyboard-accessible
star buttons and a numeric value. Only exact integers 1–5 are accepted. The optional
`rating` query parameter belongs to the same CourseOffering + academicTerm URL;
refresh restores it only after US20 validates the target. Invalid/empty/repeated
rating parameters show no rating without rejecting a valid target.

Selecting a different target creates a fresh URL without rating. Selecting the same
target keeps its rating. Star changes replace the current URL entry and retain
keyboard focus; they do not refetch or remount the target. Account/target changes
and full refresh still load current server eligibility. No localStorage, POST,
backend changes, rating table, Review persistence, text feedback or submission.

## US23 — Write text feedback

The selected eligible target has one rating and one labelled feedback textarea.
Drafts are tab-local `sessionStorage` values keyed by CourseOffering ID + exact
academicTerm, never by teacher/course alone or by group. Only `{ rating, text }`
is stored. Changing targets preserves both values for a later return; refreshing
rechecks current US20 server eligibility before mounting the draft editor.

Session storage is the draft source of truth. A valid existing stored rating wins
over a conflicting URL rating; a legacy URL rating initializes only a missing
valid stored rating. Star/text edits update storage, not the URL or backend.
Malformed JSON, invalid ratings and non-string/oversized stored text are discarded.
The textarea allows up to 2000 characters and preserves punctuation/whitespace;
whitespace-only text is incomplete, but never prevents switching targets.
Blocked/full storage shows a warning instead of claiming refresh persistence.

Successful login/logout, detected unauthenticated state and session expiry clear
review drafts in that tab without clearing unrelated session storage. No account
identifier, authentication token or eligibility is stored with drafts. Closing the
tab ends their normal lifetime. Nothing is sent or saved as a Review: no submission
endpoint, Review/ReviewDraft table, Submit button or AI moderation exists in US23.

## US24 — Submit feedback anonymously

US24 adds `POST /api/student/reviews` with the existing session, STUDENT role and
CSRF protection. Send exactly `{ courseOfferingId, academicTerm, rating, text }`.
The backend rejects extra fields (including author IDs), non-integer JSON ratings,
ratings outside 1–5, empty/whitespace-only text, and text longer than 2000 characters.
The exact academic term is preserved; only outer feedback whitespace is trimmed.
US20 eligibility is checked again inside the creation transaction.

The additive `reviews` table holds id, author_id (User FK), course_offering_id (FK),
academic_term, rating, text and created_at. Normal Hibernate schema update creates
it. No catalog or verification rows are changed. No author/target/term uniqueness
rule is added yet. A 201 response contains only reviewId, submitted,
courseOfferingId, academicTerm and rating; entities and author identity are never
returned. Author identity is retained internally for later audit/history rules.
There is no public/teacher review read endpoint and no identity-visibility toggle.

The existing draft editor now enables Submit Review only for a complete draft,
disables editing/submission while pending, and shows a safe success/error message.
Success clears only the submitted target's session draft and removes legacy URL
rating for the current target. Failure retains the draft. A network timeout cannot
prove whether the server committed; its message says submission was not confirmed.
There is no automatic retry. Single in-flight protection is not US28 deduplication.
US25 adds the single-review status view below; US26 adds moderation. History remains future work.

### Browse → Leave Review

Each Student-specific eligible relationship in teacher/course cards has a compact
Leave Review link. It uses the same CourseOffering + academicTerm query builder as
the existing review page, which rechecks eligibility before opening the draft.
All eligible relationships are listed; different teachers/courses/terms retain
separate actions. Card main content remains separate from review links, without
introducing profile routes. Student Area's existing entry point is unchanged.

## US25 — Review submission status

Reviews now persist a dedicated `ReviewStatus.SUBMITTED`. Creation assigns it in
the same transaction; the submission response also includes `status`. Existing
reviews are retained. The local database was adapted with this additive command:

```sql
ALTER TABLE public.reviews
ADD COLUMN status varchar(32) NOT NULL DEFAULT 'SUBMITTED';
```

Run that command only when the column is absent. The entity also specifies the
database default so Hibernate can add the column safely on an older database.
The default supports an older running application during the upgrade; normal
Java creation explicitly initializes the field. US26 adds the transitions described below.

`GET /api/student/reviews/{reviewId}/status` returns one Review's safe status and
course/teacher/term/rating summary. It requires STUDENT and uses the session
principal to query by both review ID and owner. Missing and other users' reviews
both return 404. It does not rematch schedules, rerun eligibility, or expose
author identifiers, email, feedback text, or schedule source identifiers.

Successful submission clears only that target's draft and opens
`/student/reviews/{reviewId}/status`. Refresh/direct navigation fetches persisted
data from the API, independent of draft or navigation state. This is a single
submission view, not My Reviews/history; Submitted does not mean published,
approved or AI-checked. No edit, duplicate prevention, or aggregation is added.

## US26 — AI moderation before publication

Every valid new review is committed as `SUBMITTED` using the existing eligibility,
ownership, rating, feedback and CSRF checks. A synchronous Spring AFTER_COMMIT
listener then makes **one** moderation attempt. It is an in-process callback, not
a queue/background job. The provider call cannot roll back the submitted review.
A separate short transaction saves a validated result:

- `ACCEPT` → `PUBLISHED`, no violation categories, `moderatedAt` set.
- `NEEDS_CORRECTION` with known categories → `NEEDS_CORRECTION`, categories and
  `moderatedAt` saved.
- Missing key, timeout, API/network failure, blocked/truncated output, invalid JSON
  or inconsistent result → remains `SUBMITTED`; no successful moderation timestamp.

The POST response remains the original `SUBMITTED` receipt. The existing status
page immediately reads the current outcome from GET `/api/student/reviews/{id}/status`.
The owner-only DTO now also contains `moderatedAt` and `moderationReasons`, each
with a fixed category and backend-defined friendly message. No author identity,
raw provider response, feedback text or prompt is returned. Refresh/direct URLs
still read the backend. There are no edit/resubmit controls or public review lists.

`ModerationProvider` accepts only the complete feedback text (maximum 2000
characters, no word limit). Rating, account and catalog metadata are never sent.
The Gemini adapter sends the publication policy as a separate system instruction
and treats feedback as untrusted data. Negative criticism is allowed. The six
categories are `PERSONAL_INSULT`, `THREAT`, `HATE_OR_DISCRIMINATION`,
`PERSONAL_INFORMATION`, `SPAM_OR_IRRELEVANT`, and `ABUSIVE_LANGUAGE`.
Structured JSON is checked again on the backend, including allowed enum values
and decision/category consistency. Policy behavior is probabilistic and still
requires a synthetic live check; mocked tests do not prove real model accuracy.

Configure **backend** environment variables in the existing IntelliJ run configuration:

- `GEMINI_API_KEY`: your Google AI Studio key; never place its value in source or Git.
- `GEMINI_MODEL`: optional, defaults to `gemini-3.5-flash-lite` as requested.

The backend uses the Gemini Developer API `generateContent` REST endpoint and
`responseJsonSchema` ([Google API reference](https://ai.google.dev/api/generate-content)).
No SDK/dependency was added. Connection timeout is 3 seconds; request timeout is
8 seconds (`app.moderation.gemini.timeout-ms`, bounded to 100 ms–10 seconds).
No automatic retry occurs. Missing credentials do not prevent startup or tests.
Restart the backend after configuring environment variables. Existing SUBMITTED
reviews are **not** automatically moderated on startup or on status reads.

Schema additions: nullable `reviews.moderated_at` and an element-collection table
`review_moderation_categories` with a review FK and unique (review_id, category).
This stores only the current categories, not moderation history. Existing reviews
retain their status, text, rating, author and target. Standard Hibernate update can
add these fields; if an older database has a status CHECK constraint, inspect and
extend that constraint to the three allowed statuses before serving new submissions.

For the current local database, these additive statements were executed once after
confirming both schema additions were absent (do not rerun blindly):

```sql
ALTER TABLE public.reviews ADD COLUMN moderated_at timestamp(6) with time zone;
CREATE TABLE public.review_moderation_categories (
  review_id bigint NOT NULL REFERENCES public.reviews(id),
  category varchar(40) NOT NULL,
  CONSTRAINT uk_review_moderation_category UNIQUE (review_id, category)
);
```

There was no restrictive status CHECK constraint to change. The five existing
reviews remain SUBMITTED with no moderation timestamp/categories.

Automated moderation tests use H2 and fake providers, never real Gemini. Configure
the key and use synthetic feedback only for a live provider check. No US27–US30,
sentiment analysis, topics, summaries or admin moderation were added.

## US27 — Correct flagged feedback

`PUT /api/student/reviews/{reviewId}/correction` accepts exactly `{ "text": "..." }`
with the existing Student session and CSRF token. Only the authenticated owner of
a `NEEDS_CORRECTION` review can correct it. Other owners and missing IDs both
receive the same 404; SUBMITTED/PUBLISHED reviews return 409. A database row lock
keeps the status check and update together for concurrent correction requests.
This is correction safety, not duplicate prevention for new review submissions.

The same Review ID is updated. Author, rating, CourseOffering, academicTerm and
createdAt remain unchanged. Initial submission and correction share the same
required/nonblank/2000-character validation and outer-whitespace trimming.
Extra request fields are rejected. There is no word limit or new schema/history.

Correction resets the review to SUBMITTED, clears old categories/moderatedAt,
and commits before invoking the unchanged US26 moderation listener/provider.
The PUT returns 200 with the existing SUBMITTED receipt; the status GET reads
the latest outcome. Provider failure preserves the corrected text as SUBMITTED,
without stale reasons. Successful moderation replaces reasons with the new result.
There is no new Gemini prompt, client or retry mechanism.

The existing owner-only status DTO includes `text` for NEEDS_CORRECTION only
(null otherwise), never author identity. The status page offers **Edit Feedback**,
preloads that text, displays the 2000-character counter and keeps reasons visible.
**Submit Corrected Feedback** disables editing while pending and reloads status
from the backend. PUBLISHED/SUBMITTED hide the editor; a new NEEDS_CORRECTION
result allows another edit with new reasons. Unsent corrections live only in
component state; refreshing discards unsaved edits and reloads persisted text.
After a network error, refresh status before retrying because the write may have
committed. No automatic retry occurs.

Tests use synthetic H2 data and the existing fake ModerationProvider, including
same-row identity, state/ownership/CSRF checks, provider failure after commit and
overlapping requests while moderation is running. See US28 below for new-review
duplicate prevention and the separate published-review update endpoint.

## US28 — One review per target and published-review updates

The duplicate key is **author + CourseOffering + academicTerm**. Every status
counts; rating, text, section and repeated schedule evidence do not change it.
POST `/api/student/reviews` still requires authoritative eligibility. It locks the
author row until commit, checks the key and returns safe HTTP 409 for a duplicate.
This serializes concurrent submissions from the same author across application
instances. The lock is released before the existing moderation provider runs.
The frontend shows a friendly conflict message and preserves drafts.

### Local database constraint installed

The original preflight found two duplicate groups (Offerings 1293 and 1482).
The user manually reviewed and removed obsolete test reviews before this final
schema step. Independent re-check on 2026-10-07 confirmed **zero duplicate groups
and six reviews**. The unchanged `backend/sql/us28-review-uniqueness.sql` was then
applied to local PostgreSQL, installing:

```sql
CONSTRAINT uk_review_author_offering_term
UNIQUE (author_id, course_offering_id, academic_term)
```

PostgreSQL also created the same-named unique B-tree index. Section, rating, text
and status are not part of this key. A duplicate insert in a rolled-back test
transaction was rejected with SQLSTATE 23505 by this exact constraint. An explicit
temporary ID avoided advancing the review sequence. No rows were retained by the
test, and no existing reviews or moderation categories were changed by this step.
Review count stayed six; catalog counts stayed 506 / 903 / 1741.

The current packaged backend started against real PostgreSQL with Hibernate
`ddl-auto=validate`, seed disabled and an isolated temporary port. Public catalog
GETs succeeded; the temporary process was stopped. No code or SQL definition
changed, so the already-passing 475-test suite was not rerun for this schema step.

The script is a **once-only** manual schema operation, not an application startup
action; do not rerun it on this database. For another database, inspect duplicates
and existing constraints first. The script checks under a table lock and aborts
if duplicates remain; it never cleans up rows. The application author lock and
database uniqueness now both protect submissions. A conflict with the named
constraint becomes safe HTTP 409; unrelated integrity errors are not reported as
duplicates. Existing isolated tests cover concurrent and direct repository writes.

### Backend update capability (no general edit UI yet)

`PUT /api/student/reviews/{reviewId}` accepts exactly
`{ "rating": 4, "text": "Updated feedback..." }` with the existing Student
session and CSRF token. Only the author of a PUBLISHED review may use it.
Other owners/missing IDs receive identical 404 responses; SUBMITTED and
NEEDS_CORRECTION return 409. Rating remains integer 1–5; text remains required,
nonblank and at most 2000 characters, with outer whitespace trimmed.

The same review row is updated: ID, author, target, term and createdAt cannot
change. A row lock protects the status check/update. New rating/text and SUBMITTED
status are committed with old moderation categories/time cleared, then the same
US26 event listener/provider moderates only the feedback text. The response is
200 with the initial SUBMITTED receipt; the status GET reads the latest outcome.
Provider failure leaves the updated review saved as SUBMITTED. If moderation
flags it, the existing US27 text-only correction flow remains available.

No My Reviews/history page, general Edit Review button, revision tables or US29+
features were added. Existing development data, catalog and authentication are
unchanged. Automated tests use synthetic data and fake moderation, never Gemini.

## US29 — My Reviews

Student Area now links to `/student/reviews` (**My Reviews**), protected by the
existing STUDENT route guard. `GET /api/student/reviews` uses the authenticated
session only; query parameters (including authorId/userId) are rejected. The
repository filters by author in SQL and fetches the course, teacher and current
moderation reasons together. Ordering is original `createdAt DESC, id DESC`.

The own-review DTO contains reviewId, course code/name, teacher name, academicTerm,
rating, current text, status, submittedAt (immutable createdAt), moderatedAt and
friendly moderation reasons. It excludes account identity, offering/source IDs,
credentials and provider internals. The older status endpoint keeps its existing
contract; only this authenticated own-history endpoint supplies text for every
status. Reading history neither checks new submission eligibility nor moderates.

- **PUBLISHED:** Edit Review opens an inline rating/text editor, preloaded from
  the server. It reuses RatingInput and the existing 1–5 / nonblank / 2000-character
  rules. Save Changes calls US28 `PUT /api/student/reviews/{reviewId}` on the same
  row, then reloads history from the server. No local moderation result is guessed.
  Cancel discards unsaved changes; controls are disabled while saving. Failed
  saves retain the editor and previously loaded review. Refresh after an uncertain
  network result (cancel editing first) because the write may already have committed.
- **NEEDS_CORRECTION:** friendly reasons plus Fix Feedback link to the existing
  owner-only status page and US27 text-only correction editor. My Reviews reloads
  current content/status on return. Status wording is shared between both pages.
- **SUBMITTED:** status and View Status only, without edit/correction controls.

Loading, empty, safe-error and manual refresh states are included. A failed reload
keeps previously loaded content with an out-of-date warning and disables editing.
Account changes remount the page; requests are cancelled on unmount. History and
unsaved edits do not use draft sessionStorage. New review drafts remain unchanged.

No schema change, deletion/archive, revision history, aggregation or US30 feature
was added. `uk_review_author_offering_term` and all US27/US28 backend update and
moderation rules remain unchanged. Tests use isolated synthetic data/fake providers;
the six existing local PostgreSQL reviews are not edited by this implementation.

## US30 — Teacher Course Feedback (Sprint 4)

Teacher Area links to `/teacher/feedback` (**Course Feedback**), using the existing
TEACHER route guard. `GET /api/teacher/feedback` is protected by the existing
`/api/teacher/**` security rule. It resolves the current account through
`AccountDetailsService.currentUser`, then uses only that account's linkedTeacher.
All query parameters are rejected; clients cannot select a teacher. Missing
linkedTeacher returns safe 403, with no name/email-based fallback. Anonymous
requests receive 401; STUDENT/ADMIN without TEACHER receive 403.

The repository filters by the linked Teacher's CourseOfferings and PUBLISHED
status **in SQL**, selecting only safe fields. SUBMITTED/NEEDS_CORRECTION content
and counts are never returned. No author, schedule or moderation-category data
is selected by this feedback query. The response is a list of groups:

- `courseOfferingId`, `courseCode`, `courseName`, `academicTerm`, `feedback`.
- Each feedback item contains only `rating`, `text`, `publishedAt`.

Offering ID is a technical group key, not displayed as normal UI text. Groups
remain distinct by CourseOffering + academicTerm, including separate Moodle
offerings for the same course. No groupSection, review/author ID, student identity
or provider internals are returned. The endpoint uses a separate teacher-safe DTO,
not the Student-own history DTO. Published free text is shown as text, not HTML.

Group order is course code ascending, offering ID ascending, academic term
descending (lexical). Inside each group, current publication time descending,
then review ID descending. Publication time uses moderatedAt, falling back to
createdAt for legacy published rows without a moderation timestamp. The fallback
does not alter stored data. A Student update hides the review while SUBMITTED or
NEEDS_CORRECTION; it becomes visible again only after PUBLISHED.

The page shows course/term groups and individual rating, text and date. Direct
navigation, reload, loading, retry and empty states use backend data. No published
feedback (including no offerings or only private reviews) gives the same neutral
empty state. Account/link changes remount the view; cancelled requests cannot
replace current data. Refresh/error clears old feedback rather than showing
potentially outdated published content.

US30 is implemented and automatically tested. Manual browser smoke has not been
performed: after starting the current backend/frontend, sign in as the Teacher
linked to an existing published review's offering, open Teacher Area → Course
Feedback, and refresh/directly open `/teacher/feedback`. Confirm the published
review appears without author/group details. Existing flagged/submitted reviews
must be absent. Do not modify real reviews or claims to manufacture this check;
isolated synthetic tests cover these states and the same-course/two-teacher case.

Read-only local check on 2026-10-09 found three PUBLISHED reviews, but none of
their offerings' Teachers currently has a linked TEACHER-role account. Therefore
the exact positive browser scenario is not available with current accounts.
No claims/accounts/reviews were changed to make it possible; an existing linked
Teacher account can still check the empty state and reload behavior.

No schema changes, average/statistics/charts, filters, AI requests, Teacher review
management or revision history were added. US28 UNIQUE and US24–US29 behavior
remain unchanged. US31–US36 and Sprint 5 have not started.

## Sprint 5 — US8: Teacher Profile

Public `GET /api/teachers/{teacherId}` returns only `id`, `name` and
`courseOfferings` containing `courseId`, `courseCode`, `courseName`, `academicTerm`.
Course information comes from existing CourseOffering relationships. The term
is the catalog's stored `semester` (for example `Fall 2026`), without guessing
a schedule/review term conversion. Missing or invalid IDs return a safe 404.

Public `/teachers/:teacherId` shows the name and courses/terms, with loading,
retry, not-found and empty states. Browse teacher cards have **View Profile**.
Teacher Area has **My Profile** alongside the existing **Course Feedback** link.
My Profile uses `linkedTeacher` from authenticated `/api/teacher/home`; no link
is offered when that relationship is absent. Both paths open the same public page.

A read-only catalog check found 16 repeated teacher/course/semester groups.
The profile query therefore selects distinct course/term display rows for the
selected teacher. Different terms remain separate. No source offering, section,
Moodle identifier, schema or stored record is merged or changed.

US8 adds no reviews, ratings, analytics, owner editing or Course Details.
Later Sprint 5 stories, including US32, are documented below.
Manual browser smoke remains: start the current application, open Browse →
View Profile, refresh/directly open `/teachers/7`, check a nonexistent ID, and
open Teacher Area → My Profile with an existing linked Teacher account.
Course Feedback should remain available separately.

## Sprint 5 — US9: Course Details

Public `GET /api/courses/{courseId}` returns `courseId`, `courseCode`,
`courseName` and `teacherOfferings` with `teacherId`, `teacherName`,
`academicTerm`. Teachers come exclusively from CourseOffering relationships.
The stored `semester` is preserved as `academicTerm` without interpreting groups
or inventing a different term format. The response contains no account,
schedule, review, rating or moderation data.

As with US8, repeated source offerings are deduplicated for display only:
one Teacher ID + term row for this Course. Distinct teachers (even with the same
name) and distinct terms remain separate. Ordering is teacher name, teacher ID,
then term descending as stored text, not inferred chronological order.
Missing/invalid Course IDs return safe 404; a Course without offerings returns
its details and an empty teacher list.

Public `/courses/:courseId` supports direct navigation/reload, loading, retry,
not-found and empty states. Navigation is Browse Courses → View Course,
Teacher Profile → View Course, and Course Details → View Teacher Profile.
Existing Teacher Area → Course Feedback remains separate and unchanged.

No database/schema changes, new filters, sorting controls, reviews, ratings,
analytics or AI calls were added in US9. Later stories are documented below.
Manual browser smoke remains: start the current application, follow both
navigation paths, refresh Course Details, and try a nonexistent Course ID.
US8 and US9 are implemented; Sprint 5 is not complete.

## Sprint 5 — US10: Public feedback by course/term

Public `GET /api/teachers/{teacherId}/courses/{courseId}/feedback` provides the
catalog relationship and available feedback targets. Query parameters:
`academicTerm` (exact stored text) and `courseOfferingId` (concrete source
offering). The offering must belong to BOTH path identities. When the term
uniquely identifies an offering it can be resolved without the optional ID;
ambiguous terms return selection choices and no feedback until an offering is
chosen. No first-offering guess or cross-offering aggregation is performed.

Example: `/api/teachers/7/courses/10/feedback?academicTerm=2026-2027%201%20term&courseOfferingId=100`
(IDs are illustrative; use existing catalog relationships).

The response contains `teacherId`, `teacherName`, `courseId`, `courseCode`,
`courseName`, selected `courseOfferingId`/`academicTerm`, `availableTargets`
(`courseOfferingId`, `academicTerm`) and `feedback` (`rating`, `text`,
`publishedAt`). Unselected offering/term values may be null. Only concrete
offering + exact review term + PUBLISHED reviews are selected in the database.
Order is publication date descending (`moderatedAt`, or `createdAt` for legacy
rows), then Review ID descending; Review IDs are not returned.

Catalog semesters and review terms are different source fields: real catalog
data uses `Fall 2026`, while published reviews also use `2026-2027 1 term` and
`2025-2026 1 term`. Options combine existing catalog offering/semester pairs
with terms of PUBLISHED reviews only, preserving exact text. No conversion is
inferred, and private-only review terms are never advertised. With duplicate
source offerings for one term, the small selection list includes the offering
ID to distinguish them without exposing sections or inventing language labels.

Both Teacher Profile and Course Details link to the same public page:
`/teachers/:teacherId/courses/:courseId/feedback?term=...&offering=...`.
Term links trigger fresh backend selection. Direct navigation/refresh,
cancellation, loading, retry and not-found states are supported. An existing
selected target without published feedback displays a neutral empty message.
Missing/unconnected identities, wrong offering, unknown term (including a
private-only term), and incomplete explicit offering selection return safe 404.

No authors, emails, accounts, groups, schedules, moderation/provider details,
private statuses or private counts are returned. US30 retains its existing
separate authenticated Teacher Area endpoint/page. No new schema, review
actions, averages, history comparisons, charts, filters beyond this selection,
or AI calls were added in US10. US11, US12 and US32 are documented below.

Manual browser smoke remains: start the current application; open a Teacher
Profile or Course Details → View Feedback; choose a published review's exact
term (which may differ from the catalog semester); refresh/directly reopen the
URL; switch term and, where listed separately, offering. Check a neutral empty
catalog-term selection and an invalid selection. No real reviews need changing.
Read-only inspection found published targets 1293 (INF 345, `2026-2027 1 term`),
1479 (MDE 154, `2025-2026 1 term`) and 1482 (MDE 153, `2025-2026 1 term`).

## Sprint 5 — US11: Previous Ratings

Public `GET /api/teachers/{teacherId}/courses/{courseId}/ratings/history`
returns `teacherId`, `teacherName`, `courseId`, `courseCode`, `courseName`, and
`ratings`: `courseOfferingId`, `academicTerm`, `averageRating`,
`publishedReviewCount`. It accepts no query filters. Nonexistent or unconnected
Teacher/Course identities return safe 404. All data is read-only.

The existing catalog contains separate Moodle/source offerings for some equal
Teacher + Course + term combinations. Their equivalence is unproven, and US28
allows a student one review per offering/term. Combining them could count the
same student's separate source reviews together. US11 therefore preserves the
US10 source boundary: SQL AVG/COUNT groups by **CourseOffering + exact review
academicTerm**, restricted to the exact Teacher and Course and PUBLISHED status.
All relevant offerings appear, but different offerings are never silently merged.
This is the offering-aware fallback permitted by US11, not one blended average
across ambiguous offerings. No group/section interpretation is used.

AVG is the arithmetic mean of current PUBLISHED ratings; COUNT includes exactly
those reviews. SUBMITTED and NEEDS_CORRECTION affect neither mean nor count.
Backend returns the numeric mean without permanent rounding; frontend displays
one decimal (`5, 4, 4` → `4.3 / 5`). No stats are stored or cached in PostgreSQL.
Terms with no published ratings are omitted; a valid empty relationship returns
`ratings: []`, never a fabricated `0.0 / 5`. Editing a published review removes
its contribution until it is published again; this is not revision history.

Ordering is exact academicTerm text descending, then offering ID ascending.
There is no inferred chronology or normalization between `Fall 2026` and
`2026-2027 1 term`. This limitation is explained in the UI. If several offerings
have published ratings, each row identifies its course offering separately.

US10 Public Feedback → **Previous Ratings** opens
`/teachers/:teacherId/courses/:courseId/ratings/history`. This separate public
page supports direct navigation, refresh, request cancellation, loading, retry,
safe not-found and `No published ratings yet.` states. It links back to US10,
the Teacher Profile and Course Details without duplicating review-text cards.

Only catalog identity and term averages/counts are returned. No review text,
Review IDs, authors, groups, schedule data or moderation/provider information
is returned. No overall/all-time rating, distributions, charts, trend arrows,
AI or new filtering system was added in US11. US32 is documented below.
AccountSetupService, review writes, schema and US28 UNIQUE remain
unchanged.

Manual browser smoke remains: start the current application; open Teacher
Profile or Course Details → View Feedback → Previous Ratings; confirm the
correct Teacher/Course, term labels, averages/counts; refresh/directly reopen
the history URL. A relationship without published reviews should show the
neutral empty state; an unconnected pair should show not-found. Return to
View Feedback to check that US10 still displays individual published reviews.

## Sprint 5 — US12: Sort Teacher Results

Public `GET /api/teachers?q=...&courseQ=...&sort=RATING_DESC` keeps the existing
Teacher response (`id`, `name`, `department`). `sort` is optional; the default
is `NAME_ASC`. An unsupported explicit value, including an empty value, returns
400 with `Unsupported teacher sort.`

| Sort | Order |
| --- | --- |
| `NAME_ASC` | Name A–Z, then ID ascending (existing default) |
| `NAME_DESC` | Name Z–A, then ID ascending |
| `RATING_DESC` | Rated first, published average descending, then name/ID ascending |
| `RATING_ASC` | Rated first, published average ascending, then name/ID ascending |
| `REVIEW_COUNT_DESC` | Published review count descending, then name/ID ascending |
| `HAS_FEEDBACK_FIRST` | At least one published review first, then name/ID ascending |

US12's refined teacher-level sort uses **all PUBLISHED reviews across that
teacher's offerings and review terms**. It weights each review equally, not
each offering equally. SUBMITTED and NEEDS_CORRECTION never contribute.
Unrated teachers are not zero-rated and follow all rated teachers in both
rating directions. Feedback-first retains teachers without published reviews.
This internal teacher-level sort does not change US11's offering/term history.

Search and course filters keep their existing partial, case-insensitive semantics.
They limit which teachers appear, not which reviews contribute to their rating.
The aggregate repository query groups reviews once by teacher and uses EXISTS
for course filtering, avoiding multiplied counts and per-teacher queries. Name
sorts retain the existing repository queries and database collation. Sorting
values are enum-controlled; no user-supplied SQL fragments are used.

Browse Teachers has one **Sort by** select with all six options. Changing it
preserves both search fields, request cancellation, loading/error/empty states,
retry, and View Profile links. The browser receives no new rating/count fields,
review text, identities or moderation details. US32 displays stats separately
on Teacher Profile, not Browse cards. No schema, catalog, review-writing,
US28 uniqueness, or AccountSetupService changes are required.

Manual browser smoke: start the current backend/frontend yourself; open Browse,
try each sort with and without teacher/course search, clear the fields, and
open View Profile. Check unrated teachers remain after rated teachers in both
rating directions and search values survive sort changes. Automated API and
rendering checks cover these contracts; no live browser smoke was performed.

## Sprint 5 — US32: Overall Rating Stats

Public `GET /api/teachers/{teacherId}/ratings/overall` returns only:
`teacherId`, `teacherName`, nullable `averageRating`, `publishedReviewCount`,
and `ratingDistribution` (keys `5`, `4`, `3`, `2`, `1`, including zero counts).
Nonexistent/invalid Teacher IDs return safe 404. Like US11, this endpoint rejects
query parameters with 400; overall statistics have no course, term or user filters.

SQL AVG, COUNT and conditional counts include **only PUBLISHED reviews**, each
once, across all of that teacher's courses, source offerings and review terms.
This is the same teacher-wide scope used by US12 sorting; it is not an average
of per-offering averages. US11's separate offering/term history stays unchanged.
SUBMITTED and NEEDS_CORRECTION affect none of the fields. No published reviews
means `averageRating: null`, count zero and all five buckets zero.

The repository follows US11's aggregate-projection pattern. A teacher lookup
and one aggregate query provide the result, without loading review entities,
authors or one query per offering. The small single-teacher query does not
couple Browse to this API or replace US12's existing bulk sorting query.
No stats are stored/cached, no schema migration is needed, and US28 UNIQUE is
unchanged. The endpoint returns no review text/IDs, student identities, groups,
term details, offering IDs, schedule or moderation/provider data.

Teacher Profile displays **Overall Rating** above Courses taught: average to
one decimal, published review count, and simple 5-to-1 star count rows. Empty
state says **No ratings yet / 0 published reviews**, never a false `0.0 / 5`.
Statistics load independently with cancellation and a safe retry state; a stats
failure does not hide courses. Direct navigation and refresh fetch current stats.
Browse cards remain unchanged. No charts, trends, AI, comparisons or analytics
filters were introduced. AccountSetupService was not changed.

US8–US12 and US32 make Sprint 5 implementation-complete. Automated verification
is separate from the remaining manual browser smoke check: start the current
application, open a Teacher Profile with published reviews, check one-decimal
mean/count/five buckets, refresh, then check a teacher without ratings. Verify
View Course/View Feedback links and Browse sorting still work. No real review
data needs creating or modifying. Sprint 6 begins with US31 below.

US32 automated verification: 124 focused backend tests passed; final
`mvn.cmd package` passed all 628 backend tests in one full run. Frontend:
78 affected regression tests passed plus 12 US32 tests (after correcting the
new test's expected existing feedback URL); production build passed. Read-only
PostgreSQL snapshots remained identical: 506 Teachers, 903 Courses,
1741 CourseOfferings, 6 Reviews, with the US28 UNIQUE constraint unchanged.

## Sprint 6 — US31: Filter Teacher Analytics

Teacher Area now links to `/teacher/analytics`. The existing TEACHER route guard
and backend `/api/teacher/**` security apply. The page provides Course and Academic
Term selectors only, with query state retained across refresh/back/forward.
My Profile and Course Feedback remain separate and unchanged.

`GET /api/teacher/analytics/scope?courseId=123&term=2026-2027%201%20term`
returns exactly:

- `courses`: `{courseId, courseCode, courseName, academicTerms: string[]}` options
- `selectedCourseId` and `selectedAcademicTerm` (null when no courses exist)
- `hasPublishedFeedback`: boolean

Ownership comes exclusively from the authenticated account's `linkedTeacher`.
Only that teacher's CourseOffering courses appear. Missing login returns 401,
wrong role or missing linkedTeacher returns 403. Foreign/nonexistent/invalid
course or term selections return safe 404. Unknown or duplicate parameters
(including `teacherId`) return 400. No additional filters are supported.

Terms are the exact union of owned offering semesters and PUBLISHED review
academicTerms for the same teacher/course, following US10's distinction between
catalog semesters and review terms. Different formats and whitespace stay distinct;
there is no inferred equivalence or chronology. SUBMITTED/NEEDS_CORRECTION never
add options or affect availability. Courses use code-ascending order (ID tie-breaker);
terms use reverse lexical order. Without a selection, the first options are used
deterministically, not as an inferred current semester. Changing course clears the
previous term; the server selects a valid term for the new course. Term without a
course is rejected. Invalid selections can be reset; loading/errors hide stale scope.

Read-only inspection found 16 teacher/course/semester groups with repeated source
offerings. US31's scope is the owned **teacher + course + exact review term**:
availability means at least one PUBLISHED review in that set. Source offering IDs
remain distinct; this boolean does not merge their records or statistics. US11
continues to show separate offering/term history; US12/US32 keep their existing
teacher-level aggregate scope. Group/section does not participate in US31 filters.

Two projection queries retrieve catalog options and published term availability,
in addition to existing current-account loading. No Review entities, per-offering
queries or AI calls are needed. The response excludes student identity, review IDs
and text, groupSection, schedule/verification data, ratings, counts and moderation
details. Empty states say "No courses available for analytics." or "No published
feedback available for this selection." No analytics tables or schema changes.

Manual browser smoke (no new reviews needed): after starting the current app,
sign in as a linked Teacher, open Teacher Analytics, change course and term, refresh
and use Back. Confirm only own courses appear. Try an invalid course/term and reset;
check a valid scope without published feedback. Existing My Profile/Course Feedback
links should still work. Automated tests cover ownership, status privacy, exact terms,
multiple offerings, URL/filter callbacks, empty/error states and bounded SQL queries.
US33–US36, AI analysis and charts are not implemented; Sprint 6 is not complete.

US31 verification: 23 focused analytics backend cases passed, alongside 139 affected
backend regression cases. The initial query-budget assertion was corrected to count
the three existing current-account queries plus two analytics projections; adding
ten offerings still uses five queries. Frontend: 110 focused/affected tests passed,
and production build passed. Final `mvn.cmd package` ran the full backend suite
once: 664 passed, no failures/errors/skips. PostgreSQL before/after row checksums
and counts match: 506 Teachers, 903 Courses, 1741 CourseOfferings, 9 Reviews;
`uk_review_author_offering_term` is unchanged. Real browser smoke remains manual;
the user's running application was not restarted. Gemini diagnostics and
AccountSetupService were left untouched.

## Sprint 6 — US33: AI Sentiment

Teacher Analytics now adds aggregate Sentiment Analysis below the existing US31
course/term filters. `GET /api/teacher/analytics/sentiment?courseId=...&term=...`
requires both parameters and TEACHER access. It reuses US31 parameter, linkedTeacher,
owned-course and exact-term validation, including safe 401/403/404/400 responses.
Changing a filter remounts the section, cancels the old request and hides its result.
Refresh reloads the URL scope; Retry requests a fresh result. No result is cached.

The database selects only PUBLISHED review **text**, once per review, across the
selected course's offerings owned by the authenticated teacher and the exact review
academicTerm. Catalog semester labels are not equated with differently formatted
review terms. SUBMITTED and NEEDS_CORRECTION are excluded before provider access.
The read-only database step completes before the network call.

`SentimentAnalysisService -> SentimentProvider -> GeminiSentimentProvider` is separate
from moderation. It reuses existing GEMINI_API_KEY / GEMINI_MODEL configuration and
the existing 8000 ms request timeout (3 second connection timeout), with no automatic
retries. No moderation code/configuration is changed. A large/slow analysis may be
unavailable rather than producing partial or invented results.

The Gemini user payload is a JSON array containing only anonymous published feedback
texts. No ratings, identity fields, teacher/course/offerings IDs, groupSection, schedule
data or moderation history are added. The fixed policy treats texts as untrusted data,
allows negative criticism, and asks only for sentiment, never moderation, topics or
summary. The JSON schema requires `{"sentiments":["POSITIVE","NEUTRAL","NEGATIVE"]}`
with one label per input in the same order. See the official
[Gemini structured output documentation](https://ai.google.dev/gemini-api/docs/generate-content/structured-output?hl=en).
Backend validation rejects wrong length, unknown labels, extra/duplicate JSON fields,
invalid JSON, blocked/truncated output and unexpected envelopes.

Backend counts classifications and derives MOSTLY_POSITIVE / MOSTLY_NEUTRAL /
MOSTLY_NEGATIVE only when one category has a strictly higher count than both others;
any tie for highest is MIXED. Integer percentages use floor plus largest remainder
allocation, with equal remainders resolved POSITIVE, NEUTRAL, NEGATIVE. They total
100 (for example 1/1/1 becomes 34/33/33). Ratings never determine sentiment.

The safe response contains courseId, courseCode, courseName, academicTerm, status,
analysisAvailable, analyzedReviewCount, overallSentiment, the three category counts
and percentages. AVAILABLE includes aggregates only, never individual labels/texts
or student/group/moderation/provider fields. EMPTY means zero published reviews:
no AI call, analyzedReviewCount=0, null counts/percentages/overall. UNAVAILABLE means
provider failure: analysisAvailable=false and all result/count fields null. Both
are HTTP 200 domain states. The UI shows clear empty/unavailable messages; unavailable
offers Retry. No fake neutral result, rating-based fallback or stale success.

Safe provider warnings include model, key presence boolean, failure category, HTTP
status, an allowlisted reason and item count. No exception stack/message, feedback,
raw response, key or authorization header is logged. Missing config, invalid model,
quota, HTTP errors, timeout/network errors and response parsing are distinguished.

US33 adds no tables, migrations, stored sentiment or minimum sample threshold.
Existing US28 uniqueness, US26 moderation and diagnostic changes remain intact.
US34 topics, US35 summaries and US36 privacy enhancements are not implemented.
Manual browser smoke: run the current app, sign in as a linked Teacher, select an
owned course/term, check aggregate/empty/unavailable states, change filters, refresh
and Retry if needed. No real Student feedback was used for diagnostic live calls.

US33 verification (2026-10-14): 145 focused backend/regression cases passed,
including 23 mocked Gemini sentiment provider/parser cases and 19 service/aggregate
cases. Frontend: 50 focused US33/US31/US30 tests and production build passed.
Final `mvn.cmd package` ran the full backend suite once: 706 passed, no failures,
errors or skips. One live synthetic-only call using gemini-3.5-flash-lite returned
HTTP 200; parsing succeeded with POSITIVE / NEGATIVE / NEUTRAL. No retries were made.
Read-only PostgreSQL before/after snapshots matched: 506 Teachers, 903 Courses,
1741 CourseOfferings and 10 Reviews; row checksums and US28 UNIQUE were unchanged.
Moderation diagnostics and AccountSetupService file checksums also matched.
Real browser smoke remains manual; the user's application was not started/stopped.

## Agile backlog refinement — Retry Incomplete Review Moderation

This story was added after real provider outages; it was not part of the original
backlog. An authenticated STUDENT may manually retry their own SUBMITTED review.
`POST /api/student/reviews/{reviewId}/moderation/retry` uses the existing session
and CSRF header from `/api/auth/csrf`. It accepts no body or query fields. A foreign
or missing review returns safe 404, PUBLISHED/NEEDS_CORRECTION returns 409,
unauthenticated access returns 401 and a non-Student or invalid CSRF request is
rejected by existing security. Multi-role accounts with STUDENT are allowed.

The backend locks the owned Review briefly, checks its status and the **60-second
cooldown**, stores `lastModerationAttemptAt`, and commits before calling Gemini.
The same timestamp reservation is used for initial submission, US27 correction
and US28 published updates before their existing AFTER_COMMIT moderation event.
Rejected clicks and rolled-back transactions do not record an attempt or invoke AI.
The nullable timestamp represents the latest reserved moderation attempt, including
provider failures; `moderatedAt` still means completed moderation. Legacy SUBMITTED
reviews with no attempt timestamp are immediately retryable.

Two simultaneous retries cannot reserve the same review: after the first commits,
the second sees the cooldown. No database lock is held across the provider call.
Completion uses another brief lock and compares the event's microsecond-precision
attempt timestamp with the current row, so a late result cannot complete a newer
retry/correction/update. There is **no attempt counter, permanent limit, scheduler,
queue or automatic retry**. A failed attempt can be retried after the cooldown.

The existing US26 provider/policy is reused. ACCEPT becomes PUBLISHED; a violation
becomes NEEDS_CORRECTION; provider failure retains SUBMITTED and the committed attempt
timestamp. Rating, text, author, offering, academicTerm, createdAt and Review ID do
not change on retry, and no new Review is inserted. US28 UNIQUE is unchanged.

HTTP 200 returns the current safe own-review status DTO. Its two new operational
fields are `retryModerationAvailable` and `retryAfterSeconds`; no raw attempt
timestamp/provider details are exposed. Remaining seconds round up and reach zero
at 60 seconds. Cooldown requests return HTTP 429, `Retry-After: <seconds>` and only
`{message, retryAfterSeconds}`. No Teacher/public/analytics DTO receives these fields.

Review Status shows Retry Moderation only for SUBMITTED, disables it while pending
or cooling down, and displays a countdown derived from the server's remaining wait.
The backend remains authoritative after F5 or in another tab. Success replaces the
same status card with server data. Unconfirmed/network failures offer Refresh status
instead of inventing an outcome. My Reviews links SUBMITTED reviews to this same
retry/status page; Edit Review and Fix Feedback retain their existing flows.

Schema setup is explicit: run `backend/sql/review-moderation-retry.sql` against the
existing `student_feedback` database before starting the updated backend. It adds
only `public.reviews.last_moderation_attempt_at timestamp(6) with time zone NULL`,
with no default/backfill or changes to existing review data/constraints. This was
applied locally during this story and verified through PostgreSQL metadata; normal
Hibernate update is not the only record of this schema change.

Manual smoke: start the updated application yourself, open an old SUBMITTED review
through My Reviews, and click Retry Moderation. Verify the same Review ID becomes
Published/Needs correction or remains Submitted if the provider is unavailable.
For the last case, immediately retry from another tab: expect 429/cooldown, then
retry after it expires. Do not alter Gemini configuration to simulate an outage;
automated tests cover failures, concurrent tabs and stale provider responses.
No real reviews were retried during automated implementation. US34 was not started.

Verification: 16 retry tests and 159 affected backend regression tests passed.
New test fixtures were corrected to send CSRF in the same header as the frontend
and to mutate lazy JPA collections inside a transaction; production authorization
was not relaxed. Frontend: 18 retry tests and 76 affected tests passed (one overly
broad source assertion was corrected), and production build passed. Final Maven
package ran the full suite once: 722 passed, no failures/errors/skips. Real
PostgreSQL stayed at 506 Teachers / 903 Courses / 1741 CourseOfferings / 10 Reviews;
all original field checksums and US28 UNIQUE matched before/after. Only the nullable
attempt column was added. No live Gemini calls or real review retries were made.
Gemini diagnostics and AccountSetupService were preserved; no commits/staging.

## US34 — Common Topics / Concerns

`GET /api/teacher/analytics/topics?courseId=<id>&term=<exact academic term>` is
TEACHER-only. It reuses US31 scope validation and the US33 read-only published-text
selection: the session's linked Teacher, owned CourseOffering(s), selected Course,
exact Review academicTerm and PUBLISHED status. `teacherId`, unknown or duplicate
parameters are rejected. Public/Student access is forbidden; no role system changed.

Separate `TopicsAnalysisService → TopicsProvider → GeminiTopicsProvider` performs
on-demand analysis. Only the anonymous published text array is sent; no ratings,
IDs, authors, group/section, schedules, moderation metadata or sentiment output.
The existing Gemini key/model/timeout configuration is reused without changing
US26 or US33. Database reads finish before the bounded external request. There is
no cache, persistence, schema migration, scheduled work or automatic retry.

The prompt asks for recurring semantic themes supported by at least two distinct
feedback items, separating neutral/positive topics from concerns. It treats texts
as untrusted, allows criticism, forbids identifying details and requests short
neutral English labels, without summaries, recommendations or sentiment percentages.
Semantic recurrence is assessed by Gemini; JSON validation cannot prove that a
label accurately represents the source. No student privacy threshold is claimed.

Strict output: `{"topics":["Clear explanations"],"concerns":["Short deadlines"]}`.
Each array has at most 5 labels, each at most 80 Unicode code points. Surrounding
and repeated whitespace is normalized. Blank/control-character labels, duplicates
(case-insensitive, including across both lists), excess items, unexpected fields,
duplicate JSON keys and malformed/provider-blocked/truncated responses are rejected
as unavailable, not repaired into invented analysis.

The safe response includes course identity, exact term, `status`, `analysisAvailable`,
`enoughRecurringFeedback`, `analyzedReviewCount`, `topics`, and `concerns`. Zero
published reviews gives EMPTY; one gives INSUFFICIENT. Neither calls Gemini, and
both report 0 analyzed reviews. A valid provider result with no recurring themes
is AVAILABLE with empty lists and `enoughRecurringFeedback=false`. Failures return
UNAVAILABLE with empty lists and null analyzed count. Diagnostics log only model,
key presence, allowlisted category/reason, HTTP status and input count, never raw
responses, exception messages, review texts or secrets.

Teacher Analytics retains US31 filters and US33 sentiment, followed by Common
Topics / Common Concerns. Changing course/term remounts the section, hides old
results and cancels stale requests. Retry clears results and repeats only the topics
request. Empty, insufficient and unavailable states use distinct safe messages.
No individual review mapping, summary narrative, charts or US36 privacy UI was added.

Manual smoke after starting the current backend/frontend: sign in as a linked
Teacher, open `/teacher/analytics`, select an owned course/exact term, change each
filter, then refresh the direct URL. Check sentiment still works, previous topics
disappear during loading, and Retry appears if topic analysis fails. With zero/one
published review the dedicated empty/insufficient message is expected.

US34 verification: 166 focused backend tests passed, including 10 topics integration
and 32 Gemini topics/parser tests plus US31/US33/moderation/retry regressions.
53 focused frontend tests and the frontend production build passed. Maven package
ran the full backend suite once: 764 passed, no failures/errors/skips. One optional
synthetic Gemini call with four invented anonymous texts succeeded (HTTP 200,
strict parsing passed, 1 topic and 1 concern); no real feedback was used in that
call and arbitrary provider output was not logged. PostgreSQL before/after remained
506 Teachers / 903 Courses / 1741 CourseOfferings / 10 Reviews, with identical row
checksums and unchanged US28 UNIQUE. US33, US26 and AccountSetupService were unchanged.
No normal backend/frontend server was started or stopped; test servers were closed.
Browser smoke remains the manual check above. US35/US36 and Sprint 6 completion are
not claimed. Existing uncommitted work was preserved; nothing staged or committed.

## US35 — AI Summary

`GET /api/teacher/analytics/summary?courseId=<id>&term=<exact academic term>` is
TEACHER-only and reuses US31 validation and the existing published-text read.
The authenticated account's linked Teacher determines ownership; course and exact
Review academicTerm select the scope across that Teacher's relevant offerings.
`Fall 2026` and `2026-2027 1 term` are distinct. Client-supplied teacher IDs, review
IDs, raw feedback and other unsupported/duplicate query parameters are rejected.

`SummaryAnalysisService → SummaryProvider → GeminiSummaryProvider` is independent
of sentiment, topics and moderation. Only current PUBLISHED text strings go into
the request, without ratings, author/Review/offering IDs, groupSection, schedule,
verification, moderation, retry or timestamp metadata. Summary does not call or
fall back to US33/US34 outputs. Database reads finish before the external request.
The existing GEMINI_API_KEY/GEMINI_MODEL and bounded timeout configuration are reused.

The prompt treats feedback as untrusted data, forbids obeying embedded commands,
and requests one neutral English aggregate paragraph of 2–4 concise sentences.
It requires grounding in the supplied texts, cautious wording, no identities,
quotes, invented counts/percentages, causation, judgments, recommendations, scoring,
sentiment categories or topic arrays. Content grounding and sentence quality are
model instructions, not a factual correctness guarantee or US36 privacy protection.

The strict provider response is exactly `{"summary":"..."}`. The server rejects
malformed JSON, duplicate keys, trailing JSON, extra fields, missing/non-string/
blank/overlong summaries, control characters and fenced/heading/list output.
Whitespace is normalized into prose; the maximum is 1000 Unicode code points.
Provider truncation/blocking/errors also fail closed. Diagnostic logs contain only
model, key presence, safe category/reason, HTTP status and input count. They never
include exception messages, secrets, prompts, review text or generated summary.

The response contains course identity, exact term, status, analyzedReviewCount,
summary, analysisAvailable and enoughFeedback. With zero published reviews it is
EMPTY; with one it is INSUFFICIENT. Neither calls Gemini or paraphrases that one
review; both return null summary and zero analyzed count. With 2+ reviews a valid
result is AVAILABLE. Failure is UNAVAILABLE with null summary/count; enoughFeedback
still describes the 2+ input prerequisite, not provider success. There is no cache,
summary persistence, schema migration, timestamp storage or automatic retry.

AI Summary appears after the existing Sentiment and Topics/Concerns sections.
It has separate loading, success, empty, insufficient and temporary-failure states.
Filter/account changes remount the section, hide previous results and abort old
requests; late responses cannot update the next selection. Retry resets and requests
only US35, without triggering moderation, sentiment or topics. Output is escaped
React text in a paragraph, not HTML or raw JSON.

Manual browser smoke after starting the current application: sign in as a linked
Teacher, open `/teacher/analytics`, choose an owned course/exact term and verify all
three independent sections remain visible. Change each filter, refresh the direct
URL, and check old summary text is absent during loading. For zero/one published
review expect the specific empty/insufficient message. Provider failure offers Retry
only inside AI Summary. No US36 work or Sprint 6 completion is claimed.

US35 verification: 207 focused backend tests passed, including 10 summary integration
tests and 31 Gemini summary/parser tests plus affected US31/US33/US34/moderation/retry
regressions. 71 frontend tests passed, including execution of the production summary
effect with deferred HTTP responses to check stale course/term responses and isolated
Retry. Frontend production build passed. Maven package ran the full backend suite
once: 805 passed, no failures/errors/skips.

Exactly one optional live synthetic call was attempted with three invented anonymous
feedback texts. The configured gemini-3.5-flash-lite provider had a key present but
timed out at the existing 8000 ms limit (TIMEOUT / REQUEST_TIMED_OUT, no HTTP status).
No response was available for live parsing/content verification. No retry, secret
output, real-review submission or timeout/configuration change was made.

PostgreSQL remained 506 Teachers / 903 Courses / 1741 CourseOfferings / 10 Reviews.
Before/after row checksums, public column/index metadata checksums and US28 UNIQUE
were identical. No summary table/column or saved summary was introduced. Existing
US26/US31/US33/US34 providers/services and AccountSetupService retained their file
checksums. No normal server was started/stopped; temporary test servers closed and
ports 8080/5173 were free. Browser smoke remains manual. All existing uncommitted
work was preserved; no staging/commit and no US36 implementation.

## US36 — Protect Student Privacy in Analytics

`AnalyticsPrivacyPolicy.MIN_ANALYTICS_REVIEW_COUNT = 3` is the single shared rule
for Teacher AI analytics. Only PUBLISHED Reviews in the authenticated linked
Teacher + selected Course + exact Review academicTerm scope count. The existing
Teacher-owned offerings are included; each Review counts once, even when several
source offerings exist. This is a review threshold, not a count of distinct authors.
Other teachers, courses, terms, SUBMITTED and NEEDS_CORRECTION never contribute.

`GET /api/teacher/analytics/scope` now adds `privacyThresholdMet` without returning
an actual count. Its internal grouped count replaces the previous distinct-term
projection, retaining exact term options and fixed query count. The existing
`hasPublishedFeedback` boolean remains for compatibility, but no separate small-
sample availability message is displayed. No public feedback query was changed.

Each of `/sentiment`, `/topics`, and `/summary` independently checks the same policy
against its current PUBLISHED input list BEFORE any provider call. Below threshold,
all return HTTP 200, `status=PRIVACY_PROTECTED`, `privacyThresholdMet=false`,
`analysisAvailable=false`, and `analyzedReviewCount=null`. Sentiment counts/percentages
and summary are null; topic/concern arrays are empty. This supersedes the earlier
US33–US35 EMPTY/INSUFFICIENT states for 0/1/2 reviews; none is sent to Gemini.
At 3+ reviews, existing results, recurrence behavior, provider diagnostics and
temporary-provider-failure Retry behavior continue unchanged, with privacy eligibility
true. Eligibility does not guarantee provider success.

The frontend waits for scope eligibility before mounting any AI section. Below
threshold, it shows one Analytics Privacy notice: "Not enough published feedback
to display analytics while protecting student privacy." No exact small count,
individual analysis section or AI Retry button is shown. Course/term/account keys
remount the scope and clear old results immediately; aborted/late responses cannot
restore previous analytics. If an individual endpoint reports that the published
set has fallen below threshold after scope loading, the page closes all sections
and shows the same single privacy notice. Refresh rechecks current eligibility.

No privacy provider, live AI smoke, schema object, persisted count/eligibility or
analytics result is introduced. US10 public anonymous feedback and US30 Teacher
Course Feedback are unchanged. No moderation or AccountSetupService changes.

Manual smoke with existing data only: open Teacher Analytics as a linked Teacher,
select a scope below three published reviews and confirm one privacy notice with no
AI requests/Retry. For an existing eligible scope, confirm all analyses load normally.
Switch eligible → ineligible and back, then refresh the direct course/term URL.
Do not create or edit real feedback just to make these scenarios available.

US36 verification: 194 focused backend/regression tests and 97 frontend tests passed.
The single final Maven package run passed all 813 backend tests (no failures,
errors or skipped tests); the frontend production build also passed. US31 and
US33–US35 regressions are green, including provider parsing and exact scope checks.
Read-only PostgreSQL before/after snapshots match: 506 Teachers, 903 Courses,
1,741 CourseOfferings and 10 Reviews. Row-content hashes, schema metadata and the
US28 unique constraint are unchanged. Preserved provider/public-feedback files and
AccountSetupService match their starting checksums. No live Gemini call was made.
Automated frontend checks cover filter changes, stale responses and refresh;
the manual browser scenarios above remain unperformed. Temporary test servers
were closed; ports 8080 and 5173 were not listening at the final check. The user's
IntelliJ process was not controlled. No staging, commit or next story was started.
