package kz.sdu.feedback.user.seed;

import kz.sdu.feedback.FeedbackApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/** Run this main explicitly in IntelliJ with argument --local-development. */
public class DevelopmentPasswordSetupCommand {
    public static void main(String[] args) {
        if (args.length != 1 || !"--local-development".equals(args[0])) {
            throw new IllegalArgumentException("Explicit acknowledgement required: --local-development");
        }
        String student = required("DEV_STUDENT_PASSWORD");
        String teacher = required("DEV_TEACHER_PASSWORD");
        String admin = required("DEV_ADMIN_PASSWORD");
        String database = System.getenv("DB_URL");
        if (database != null && !database.equals("jdbc:postgresql://localhost:5432/student_feedback")) {
            throw new IllegalArgumentException("This command is restricted to local student_feedback PostgreSQL");
        }
        var app = new SpringApplication(FeedbackApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (var context = app.run("--spring.profiles.active=dev", "--app.seed.enabled=false",
                "--app.dev-accounts.enabled=false", "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.datasource.url=jdbc:postgresql://localhost:5432/student_feedback")) {
            context.getBean(DevelopmentPasswordSetupService.class).setup(student, teacher, admin);
        }
        System.out.println("Updated passwords for exactly three local development accounts. No other rows changed.");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required local environment variable is missing: " + name);
        }
        return value;
    }
}
