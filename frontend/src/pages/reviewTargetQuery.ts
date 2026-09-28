import type { ReviewEligibility } from '../api/scheduleUploads';

// Shared by catalog shortcuts and the review page; never a teacher/course-only target.
export function reviewTargetQuery(target: Pick<ReviewEligibility, 'courseOfferingId' | 'academicTerm'>, rating?: number) {
  const query = new URLSearchParams({ courseOfferingId: String(target.courseOfferingId), academicTerm: target.academicTerm });
  if (rating !== undefined && Number.isInteger(rating) && rating >= 1 && rating <= 5) query.set('rating', String(rating));
  return query;
}
