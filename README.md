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
