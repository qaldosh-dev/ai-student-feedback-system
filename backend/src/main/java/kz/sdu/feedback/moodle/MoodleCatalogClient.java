package kz.sdu.feedback.moodle;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;

public class MoodleCatalogClient {
    public static final URI FIRST_PAGE = URI.create(
            "https://moodle.sdu.edu.kz/course/index.php?categoryid=4&lang=ru");

    public String fetchFirstPage() throws IOException, InterruptedException {
        return fetchPage(0);
    }

    public String fetchPage(int page) throws IOException, InterruptedException {
        if (page < 0) {
            throw new IllegalArgumentException("Page number must be non-negative.");
        }
        URI url = URI.create(FIRST_PAGE + "&browse=courses&perpage=20&page=" + page);
        // No CookieHandler, authentication, redirects or retries.
        try (var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()) {
            var request = HttpRequest.newBuilder(url)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "SDU-Student-Feedback-Preview/0.1 (public catalog preview)")
                    .header("Accept", "text/html")
                    .GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IOException("Public Moodle page returned HTTP " + response.statusCode());
            }
            if (!response.headers().firstValue("Content-Type").orElse("")
                    .toLowerCase(Locale.ROOT).contains("text/html")) {
                throw new IOException("Public Moodle page did not return HTML.");
            }
            return response.body();
        } catch (IOException exception) {
            throw new IOException("Moodle page " + page + " failed (" + url + "): "
                    + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("Moodle page " + page + " was interrupted.");
        }
    }

    // Run this main directly for a preview. It never starts Spring or connects to PostgreSQL.
    public static void main(String[] args) throws IOException, InterruptedException {
        var client = new MoodleCatalogClient();
        var parser = new MoodleCatalogParser();
        List<MoodleCourseRecord> records = new ArrayList<>();
        Set<Long> courseIds = new HashSet<>();
        int totalCards = 0;
        for (int page = 0; page <= 4; page++) {
            if (page > 0) Thread.sleep(1000);
            String html = client.fetchPage(page);
            MoodleCatalogParser.ParseResult result;
            try {
                result = parser.parse(html, page);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Cannot parse Moodle page " + page
                        + "; preview is incomplete and has been stopped.", exception);
            }
            totalCards += result.courseCount();
            records.addAll(result.records());
            courseIds.addAll(result.courseIds());
            System.out.printf("Page %d: %d cards, %d relationships, %d skipped, %d warnings%n",
                    page, result.courseCount(), result.records().size(), result.skipped().size(), result.warnings().size());
            result.skipped().forEach(reason -> System.out.println("Skipped: " + reason));
            result.warnings().forEach(reason -> System.out.println("Warning: " + reason));
        }
        System.out.println("Completed 5 sequential requests; source: " + FIRST_PAGE);
        System.out.println("Total cards examined: " + totalCards);
        System.out.println("Unique Moodle IDs across examined cards: " + courseIds.size());
        new MoodleDuplicateAnalysis().printReport(records);
    }
}
