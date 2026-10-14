package kz.sdu.feedback.review.service;

import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import kz.sdu.feedback.review.dto.PreviousRatingsResponse;
import kz.sdu.feedback.review.repository.ReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PreviousRatingsService {
    private final CourseOfferingRepository offerings;
    private final ReviewRepository reviews;

    public PreviousRatingsService(CourseOfferingRepository offerings, ReviewRepository reviews) {
        this.offerings = offerings;
        this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    public PreviousRatingsResponse history(Long teacherId, Long courseId) {
        var relationship = offerings.findFeedbackOfferings(teacherId, courseId);
        if (relationship.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rating history not found.");
        var catalog = relationship.getFirst();
        var ratings = reviews.findPublishedRatingHistory(teacherId, courseId).stream().map(row ->
                new PreviousRatingsResponse.TermRating(row.getCourseOfferingId(), row.getAcademicTerm(),
                        row.getAverageRating(), row.getPublishedReviewCount())).toList();
        return new PreviousRatingsResponse(teacherId, catalog.getTeacherName(), courseId, catalog.getCourseCode(),
                catalog.getCourseName(), ratings);
    }
}
