import type { OwnReview } from '../api/reviews';

export function existingReview(reviews: OwnReview[], target: { courseOfferingId: number; academicTerm: string }) {
  return reviews.find(review => review.courseOfferingId === target.courseOfferingId && review.academicTerm === target.academicTerm);
}

export function reviewAction(review: OwnReview) {
  switch (review.status) {
    case 'PUBLISHED': return { label: 'Edit Review', status: 'Published', href: `/student/reviews?edit=${review.reviewId}` };
    case 'NEEDS_CORRECTION': return { label: 'Fix Feedback', status: 'Needs correction', href: `/student/reviews/${review.reviewId}/status` };
    case 'SUBMITTED': return { label: 'View Status', status: 'Submitted', href: `/student/reviews/${review.reviewId}/status` };
  }
}

export function requestedReviewEdit(reviews: OwnReview[], search: string): number | null {
  const values = new URLSearchParams(search).getAll('edit');
  if (values.length !== 1 || !/^[1-9]\d*$/.test(values[0])) return null;
  return reviews.find(review => review.reviewId === Number(values[0]) && review.status === 'PUBLISHED')?.reviewId ?? null;
}
