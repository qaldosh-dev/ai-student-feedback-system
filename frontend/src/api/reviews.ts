import { csrfHeaders } from './auth';

export interface ReviewRequest { courseOfferingId: number; academicTerm: string; rating: number; text: string }
export interface ReviewSubmission { reviewId: number; submitted: boolean; courseOfferingId: number; academicTerm: string; rating: number }

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
