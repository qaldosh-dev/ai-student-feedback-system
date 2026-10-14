package kz.sdu.feedback.review.service;

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import kz.sdu.feedback.review.dto.PublicFeedbackResponse;
import kz.sdu.feedback.review.dto.PublicFeedbackResponse.Target;
import kz.sdu.feedback.review.repository.ReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublicFeedbackService {
    private final CourseOfferingRepository offerings;
    private final ReviewRepository reviews;

    public PublicFeedbackService(CourseOfferingRepository offerings, ReviewRepository reviews) {
        this.offerings = offerings;
        this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    public PublicFeedbackResponse feedback(Long teacherId, Long courseId, Long offeringId, String term) {
        var relationship = offerings.findFeedbackOfferings(teacherId, courseId);
        if (relationship.isEmpty()) throw notFound();
        // Never infer that Fall 2026 and a schedule/review academic term are equivalent.
        var targets = new TreeSet<Target>(Comparator.comparing(Target::academicTerm).reversed()
                .thenComparing(Target::courseOfferingId));
        for (var row : relationship) targets.add(new Target(row.getCourseOfferingId(), row.getAcademicTerm()));
        for (var row : reviews.findPublicTargets(teacherId, courseId))
            targets.add(new Target(row.getCourseOfferingId(), row.getAcademicTerm()));

        Long selectedId = null;
        if (offeringId != null && term == null) throw notFound();
        if (term != null) {
            var matches = targets.stream().filter(target -> target.academicTerm().equals(term)
                    && (offeringId == null || target.courseOfferingId().equals(offeringId))).toList();
            if (matches.isEmpty()) throw notFound();
            // Ambiguous source shells require selection, never merge their reviews or pick the first.
            if (matches.size() == 1) selectedId = matches.getFirst().courseOfferingId();
        }
        List<PublicFeedbackResponse.Feedback> feedback = selectedId == null ? List.of() :
                reviews.findPublicFeedback(selectedId, term).stream().map(row ->
                        new PublicFeedbackResponse.Feedback(row.getRating(), row.getText(), row.getPublishedAt())).toList();
        var catalog = relationship.getFirst();
        return new PublicFeedbackResponse(teacherId, catalog.getTeacherName(), courseId, catalog.getCourseCode(),
                catalog.getCourseName(), selectedId, term, List.copyOf(targets), feedback);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback selection not found.");
    }
}
