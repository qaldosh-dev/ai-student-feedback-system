package kz.sdu.feedback.moodle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MoodleCatalogCrawlTest {
    @TempDir Path directory;

    @Test
    void discoversLastPageFromPaginationAndIgnoresOtherCategories() {
        String html = page(1).replace("page=1", "page=87")
                + "<nav class='pagination'><a href='?categoryid=99&amp;page=999'>Other</a></nav>";
        assertEquals(88, MoodleCatalogCrawl.discoverPageCount(html));
        assertThrows(IllegalArgumentException.class, () -> MoodleCatalogCrawl.discoverPageCount("<h1>Login</h1>"));
    }

    @Test
    void parsesAndExportsCachedHtmlWithoutNetwork() throws IOException {
        Files.writeString(directory.resolve("crawl.properties"), "complete=true\npageCount=2\nrequests=2\nretries=0\n");
        Files.writeString(MoodleCatalogCrawl.pagePath(directory, 0), page(10));
        Files.writeString(MoodleCatalogCrawl.pagePath(directory, 1), page(11));
        var catalog = MoodleCatalogExport.readCache(directory);
        assertEquals(2, catalog.cards().size());
        assertEquals(2, catalog.records().size());
        assertEquals(1, catalog.records().get(1).sourcePage());
        assertEquals(42L, catalog.records().getFirst().moodleTeacherId());
        MoodleCatalogExport.export(catalog, directory.resolve("analysis"));
        assertEquals(3, Files.readAllLines(directory.resolve("analysis/fall-2026-records.csv")).size());
    }

    @Test
    void refusesIncompleteCacheMissingPagesAndAccidentalSecondCrawl() throws IOException {
        Files.writeString(directory.resolve("crawl.properties"), "complete=false\npageCount=2\n");
        assertThrows(IOException.class, () -> MoodleCatalogExport.readCache(directory));
        assertThrows(IOException.class, () -> MoodleCatalogCrawl.crawl(directory));
        Files.writeString(directory.resolve("crawl.properties"), "complete=true\npageCount=2\n");
        Files.writeString(MoodleCatalogCrawl.pagePath(directory, 0), page(10));
        assertThrows(IOException.class, () -> MoodleCatalogExport.readCache(directory));
    }

    private String page(long id) {
        return "<header id='page-header'><h1>Fall 2026 (2026-2027)</h1></header>"
                + "<nav class='pagination'><a href='?categoryid=4&amp;page=1'>2</a></nav>"
                + "<div class='courses category-browse-4'><div class='position-relative'>"
                + "<div class='rui-course-card-text'><div class='rui-card-course-contacts'>"
                + "<a class='rui-card-contact rui-user-Учитель' href='/user/profile.php?id=42'>"
                + "<div class='rui-user-name'><span class='w-100'>Teacher A</span></div></a></div></div>"
                + "<div data-region='course-content' data-course-id='" + id + "'>"
                + "<h4 class='coursename'>CSS 114 Programming (Teacher A)</h4></div></div></div>";
    }
}
