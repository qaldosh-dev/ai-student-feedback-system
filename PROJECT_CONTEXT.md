# AI Student Feedback System — контекст проекта

Изучено 16 сентября 2026 года по запросу пользователя.

## Источники и их полнота

- История ChatGPT «Изучи контекст проекта», ID `6aaabd8a-2374-83eb-98e3-3fba711a1e08`: получены все 7 диалоговых ходов, пагинация завершена.
- Исходное вложение этой истории «Вставленный текст.txt»: полностью прочитаны все 39 разделов.
- Таблица в сообщении истории: инструмент обрезает длинное сообщение на строке Story 24. Строки 25–30 по смыслу, оценкам, prerequisites и QA прочитаны в локальном `outputs/6aa26d1b-user-stories/Student Feedback User Stories - revised.xlsx`, лист `main`, строки 27–32. Их персональные назначения и сроки в текущей таблице напрямую не получены; не выдавать реконструкцию за прочитанные ячейки.
- Локальные Excel-файлы — более ранние версии. В revised и detailed ответственным указан TBD, даты и итерации не заполнены. Не заменять ими актуальное планирование из чата. Самый ранний файл без revised содержит другую нумерацию stories и не является текущим backlog.
- Первые три новых вложения pasted-text.txt были пустыми. Последующее вложение f2a800f0-3e3f-47e0-a03b-496bb697c1e2/pasted-text.txt успешно прочитано: все 40 названий и описаний. Это текущая версия содержания и нумерации backlog; полный текст сохранён ниже.

## Проект и принятые решения

Учебный групповой проект Project Management в SDU University. Пять участников, семь Sprint/Lab примерно по две недели. Пользователь совмещает функции Product Owner, Scrum Master и Developer. Цель — постепенно получить работающий учебный MVP и материалы для защиты.

Ключевая модель: `Teacher + Course + Semester (+ Section) = CourseOffering`. Enrollment связывает студента с конкретным offering, Review также относится к offering. Отзывы разных курсов и семестров не смешиваются. Основные сущности будущей системы: User/Student, Teacher, Course, CourseOffering, Enrollment, Review, AIAnalysis и pending verification records.

Студент входит в собственную систему, находит преподавателя, загружает Schedule PDF. После извлечения course code/name, teacher, semester и section данные сопоставляются с каталогом. Подтверждённый enrollment даёт право оценить только соответствующий offering. Неизвестные или конфликтующие записи остаются pending до проверки администратора; автоматически создавать их как verified нельзя.

PDF — ограниченный механизм подтверждения для учебного MVP, а не гарантия подлинности. Система не запрашивает пароль, 2FA, cookies или session SDU Portal и не входит в Portal от имени студента. Официальные SSO/API — возможное будущее, не текущая интеграция.

Последнее направление обсуждения — Moodle как основной и достаточный для MVP источник каталога Teacher/Course/Semester. Официальный сайт SDU — необязательный дополнительный источник для ручной проверки. Полное покрытие всех преподавателей Moodle не доказано. Утверждения старого ассистента о числе страниц и конкретных текущих курсах здесь повторно не проверялись. Schedule PDF нужен для student enrollment, а не для безусловного пополнения доверенного каталога.

Первый этап допускает mock/seed data. В конце чата предложено вручную взять 20–30 реальных offerings из Moodle, позже автоматизировать импорт в Story 37 (ранее 28); сбор ещё не выполнен. Seed — способ заполнения базы, его данные могут быть тестовыми либо полученными из источника. Тестовые записи не выдавать за подтверждённые SDU.

Преподавателю не раскрываются имя, email и student ID автора. Аналитика агрегируется по опубликованным отзывам и скрывается при недостаточном числе разных студентов, включая результаты после фильтрации. В QA используется порог 5; параметр предполагается настраиваемым.

AI: модерация до публикации (спам, травля, персональные данные; допустимая критика разрешена), затем аналитика опубликованных отзывов (sentiment, topics, strengths, concerns, summary). Flagged feedback можно исправить и повторно отправить на модерацию; администратор разбирает спорные отзывы. Один студент не создаёт повторные отзывы для одного offering.

Admin управляет каталогом, проверяет pending teachers/offerings и принимает решения по flagged reviews.

## Текущий этап и сотрудничество

