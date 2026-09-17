package kz.sdu.feedback.moodle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Properties;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;

/** Explicit development command, never a Spring component or a startup task. */
public class MoodleCatalogCrawl {
    public static int discoverPageCount(String html) {
        var document = Jsoup.parse(html);
        var heading = document.selectFirst("#page-header h1");
        if (heading == null || !heading.text().equals("Fall 2026 (2026-2027)")) {
            throw new IllegalArgumentException("Expected the Fall 2026 category before discovering pagination.");
        }
        int lastPage = 0;
        var pagePattern = Pattern.compile("[?&]page=(\\d+)(?:&|$)");
        for (var link : document.select(".pagination a[href]")) {
            String href = link.attr("href");
            if (!href.matches(".*[?&]categoryid=4(?:&.*)?$")) continue;
            var match = pagePattern.matcher(href);
            if (match.find()) lastPage = Math.max(lastPage, Integer.parseInt(match.group(1)));
        }
        // A changed/incorrect pagination layout must not trigger an unbounded crawl.
        if (lastPage > 1000) throw new IllegalArgumentException("Unexpectedly large page range: " + lastPage);
        return lastPage + 1;
    }

    public static void crawl(Path cacheDirectory) throws IOException, InterruptedException {
        Files.createDirectories(cacheDirectory);
        if (Files.exists(cacheDirectory.resolve("crawl.properties"))
                || Files.exists(pagePath(cacheDirectory, 0))) {
            throw new IOException("Cache already exists. Reuse it for analysis; a second crawl is not automatic.");
        }
        var status = new Properties();
        status.setProperty("source", MoodleCatalogClient.FIRST_PAGE.toString());
        status.setProperty("startedAt", Instant.now().toString());
        status.setProperty("complete", "false");
        status.setProperty("requests", "0");
        status.setProperty("retries", "0");
        status.setProperty("successfulPages", "0");
        int pageCount = 1;
        int page = 0;
        var client = new MoodleCatalogClient();
        try {
            for (; page < pageCount; page++) {
                if (page > 0) Thread.sleep(1000);
                status.setProperty("requests", Integer.toString(page + 1));
                saveStatus(cacheDirectory, status);
                String html = client.fetchPage(page);
                Files.writeString(pagePath(cacheDirectory, page), html, StandardOpenOption.CREATE_NEW);
                status.setProperty("successfulPages", Integer.toString(page + 1));
                if (page == 0) {
                    pageCount = discoverPageCount(html);
                    status.setProperty("pageCount", Integer.toString(pageCount));
                }
                saveStatus(cacheDirectory, status);
                System.out.printf("Cached page %d / %d%n", page, pageCount - 1);
            }
            status.setProperty("complete", "true");
        } catch (IOException | InterruptedException | RuntimeException exception) {
            status.setProperty("failedPage", Integer.toString(page));
            status.setProperty("error", exception.toString());
            throw exception;
        } finally {
            status.setProperty("finishedAt", Instant.now().toString());
            saveStatus(cacheDirectory, status);
        }
    }

    public static Path pagePath(Path cacheDirectory, int page) {
        return cacheDirectory.resolve("page-%03d.html".formatted(page));
    }

    private static void saveStatus(Path directory, Properties status) throws IOException {
        try (var writer = Files.newBufferedWriter(directory.resolve("crawl.properties"))) {
            status.store(writer, "Public Moodle crawl; no cookies or credentials are used");
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 2 || !args[0].equals("crawl")) {
            throw new IllegalArgumentException("Usage: MoodleCatalogCrawl crawl <cache-directory>");
        }
        crawl(Path.of(args[1]));
    }
}
