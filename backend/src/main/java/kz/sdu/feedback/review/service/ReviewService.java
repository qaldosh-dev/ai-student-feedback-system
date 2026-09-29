package kz.sdu.feedback.review.service;

import kz.sdu.feedback.review.dto.*;
import kz.sdu.feedback.review.entity.Review;
import kz.sdu.feedback.review.repository.ReviewRepository;
import kz.sdu.feedback.schedule.service.ReviewEligibilityService;
import kz.sdu.feedback.user.repository.UserRepository;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {
    private final ReviewRepository reviews;
    private final ReviewEligibilityService eligibility;
    private final UserRepository users;
    private final CourseOfferingRepository offerings;
    public ReviewService(ReviewRepository reviews, ReviewEligibilityService eligibility, UserRepository users, CourseOfferingRepository offerings) {
        this.reviews = reviews; this.eligibility = eligibility; this.users = users; this.offerings = offerings;
    }

    @Transactional
    public ReviewSubmissionResponse submit(String authenticatedEmail, SubmitReviewRequest request) {
        if (request.courseOfferingId() == null || request.courseOfferingId() <= 0 || request.academicTerm() == null
                || request.academicTerm().isBlank() || request.academicTerm().length() > 255)
            throw invalid("Invalid course offering or academic term.");
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5)
            throw invalid("Rating must be between 1 and 5.");
        if (request.text() == null || request.text().replaceAll("(?U)\\s", "").isEmpty()) throw invalid("Feedback is required.");
        if (request.text().length() > 2000) throw invalid("Feedback must be 2000 characters or fewer.");
        try {
            eligibility.requireReviewEligibility(authenticatedEmail, request.courseOfferingId(), request.academicTerm());
        } catch (ResponseStatusException denied) {
            if (denied.getStatusCode().value() == 403)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This course is not available for review.");
            throw denied;
        }
        var author = users.findByEmail(authenticatedEmail).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required."));
        var offering = offerings.findById(request.courseOfferingId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "This course is not available for review."));
        String text = request.text().replaceAll("(?U)^\\s+|\\s+$", "");
        return ReviewSubmissionResponse.from(reviews.saveAndFlush(new Review(author, offering, request.academicTerm(), request.rating(), text)));
    }
    @Transactional(readOnly = true)
    public ReviewStatusResponse status(String authenticatedEmail, Long reviewId) {
        // Submission is already persisted; reading it does not rerun schedule matching or eligibility.
        return ReviewStatusResponse.from(reviews.findByIdAndAuthorEmail(reviewId, authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found.")));
    }

    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