Ранее выбранный начальный flow: Sign In → Browse → Search/Filter → Teacher Profile. В обновлённом backlog это stories 1, 4, 5, 7, 8 (ранее 1–5). Пользователь не задавал новый состав первого этапа; не трактовать новые stories 1–5 как автоматически выбранный scope. Нужна одна динамическая страница профиля по ID. Для начала достаточно User с ролью, Teacher, Course и связи CourseOffering, небольшого seed, API и интерфейса. Стек в прочитанной истории не выбран. Реализация этих stories не подтверждена.

Работать небольшими контролируемыми шагами: сделать → запустить → проверить → следующий шаг. Объяснять простыми словами: что → зачем → как → пример → что проверить. При ошибке разбирать её причину. Пользователь не хочет огромную автоматически созданную систему или инфраструктуру без необходимости.

Важнейшее позднее уточнение пользователя: привязка stories к Sprint/Iteration нужна для документа по Project Management. Реальный порядок разработки пользователь выбирает отдельно. Не возвращаться к критике будущих зависимостей и не переставлять stories по спринтам без запроса. Рекомендации предыдущего ассистента исправить backlog не были приняты как изменения.

Даты из исходного плана: 17.09, 01.10, 15.10, 29.10, 12.11, 26.11, 10.12.2026. В прежней таблице по старой нумерации stories 1–4 относились к Iteration 1, Story 5 — к Iteration 2. Распределение обновлённых 40 stories по итерациям не предоставлено. В видимых строках назначены Bauyrzhan, Sabyrzhan, Aigerim, Aidar, Azamat. Не определено, кто из них пользователь.

## Текущие 40 stories

Пользователь подтвердил: глобально проект не изменился. Присланы номер, название и описание каждой story. Обновлённые оценки, QA, зависимости, ответственные, спринты и дедлайны не предоставлены; старые значения нельзя автоматически приписывать новым номерам.

