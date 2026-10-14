package kz.sdu.feedback.teacher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.course.repository.CourseRepository;
import kz.sdu.feedback.offering.entity.CourseOffering;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import kz.sdu.feedback.review.entity.*;
import kz.sdu.feedback.review.moderation.*;
import kz.sdu.feedback.review.repository.ReviewRepository;
import kz.sdu.feedback.teacher.dto.TeacherSort;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.teacher.service.TeacherService;
import kz.sdu.feedback.user.entity.*;
import kz.sdu.feedback.user.repository.UserRepository;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = FeedbackApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:teacher-sorting;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false",
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "logging.level.org.hibernate.stat=OFF", "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@AutoConfigureMockMvc
@Transactional
class TeacherSortingTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TeacherRepository teachers;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired ReviewRepository reviews;
    @Autowired UserRepository users;
    @Autowired TeacherService service;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @MockitoBean ModerationProvider provider;
    Teacher privateOnly, beta, empty, gamma, twin1, twin2, zeta;
    CourseOffering betaFirst, betaOther, privateOffering, zetaOffering;
    int authorNumber;

    @BeforeEach void setup() {
        privateOnly = teacher("Aardvark"); beta = teacher("Beta"); empty = teacher("Empty");
        gamma = teacher("Gamma"); twin1 = teacher("Twin"); twin2 = teacher("Twin"); zeta = teacher("Zeta");
        var common = courses.saveAndFlush(new Course("ABC 100", "Common Subject"));
        var other = courses.saveAndFlush(new Course("XYZ 200", "Other Subject"));
        privateOffering = offering(privateOnly, common, "01-N");
        betaFirst = offering(beta, common, "01-N"); betaOther = offering(beta, other, "02-P");
        offering(beta, common, "03-P"); // An extra matching offering must not multiply review rows.
        publish(betaFirst, "Fall 2026", 5); publish(betaFirst, "Spring 2025", 5);
        publish(betaOther, "Fall 2026", 2); // Weighted teacher average = 4, not average of offering averages.
        var gammaOffering = offering(gamma, common, "01-N");
        for (int i = 0; i < 4; i++) publish(gammaOffering, "Fall 2026", 3);
        publish(offering(twin1, common, "01-N"), "Fall 2026", 4);
        publish(offering(twin2, common, "01-N"), "Fall 2026", 4);
        zetaOffering = offering(zeta, common, "01-N"); publish(zetaOffering, "Fall 2026", 5);
        save(privateOffering, "Private term", 5, ReviewStatus.SUBMITTED);
        save(privateOffering, "Private term", 5, ReviewStatus.NEEDS_CORRECTION);
    }
    Teacher teacher(String name) { return teachers.saveAndFlush(new Teacher(name, null)); }
    CourseOffering offering(Teacher teacher, Course course, String section) {
        return offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", section));
    }
    void publish(CourseOffering offering, String term, int rating) { save(offering, term, rating, ReviewStatus.PUBLISHED); }
    Review save(CourseOffering offering, String term, int rating, ReviewStatus status) {
        var author = users.saveAndFlush(new User("private-" + (++authorNumber) + "@feedback.test", "private-hash",
                Set.of(UserRole.STUDENT), true, null));
        var review = new Review(author, offering, term, rating, "Private text");
        if (status != ReviewStatus.SUBMITTED) review.completeModeration(new ModerationResult(
                status == ReviewStatus.PUBLISHED ? ModerationResult.Decision.ACCEPT : ModerationResult.Decision.NEEDS_CORRECTION,
                status == ReviewStatus.PUBLISHED ? Set.of() : Set.of(ModerationCategory.THREAT)));
        return reviews.saveAndFlush(review);
    }
    String read(String sort, String name, String course) throws Exception {
        var request = get("/api/teachers").param("q", name).param("courseQ", course);
        if (sort != null) request.param("sort", sort);
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    List<Long> ids(String body) {
        var result = new ArrayList<Long>();
        for (var row : json.readTree(body)) {
            assertEquals(Set.of("id", "name", "department"), row.propertyNames());
            result.add(row.get("id").longValue());
        }
        return result;
    }
    void order(String sort, Teacher... expected) throws Exception {
        assertEquals(java.util.Arrays.stream(expected).map(Teacher::getId).toList(), ids(read(sort, "", "")));
    }

    @Test void omittedSortPreservesAlphabeticalDefaultAndDto() throws Exception {
        order(null, privateOnly, beta, empty, gamma, twin1, twin2, zeta);
        assertEquals(read(null, "", ""), read("NAME_ASC", "", ""));
    }
    @Test void ascendingNamesUseIdForEqualNames() throws Exception {
        order("NAME_ASC", privateOnly, beta, empty, gamma, twin1, twin2, zeta);
    }
    @Test void descendingNamesStillUseAscendingIdForEqualNames() throws Exception {
        order("NAME_DESC", zeta, twin1, twin2, gamma, empty, beta, privateOnly);
    }
    @Test void highestRatingIncludesAllOfferingsAndTermsWithoutMixingTeachers() throws Exception {
        order("RATING_DESC", zeta, beta, twin1, twin2, gamma, privateOnly, empty);
    }
    @Test void lowestRatingKeepsUnratedAndPrivateOnlyLastNotZeroRated() throws Exception {
        order("RATING_ASC", gamma, beta, twin1, twin2, zeta, privateOnly, empty);
    }
    @Test void mostReviewedCountsPublishedReviewsNotOfferings() throws Exception {
        order("REVIEW_COUNT_DESC", gamma, beta, twin1, twin2, zeta, privateOnly, empty);
    }
    @Test void feedbackFirstKeepsAllResultsAndDoesNotRankByCountOrAverage() throws Exception {
        order("HAS_FEEDBACK_FIRST", beta, gamma, twin1, twin2, zeta, privateOnly, empty);
    }
    @ParameterizedTest @EnumSource(TeacherSort.class)
    void unpublishedReviewsHaveNoObservableEffectOnAnySort(TeacherSort sort) throws Exception {
        var before = read(sort.name(), "", "");
        for (int i = 0; i < 5; i++) {
            save(betaFirst, "Private", 1, ReviewStatus.SUBMITTED);
            save(zetaOffering, "Private", 1, ReviewStatus.NEEDS_CORRECTION);
            save(privateOffering, "Private", 5, ReviewStatus.NEEDS_CORRECTION);
        }
        assertEquals(before, read(sort.name(), "", "")); verifyNoInteractions(provider);
    }
    @ParameterizedTest @EnumSource(TeacherSort.class)
    void searchesAndCourseFiltersComposeWithoutChangingMatchingSemantics(TeacherSort sort) throws Exception {
        assertEquals(List.of(beta.getId()), ids(read(sort.name(), "  bEt  ", "")));
        assertEquals(List.of(beta.getId()), ids(read(sort.name(), "", "  xYz  ")));
        assertEquals(List.of(beta.getId()), ids(read(sort.name(), " BeT ", "other SUBJECT")));
        assertEquals(List.of(), ids(read(sort.name(), "Gamma", "XYZ")));
        assertEquals(List.of(), ids(read(sort.name(), "doesnotexist", "")));
        assertEquals(List.of(), ids(read(sort.name(), "", "doesnotexist")));
        assertEquals(List.of(), ids(read(sort.name(), "%", "")));
        assertEquals(read(sort.name(), "", ""), read(sort.name(), "  ", "  "));
    }
    @Test void courseFilterLimitsTeachersButNotTheirRatingScope() throws Exception {
        // Beta's ABC-only average is 5; its all-course average is 4. Zeta must stay first.
        assertEquals(List.of(zeta.getId(), beta.getId(), twin1.getId(), twin2.getId(), gamma.getId(), privateOnly.getId()),
                ids(read("RATING_DESC", "", "ABC")));
        assertEquals(List.of(gamma.getId(), beta.getId(), twin1.getId(), twin2.getId(), zeta.getId(), privateOnly.getId()),
                ids(read("REVIEW_COUNT_DESC", "", "ABC")));
    }
    @ParameterizedTest @ValueSource(strings = {"", "rating_desc", "invalid", "name; drop table teachers", " NAME_ASC "})
    void invalidExplicitSortIsSafe400(String sort) throws Exception {
        mvc.perform(get("/api/teachers").param("sort", sort)).andExpect(status().isBadRequest())
                .andExpect(content().json("{\"message\":\"Unsupported teacher sort.\"}"));
        assertEquals(7, teachers.count());
    }
    @ParameterizedTest @EnumSource(TeacherSort.class)
    void eachSortUsesOneDatabaseQueryAndDoesNotWrite(TeacherSort sort) {
        em.flush(); em.clear();
        var statistics = emf.unwrap(SessionFactory.class).getStatistics(); statistics.clear();
        assertEquals(7, service.getTeachers("", "", sort).size());
        assertEquals(1, statistics.getPrepareStatementCount());
        assertEquals(0, statistics.getEntityInsertCount()); assertEquals(0, statistics.getEntityUpdateCount());
        assertEquals(0, statistics.getEntityDeleteCount());
    }
    @Test void publicationChangesAreReflectedWithoutCachedStats() throws Exception {
        var review = reviews.findAll().stream().filter(r -> r.getCourseOffering().getId().equals(zetaOffering.getId())).findFirst().orElseThrow();
        review.submitPublishedUpdate(1, "Pending update"); reviews.saveAndFlush(review);
        order("RATING_DESC", beta, twin1, twin2, gamma, privateOnly, empty, zeta);
    }
}
