package kz.sdu.feedback.review.moderation;

import java.util.Objects;
import kz.sdu.feedback.review.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReviewModerationService {
    private static final Logger log = LoggerFactory.getLogger(ReviewModerationService.class);
    private final ModerationProvider provider;
    private final ReviewRepository reviews;
    private final TransactionTemplate updateTransaction;

    public ReviewModerationService(ModerationProvider provider, ReviewRepository reviews, PlatformTransactionManager manager) {
        this.provider = provider;
        this.reviews = reviews;
        updateTransaction = new TransactionTemplate(manager);
        updateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener // AFTER_COMMIT, synchronous; no queue or automatic retries.
    public void afterSubmission(ReviewSubmitted event) {
        try {
            var result = Objects.requireNonNull(provider.moderate(event.feedback()));
            updateTransaction.executeWithoutResult(transaction -> {
                var review = reviews.findById(event.reviewId()).orElseThrow();
                review.completeModeration(result);
                reviews.saveAndFlush(review);
            });
        } catch (RuntimeException failure) {
            // Submission is already committed. Never log provider bodies, keys, feedback or identity.
            log.warn("Review moderation incomplete; submitted review retained.");
        }
    }
}
