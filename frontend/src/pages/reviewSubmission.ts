import type { ReviewEligibility } from '../api/scheduleUploads';
import { submitReview } from '../api/reviews';
import { clearDraft, FEEDBACK_MAX_LENGTH, validDraft } from './reviewDraft';
import type { ReviewDraft } from './reviewDraft';

export function completeDraft(draft: ReviewDraft) {
  return validDraft(draft).rating !== null && typeof draft.text === 'string'
    && draft.text.trim().length > 0 && draft.text.length <= FEEDBACK_MAX_LENGTH;
}

// One in-flight action only; this is not the future duplicate-review business rule.
export function createReviewSubmission(target: ReviewEligibility) {
  let pending = false;
  return {
    get pending() { return pending; },
    async submit(draft: ReviewDraft) {
      if (pending) return null;
      if (!completeDraft(draft)) throw new Error('Choose a rating and enter feedback of 2000 characters or fewer.');
      pending = true;
      try {
        const response = await submitReview({ courseOfferingId: target.courseOfferingId, academicTerm: target.academicTerm, rating: draft.rating!, text: draft.text });
        if (!response.submitted || !Number.isSafeInteger(response.reviewId) || response.reviewId <= 0 || response.status !== 'SUBMITTED'
            || response.courseOfferingId !== target.courseOfferingId || response.academicTerm !== target.academicTerm)
          throw new Error('Submission could not be confirmed. Your draft has been kept.');
        return { draftCleared: clearDraft(target), reviewId: response.reviewId, status: response.status };
      } finally { pending = false; }
    },
  };
}
