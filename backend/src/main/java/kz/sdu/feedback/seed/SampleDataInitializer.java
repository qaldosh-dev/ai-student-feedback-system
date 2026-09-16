package kz.sdu.feedback.seed;

import java.util.List;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.course.repository.CourseRepository;
import kz.sdu.feedback.offering.entity.CourseOffering;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class SampleDataInitializer implements CommandLineRunner {
    private final TeacherRepository teachers;
    private final CourseRepository courses;
    private final CourseOfferingRepository offerings;

    public SampleDataInitializer(TeacherRepository teachers, CourseRepository courses,
                                 CourseOfferingRepository offerings) {
        this.teachers = teachers;
        this.courses = courses;
        this.offerings = offerings;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Seed an empty catalog once. Never overwrite existing records on restart.
        if (teachers.count() > 0 || courses.count() > 0 || offerings.count() > 0) {
            return;
        }

        // Fictional people and teaching assignments, inspired by a university catalog.
        Teacher aida = new Teacher("Aida Sarsenova", "Computer Science");
        Teacher dias = new Teacher("Dias Beketov", "Computer Science");
        Teacher madina = new Teacher("Madina Nurpeisova", "Mathematics");
        Teacher ruslan = new Teacher("Ruslan Tulegenov", "Software Engineering");
        Teacher dana = new Teacher("Dana Askarova", "Language Education");
        Teacher alikhan = new Teacher("Alikhan Omarov", "Business");
        teachers.saveAll(List.of(aida, dias, madina, ruslan, dana, alikhan));

        Course programming = new Course("CSS 114", "Programming Fundamentals");
        Course databases = new Course("CSS 212", "Database Systems");
        Course web = new Course("CSS 232", "Web Programming: Back End");
        Course calculus = new Course("MAT 151", "Calculus I");
        Course english = new Course("ENG 101", "Academic English");
        Course management = new Course("CSS 465", "Project Management");
        courses.saveAll(List.of(programming, databases, web, calculus, english, management));

        offerings.saveAll(List.of(
                new CourseOffering(aida, programming, "Fall 2026", "01"),
                new CourseOffering(dias, programming, "Fall 2026", "02"),
                new CourseOffering(dias, databases, "Fall 2026", "01"),
                new CourseOffering(ruslan, web, "Fall 2026", "01"),
                new CourseOffering(madina, calculus, "Fall 2026", "01"),
                new CourseOffering(dana, english, "Fall 2026", "01"),
                new CourseOffering(alikhan, management, "Fall 2026", "01"),
                new CourseOffering(ruslan, management, "Fall 2026", "02"),
                new CourseOffering(aida, databases, "Spring 2027", "01"),
                new CourseOffering(dana, english, "Fall 2026", "02")
        ));
    }
}
