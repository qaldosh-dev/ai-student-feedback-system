import type { ReviewEligibility } from '../api/scheduleUploads';

export const FEEDBACK_MAX_LENGTH = 2000;
const PREFIX = 'review-draft:';
export interface ReviewDraft { rating: number | null; text: string }
type DraftTarget = Pick<ReviewEligibility, 'courseOfferingId' | 'academicTerm'>;

export function draftKey(target: DraftTarget) {
  return PREFIX + JSON.stringify([target.courseOfferingId, target.academicTerm]);
}

export function validDraft(value: unknown): ReviewDraft {
  const input = value && typeof value === 'object' ? value as Partial<ReviewDraft> : {};
  return {
    rating: typeof input.rating === 'number' && Number.isInteger(input.rating) && input.rating >= 1 && input.rating <= 5 ? input.rating : null,
    text: typeof input.text === 'string' && input.text.length <= FEEDBACK_MAX_LENGTH ? input.text : '',
  };
}

export function loadDraft(target: DraftTarget, urlRating: number | null): ReviewDraft {
  let stored: unknown;
  try { stored = JSON.parse(sessionStorage.getItem(draftKey(target)) ?? 'null'); } catch { /* Missing/unavailable storage or malformed JSON: start safely. */ }
  const draft = validDraft(stored);
  return { ...draft, rating: draft.rating ?? validDraft({ rating: urlRating }).rating };
}

export function saveDraft(target: DraftTarget, draft: ReviewDraft): boolean {
  try {
    sessionStorage.setItem(draftKey(target), JSON.stringify(validDraft(draft)));
    return true;
  } catch { return false; }
}

export function clearDraft(target: DraftTarget): boolean {
  try { sessionStorage.removeItem(draftKey(target)); return true; } catch { return false; }
}

// Authentication lifecycle cleanup; no account identifiers or credentials are stored.
export function clearReviewDrafts() {
  try {
    for (let index = sessionStorage.length - 1; index >= 0; index--) {
      const key = sessionStorage.key(index);
      if (key?.startsWith(PREFIX)) sessionStorage.removeItem(key);
    }
  } catch { /* Storage may be disabled. */ }
}