| # | story_title | story description |
|---:|---|---|
| 1 | Sign In to the System | As a user, I want to sign in to the feedback system so that I can securely access the features available for my role. |
| 2 | Sign Out of the System | As a user, I want to sign out of the system so that my account remains secure after I finish using it. |
| 3 | Access Features Based on Role | As a user, I want to access features based on my role so that I only see functionality relevant to me. |
| 4 | Browse Teachers and Courses | As a student, I want to browse available teachers and courses so that I can find the teacher or course I am interested in. |
| 5 | Search for a Teacher | As a student, I want to search for a teacher by name so that I can quickly find their profile. |
| 6 | Search for a Course | As a student, I want to search for a course by code or name so that I can quickly find the course I am interested in. |
| 7 | Filter Teachers by Course | As a student, I want to filter teachers by course so that I can see who teaches a specific subject. |
| 8 | View Teacher Profile | As a student, I want to view a teacher's profile so that I can see the courses they teach and their feedback results. |
| 9 | View Course Details | As a student, I want to view course details so that I can see its name, code, teachers and available semesters. |
| 10 | View Feedback by Course and Semester | As a student, I want to view feedback for a specific teacher, course and semester so that feedback from different course offerings is not mixed together. |
| 11 | View Previous Ratings | As a student, I want to see aggregated ratings from previous students so that I can better understand their experience. |
| 12 | Sort Teacher Results | As a student, I want to sort teacher results so that I can browse the catalog more conveniently. |
| 13 | Upload Schedule PDF | As a student, I want to upload my university Schedule PDF so that the system can verify which courses and teachers I actually studied with. |
| 14 | Validate Uploaded Schedule File | As a student, I want the system to validate my uploaded schedule file so that I receive a clear message when the file cannot be processed. |
| 15 | Automatically Extract Schedule Information | As a student, I want the system to automatically extract my courses, teachers, semester and sections from the uploaded schedule so that I do not have to enter them manually. |
| 16 | View Extracted Schedule Information | As a student, I want to see the information extracted from my schedule so that I know what the system detected. |
| 17 | View Verified Courses | As a student, I want to see which courses from my schedule were successfully verified so that I know which courses I can review. |
| 18 | Handle Unknown Teacher or Course | As a student, I want to be informed when a teacher or course cannot be verified so that I know it is waiting for administrator approval. |
| 19 | View Verification Status | As a student, I want to see whether my course verification is verified, pending or rejected so that I understand its current status. |
| 20 | Restrict Reviews to Verified Courses | As an administrator, I want students to review only courses they are verified to have taken so that ratings cannot be manipulated by unrelated students. |
| 21 | Select Course Offering for Review | As a student, I want to select the exact teacher, course, semester and section I am reviewing so that my feedback is connected to the correct course offering. |
| 22 | Give a Rating | As a student, I want to rate my experience from 1 to 5 so that I can quickly express my overall satisfaction. |
| 23 | Write and Submit Text Feedback | As a student, I want to write and submit detailed feedback about my course experience so that I can explain what was good or bad. |
| 24 | Submit Feedback Anonymously | As a student, I want to submit my feedback anonymously so that my identity is not shown to the teacher. |
| 25 | View Review Submission Status | As a student, I want to see the status of my submitted feedback so that I know whether it is pending, approved or flagged. |
| 26 | AI Moderation Before Publication | As an administrator, I want submitted feedback to be automatically moderated so that spam, harassment and personal information are detected before publication. |
| 27 | Correct Flagged Feedback | As a student, I want to know why my feedback was flagged so that I can edit it and submit an acceptable version. |
| 28 | Prevent Duplicate Reviews | As a student, I want the system to tell me if I have already reviewed the same course offering so that I do not accidentally submit duplicate feedback. |
| 29 | View Own Feedback History | As a student, I want to see the feedback I have previously submitted so that I can keep track of my reviews. |
| 30 | View Aggregated Course Feedback | As a teacher, I want to view aggregated feedback for my courses so that I can understand students' overall experience without seeing their identities. |
| 31 | Filter Teacher Analytics | As a teacher, I want to filter feedback by course, semester and section so that I can analyze each class separately. |
| 32 | View Overall Rating Statistics | As a teacher, I want to see the average rating and number of submitted reviews so that I can quickly understand the overall feedback result. |
| 33 | View AI Sentiment Analysis | As a teacher, I want to see how much feedback is positive, neutral or negative so that I can understand the general student sentiment. |
| 34 | View Common Topics and Concerns | As a teacher, I want AI to identify common topics, strengths and concerns in student feedback so that I can understand what students mention most often. |
| 35 | View AI Feedback Summary | As a teacher, I want to receive an AI-generated summary of student feedback so that I do not have to manually read a large number of reviews. |
| 36 | Protect Student Privacy in Analytics | As a student, I want analytics to be displayed only after enough students have submitted feedback so that my individual feedback cannot be easily identified. |
| 37 | Manage Teacher and Course Catalog | As an administrator, I want to import and update teachers and courses from public university data so that the system has an up-to-date catalog. |
| 38 | Manage Pending Teacher and Course Verification | As an administrator, I want to review pending teachers and course offerings so that student enrollments are linked to accurate catalog records. |
| 39 | Manage Flagged Reviews | As an administrator, I want to review feedback flagged by AI moderation so that acceptable feedback can be published and inappropriate content can be rejected. |
| 40 | Use Clear and Responsive Interface | As a user, I want the system interface to be clear and responsive on different screen sizes so that I can use the main features comfortably. |
Все прежние 30 смысловых stories сохранены с новой нумерацией и некоторыми уточнениями. Добавлены отдельные stories 2, 3, 6, 9, 12, 14, 16, 19, 25, 40. В Story 21 section теперь явно включена в описание выбора offering. В Story 25 перечислены статусы pending, approved, flagged; исчерпывающую внутреннюю модель статусов отдельно не согласовывали. В Story 12 не указаны критерии сортировки — не придумывать их как утверждённые требования.

Прежняя полная таблица содержала оценки, HC/LC и HV/LV, роли, prerequisites, QA, ответственных, iteration и deadline. Её QA сохраняют исторический контекст: поиск по части имени, сброс фильтра, ошибочный PDF, pending enrollment без доступа к отзыву, рейтинг 1–5, запрет дублей, повторная модерация, среднее 3.0 для оценок 1–5, порог 4/5 студентов после фильтрации, повторный импорт без дубликатов и ограничения ролей. Обновлённая полная таблица не просмотрена. Не менять backlog без соответствующего запроса.

