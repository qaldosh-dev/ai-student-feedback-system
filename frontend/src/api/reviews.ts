import { AccountSessionExpired, csrfHeaders } from './auth';

export interface ReviewRequest { courseOfferingId: number; academicTerm: string; rating: number; text: string }
export interface ReviewSubmission { reviewId: number; submitted: boolean; courseOfferingId: number; academicTerm: string; rating: number; status: 'SUBMITTED' }
export interface ReviewStatusResponse {
  reviewId: number; status: 'SUBMITTED'; submittedAt: string;
  courseOfferingId: number; courseCode: string; courseName: string; teacherName: string;
  academicTerm: string; rating: number;
}

export async function getReviewStatus(reviewId: string, signal: AbortSignal): Promise<ReviewStatusResponse | null> {
  const response = await fetch(`/api/student/reviews/${encodeURIComponent(reviewId)}/status`, {
    credentials: 'same-origin', signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (response.status === 401) throw new AccountSessionExpired();
  if (response.status === 404) return null;
  if (response.status === 403) throw new Error('You do not have access to this area.');
  if (!response.ok) throw new Error('Could not load review status. Please try again.');
  return response.json();
}

export async function submitReview(input: ReviewRequest): Promise<ReviewSubmission> {
  const response = await fetch('/api/student/reviews', {
    method: 'POST', credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...await csrfHeaders() },
    body: JSON.stringify({ courseOfferingId: input.courseOfferingId, academicTerm: input.academicTerm, rating: input.rating, text: input.text }),
    signal: AbortSignal.timeout(15000),
  });
  if (response.status === 401) throw new Error('Sign in again before submitting. Your draft has been kept.');
  if (response.status === 403) throw new Error('This course is not available for review, or your session needs refreshing. Your draft has been kept.');
  if (!response.ok) throw new Error('Review could not be submitted. Check your rating and feedback, then try again.');
  return response.json();
}
