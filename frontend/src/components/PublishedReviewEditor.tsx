import { useToast } from './ToastProvider';
import { useEffect, useRef, useState } from 'react';
import { AccountSessionExpired } from '../api/auth';
import { updatePublishedReview } from '../api/reviews';
import { FEEDBACK_MAX_LENGTH } from '../pages/reviewDraft';
import type { ReviewDraft } from '../pages/reviewDraft';
import { completeDraft } from '../pages/reviewSubmission';
import RatingInput from './RatingInput';

export default function PublishedReviewEditor({ reviewId, initialRating, initialText, onSaved, onCancel, onSessionExpired }: {
  reviewId: number; initialRating: number; initialText: string; onSaved: () => void; onCancel: () => void; onSessionExpired: () => void;
}) {
  const notify = useToast();
  const [draft, setDraft] = useState<ReviewDraft>({ rating: initialRating, text: initialText });
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);

  async function save() {
    if (inFlight.current || !completeDraft(draft) || draft.rating === null) return;
    inFlight.current = true; setPending(true); setError('');
    try {
      await updatePublishedReview(reviewId, draft.rating, draft.text);
      if (mounted.current) { notify('reviewUpdated'); onSaved(); }
    } catch (failure) {
      if (!mounted.current) return;
      notify('reviewError');
      if (failure instanceof AccountSessionExpired) onSessionExpired();
      else setError(failure instanceof Error && failure.name !== 'TypeError' && failure.name !== 'TimeoutError'
        ? failure.message : 'Update could not be confirmed. Refresh your reviews before trying again.');
    } finally {
      inFlight.current = false;
      if (mounted.current) setPending(false);
    }
  }

  return <>
    <PublishedReviewForm reviewId={reviewId} draft={draft} pending={pending} onChange={setDraft} onSubmit={save} onCancel={onCancel} />
    {error && <p role="alert">{error}</p>}
  </>;
}

export function PublishedReviewForm({ reviewId, draft, pending, onChange, onSubmit, onCancel }: {
  reviewId: number; draft: ReviewDraft; pending: boolean; onChange: (draft: ReviewDraft) => void; onSubmit: () => void; onCancel: () => void;
}) {
  const inputId = `published-feedback-${reviewId}`;
  return <form onSubmit={event => { event.preventDefault(); if (!pending && completeDraft(draft)) onSubmit(); }}>
    <fieldset disabled={pending} className="review-draft-fields">
      <RatingInput value={draft.rating} onChange={rating => onChange({ ...draft, rating })} />
      <div className="feedback-input">
        <label htmlFor={inputId}>Your feedback</label>
        <textarea id={inputId} rows={5} maxLength={FEEDBACK_MAX_LENGTH} value={draft.text}
          aria-describedby={`${inputId}-count`} onChange={event => onChange({ ...draft, text: event.target.value })} />
        <p id={`${inputId}-count`}>{draft.text.length} / {FEEDBACK_MAX_LENGTH}</p>
        <p>Your course, teacher and term stay the same. Updated feedback goes through moderation again.</p>
      </div>
      <div className="setup-actions">
        <button type="submit" className="account-button" disabled={pending || !completeDraft(draft)}>{pending ? 'Saving…' : 'Save Changes'}</button>
        <button type="button" disabled={pending} onClick={onCancel}>Cancel</button>
      </div>
    </fieldset>
  </form>;
}
