package kz.sdu.feedback.review.service;

import kz.sdu.feedback.review.dto.*;
import kz.sdu.feedback.review.entity.Review;
import kz.sdu.feedback.review.entity.ReviewStatus;
import kz.sdu.feedback.review.repository.ReviewRepository;
import kz.sdu.feedback.schedule.service.ReviewEligibilityService;
import kz.sdu.feedback.user.repository.UserRepository;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import kz.sdu.feedback.review.moderation.ReviewSubmitted;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import java.util.Locale;

@Service
public class ReviewService {
    private final ReviewRepository reviews;
    private final ReviewEligibilityService eligibility;
    private final UserRepository users;
    private final CourseOfferingRepository offerings;
    private final ApplicationEventPublisher events;
    public ReviewService(ReviewRepository reviews, ReviewEligibilityService eligibility, UserRepository users, CourseOfferingRepository offerings, ApplicationEventPublisher events) {
        this.reviews = reviews; this.eligibility = eligibility; this.users = users; this.offerings = offerings;
        this.events = events;
    }

    @Transactional
    public ReviewSubmissionResponse submit(String authenticatedEmail, SubmitReviewRequest request) {
        if (request.courseOfferingId() == null || request.courseOfferingId() <= 0 || request.academicTerm() == null
                || request.academicTerm().isBlank() || request.academicTerm().length() > 255)
            throw invalid("Invalid course offering or academic term.");
        validateRating(request.rating());
        String text = validatedText(request.text());
        // Keep the author's Teacher link stable while checking eligibility and saving the review.
        // This also serializes submissions; the lock ends before external moderation.
        var author = users.findByEmailForUpdate(authenticatedEmail).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required."));
        try {
            eligibility.requireReviewEligibility(authenticatedEmail, request.courseOfferingId(), request.academicTerm());
        } catch (ResponseStatusException denied) {
            if (denied.getStatusCode().value() == 403)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This course is not available for review.");
            throw denied;
        }
        if (reviews.existsByAuthorIdAndCourseOfferingIdAndAcademicTerm(author.getId(), request.courseOfferingId(), request.academicTerm()))
            throw duplicate();
        var offering = offerings.findById(request.courseOfferingId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "This course is not available for review."));
        Review review;
        try {
            review = reviews.saveAndFlush(new Review(author, offering, request.academicTerm(), request.rating(), text));
        } catch (DataIntegrityViolationException failure) {
            // Catch only this named uniqueness rule; unrelated integrity errors are not duplicate reviews.
            if (isDuplicateConstraint(failure)) throw duplicate();
            throw failure;
        }
        beginModeration(review);
        // This receipt confirms initial SUBMITTED persistence. The status GET reads the moderation outcome.
        return ReviewSubmissionResponse.from(review);
    }
    @Transactional(readOnly = true)
    public java.util.List<OwnReviewResponse> history(String authenticatedEmail) {
        return reviews.findOwnHistory(authenticatedEmail).stream().map(OwnReviewResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ReviewStatusResponse status(String authenticatedEmail, Long reviewId) {
        // Submission is already persisted; reading it does not rerun schedule matching or eligibility.
        return ReviewStatusResponse.from(reviews.findByIdAndAuthorEmail(reviewId, authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found.")));
    }

    @Transactional
    public ReviewSubmissionResponse correct(String authenticatedEmail, Long reviewId, CorrectReviewRequest request) {
        // Lock and check ownership/status together; a second request sees the committed SUBMITTED state.
        var review = reviews.findOwnedForUpdate(reviewId, authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found."));
        if (review.getStatus() != ReviewStatus.NEEDS_CORRECTION)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only feedback needing correction can be edited.");
        review.submitCorrection(validatedText(request.text()));
        reviews.saveAndFlush(review);
        beginModeration(review);
        return ReviewSubmissionResponse.from(review);
    }

    @Transactional
    public ReviewSubmissionResponse updatePublished(String authenticatedEmail, Long reviewId, UpdateReviewRequest request) {
        var review = reviews.findOwnedForUpdate(reviewId, authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found."));
        if (review.getStatus() != ReviewStatus.PUBLISHED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only published feedback can be updated here.");
        validateRating(request.rating());
        review.submitPublishedUpdate(request.rating(), validatedText(request.text()));
        reviews.saveAndFlush(review);
        beginModeration(review);
        return ReviewSubmissionResponse.from(review);
    }

    @Transactional
    public void retryModeration(String authenticatedEmail, Long reviewId) {
        var review = reviews.findOwnedForUpdate(reviewId, authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found."));
        if (review.getStatus() != ReviewStatus.SUBMITTED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only submitted reviews can retry moderation.");
        long remaining = review.moderationRetryAfterSeconds(java.time.Instant.now());
        if (remaining > 0) throw new ModerationCooldownException(remaining);
        beginModeration(review);
    }

    private void beginModeration(Review review) {
        var attemptAt = review.beginModerationAttempt();
        reviews.saveAndFlush(review);
        // The reservation commits before the synchronous US26 provider call starts.
        events.publishEvent(new ReviewSubmitted(review.getId(), review.getText(), attemptAt));
    }

    private void validateRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) throw invalid("Rating must be between 1 and 5.");
    }

    private ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "You already submitted feedback for this course offering and term.");
    }

    private boolean isDuplicateConstraint(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                String name = violation.getConstraintName().toLowerCase(Locale.ROOT);
                if (name.equals("uk_review_author_offering_term")
                        || name.equals("public.uk_review_author_offering_term")
                        || name.startsWith("public.uk_review_author_offering_term index ")
                        || name.startsWith("public.uk_review_author_offering_term_index_")) return true;
            }
        }
        return false;
    }

    private String validatedText(String text) {
        if (text == null || text.replaceAll("(?U)\\s", "").isEmpty()) throw invalid("Feedback is required.");
        if (text.length() > 2000) throw invalid("Feedback must be 2000 characters or fewer.");
        return text.replaceAll("(?U)^\\s+|\\s+$", "");
    }

    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
