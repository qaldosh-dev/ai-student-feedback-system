import { AccountSessionExpired, csrfHeaders } from './auth';

export interface ReviewRequest { courseOfferingId: number; academicTerm: string; rating: number; text: string }
export interface ReviewSubmission { reviewId: number; submitted: boolean; courseOfferingId: number; academicTerm: string; rating: number; status: 'SUBMITTED' }
export interface ReviewStatusResponse {
  reviewId: number; status: 'SUBMITTED' | 'PUBLISHED' | 'NEEDS_CORRECTION'; submittedAt: string;
  courseOfferingId: number; courseCode: string; courseName: string; teacherName: string;
  academicTerm: string; rating: number; moderatedAt: string | null;
  moderationReasons: { category: string; message: string }[];
  text: string | null; // Own flagged feedback only; never author identity.
  retryModerationAvailable: boolean;
  retryAfterSeconds: number;
}

export type OwnReview = Omit<ReviewStatusResponse, 'courseOfferingId' | 'text' | 'retryModerationAvailable' | 'retryAfterSeconds'> & { text: string };

export class ModerationRetryCooldown extends Error {
  constructor(public retryAfterSeconds: number) { super('Moderation was recently attempted. Please try again later.'); }
}

export async function retryReviewModeration(reviewId: number): Promise<ReviewStatusResponse> {
  const response = await fetch(`/api/student/reviews/${reviewId}/moderation/retry`, {
    method: 'POST', credentials: 'same-origin', headers: await csrfHeaders(), signal: AbortSignal.timeout(15000),
  });
  if (response.status === 401) throw new AccountSessionExpired();
  if (response.status === 429) {
    const result = await response.json().catch(() => null);
    const wait = result?.retryAfterSeconds;
    if (Number.isSafeInteger(wait) && wait > 0) throw new ModerationRetryCooldown(wait);
  }
  if (response.status === 404) throw new Error('Review not found or unavailable. Refresh its status.');
  if (response.status === 409) throw new Error('This review is no longer awaiting moderation. Refresh its status.');
  if (!response.ok) throw new Error('Moderation retry could not be confirmed. Refresh the status before trying again.');
  const result: ReviewStatusResponse = await response.json();
  if (result.reviewId !== reviewId || !['SUBMITTED', 'PUBLISHED', 'NEEDS_CORRECTION'].includes(result.status))
    throw new Error('Moderation retry could not be confirmed. Refresh the status before trying again.');
  return result;
}

export async function getOwnReviews(signal: AbortSignal): Promise<OwnReview[]> {
  const response = await fetch('/api/student/reviews', {
    credentials: 'same-origin', signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (response.status === 401) throw new AccountSessionExpired();
  if (!response.ok) throw new Error('Could not load your reviews. Please try again.');
  return response.json();
}

export async function updatePublishedReview(reviewId: number, rating: number, text: string): Promise<void> {
  const response = await fetch(`/api/student/reviews/${reviewId}`, {
    method: 'PUT', credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...await csrfHeaders() },
    body: JSON.stringify({ rating, text }), signal: AbortSignal.timeout(15000),
  });
  if (response.status === 401) throw new AccountSessionExpired();
  if (response.status === 404) throw new Error('Review not found or unavailable.');
  if (response.status === 409) throw new Error('This review is no longer available for editing. Refresh your reviews.');
  if (response.status === 400) throw new Error('Choose a rating from 1 to 5 and enter feedback of 2000 characters or fewer.');
  if (response.status === 403) throw new Error('Editing is not allowed, or your session needs refreshing.');
  if (!response.ok) throw new Error('Update could not be confirmed. Refresh your reviews before trying again.');
  const result = await response.json();
  if (result.reviewId !== reviewId || result.submitted !== true || result.status !== 'SUBMITTED')
    throw new Error('Update could not be confirmed. Refresh your reviews before trying again.');
}

export async function correctReview(reviewId: number, text: string): Promise<void> {
  const response = await fetch(`/api/student/reviews/${reviewId}/correction`, {
    method: 'PUT', credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...await csrfHeaders() },
    body: JSON.stringify({ text }), signal: AbortSignal.timeout(15000),
  });
  if (response.status === 401) throw new AccountSessionExpired();
  if (response.status === 404) throw new Error('Review not found or unavailable.');
  if (response.status === 409) throw new Error('This review is no longer available for correction. Refresh its status.');
  if (response.status === 403) throw new Error('Correction is not allowed, or your session needs refreshing.');
  if (response.status === 400) throw new Error('Enter feedback of 2000 characters or fewer.');
  if (!response.ok) throw new Error('Correction could not be confirmed. Refresh the status before trying again.');
  const result = await response.json();
  if (result.reviewId !== reviewId || result.submitted !== true || result.status !== 'SUBMITTED')
    throw new Error('Correction could not be confirmed. Refresh the status before trying again.');
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
  if (response.status === 409) throw new Error('You already submitted feedback for this course offering and term. Your draft has been kept.');
  if (response.status === 403) throw new Error('This course is not available for review, or your session needs refreshing. Your draft has been kept.');
  if (!response.ok) throw new Error('Review could not be submitted. Check your rating and feedback, then try again.');
  return response.json();
}
