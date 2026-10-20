package kz.sdu.feedback.review.service;

import java.util.*;
import kz.sdu.feedback.auth.dto.CurrentUser;
import kz.sdu.feedback.auth.service.AccountDetailsService;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import kz.sdu.feedback.review.dto.TeacherAnalyticsResponse;
import kz.sdu.feedback.review.dto.TeacherAnalyticsResponse.CourseOption;
import kz.sdu.feedback.review.repository.ReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TeacherAnalyticsService {
    private final AccountDetailsService accounts;
    private final CourseOfferingRepository offerings;
    private final ReviewRepository reviews;
    public TeacherAnalyticsService(AccountDetailsService accounts, CourseOfferingRepository offerings, ReviewRepository reviews) {
        this.accounts = accounts; this.offerings = offerings; this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    public TeacherAnalyticsResponse scope(String authenticatedEmail, Long courseId, String term) {
        return scope(linkedTeacherId(authenticatedEmail), courseId, term);
    }

    // Finish database reads before the sentiment service calls the external provider.
    @Transactional(readOnly = true)
    public SentimentInput sentimentInput(String authenticatedEmail, Long courseId, String term) {
        Long teacherId = linkedTeacherId(authenticatedEmail);
        if (courseId == null || term == null) throw invalidScope();
        var scope = scope(teacherId, courseId, term);
        var course = scope.courses().stream().filter(option -> option.courseId().equals(courseId)).findFirst().orElseThrow();
        return new SentimentInput(course, scope.selectedAcademicTerm(),
                // One extra item detects the extreme safety ceiling. Providers reject the entire
                // analysis above that ceiling; this prefix is NEVER presented as a complete result.
                List.copyOf(reviews.findPublishedSentimentTexts(teacherId, courseId, term,
                        org.springframework.data.domain.PageRequest.of(0, kz.sdu.feedback.gemini.GeminiWork.MAX_REVIEWS + 1))));
    }

    public record SentimentInput(CourseOption course, String term, List<String> texts) {
        public boolean privacyThresholdMet() {
            return AnalyticsPrivacyPolicy.thresholdMet(texts.size());
        }
    }

    private Long linkedTeacherId(String authenticatedEmail) {
        CurrentUser account;
        try { account = accounts.currentUser(authenticatedEmail); }
        catch (UsernameNotFoundException missing) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required.");
        }
        if (account.linkedTeacher() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No teacher profile is linked to this account.");
        return account.linkedTeacher().id();
    }

    private TeacherAnalyticsResponse scope(Long teacherId, Long courseId, String term) {
        var catalog = offerings.findProfileCourses(teacherId);
        var published = new HashMap<ScopeKey, Long>();
        for (var row : reviews.findPublishedAnalyticsTerms(teacherId))
            published.put(new ScopeKey(row.getCourseId(), row.getAcademicTerm()), row.getReviewCount());

        var terms = new LinkedHashMap<Long, SortedSet<String>>();
        var identities = new LinkedHashMap<Long, CourseOfferingRepository.ProfileCourseTerm>();
        for (var row : catalog) {
            identities.putIfAbsent(row.getCourseId(), row);
            terms.computeIfAbsent(row.getCourseId(), ignored -> new TreeSet<>(Comparator.reverseOrder())).add(row.getAcademicTerm());
        }
        for (var key : published.keySet()) {
            if (terms.containsKey(key.courseId())) terms.get(key.courseId()).add(key.term());
        }
        var courses = identities.values().stream().map(row -> new CourseOption(row.getCourseId(), row.getCourseCode(),
                row.getCourseName(), List.copyOf(terms.get(row.getCourseId())))).toList();
        if (courses.isEmpty()) {
            if (courseId != null || term != null) throw invalidScope();
            return new TeacherAnalyticsResponse(List.of(), null, null, false, false);
        }
        if (courseId == null && term != null) throw invalidScope();
        var selected = courseId == null ? courses.getFirst() : courses.stream()
                .filter(course -> course.courseId().equals(courseId)).findFirst().orElseThrow(TeacherAnalyticsService::invalidScope);
        String selectedTerm = term == null ? selected.academicTerms().getFirst() : term;
        if (!selected.academicTerms().contains(selectedTerm)) throw invalidScope();
        long count = published.getOrDefault(new ScopeKey(selected.courseId(), selectedTerm), 0L);
        return new TeacherAnalyticsResponse(courses, selected.courseId(), selectedTerm,
                count > 0, AnalyticsPrivacyPolicy.thresholdMet(count));
    }

    private static ResponseStatusException invalidScope() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Analytics selection not found.");
    }
    private record ScopeKey(Long courseId, String term) { }
}
