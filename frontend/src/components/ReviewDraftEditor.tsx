import { useToast } from './ToastProvider';
import { useEffect, useRef, useState } from 'react';
import type { ReviewEligibility } from '../api/scheduleUploads';
import { FEEDBACK_MAX_LENGTH, loadDraft, saveDraft, validDraft } from '../pages/reviewDraft';
import type { ReviewDraft } from '../pages/reviewDraft';
import RatingInput from './RatingInput';
import { completeDraft, createReviewSubmission } from '../pages/reviewSubmission';

// Mounted only after the current server eligibility response authorizes this target.
export default function ReviewDraftEditor({ target, urlRating, onSubmitted }: { target: ReviewEligibility; urlRating: number | null; onSubmitted?: (reviewId: number) => void }) {
  const notify = useToast();
  const [draft, setDraft] = useState(() => loadDraft(target, urlRating));
  const [storageFailed, setStorageFailed] = useState(false);
  const [submission] = useState(() => createReviewSubmission(target));
  const [pending, setPending] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState('');
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => { if (!submitted) setStorageFailed(!saveDraft(target, draft)); }, [target, draft, submitted]);

  function update(next: ReviewDraft) {
    if (submission.pending || submitted) return;
    const checked = validDraft(next);
    setDraft(checked);
    // Save immediately so switching targets cannot lose the last keystroke.
    setStorageFailed(!saveDraft(target, checked));
  }

  async function submit() {
    if (submission.pending || submitted) return;
    setPending(true); setError('');
    try {
      const result = await submission.submit(draft);
      if (result && mounted.current) {
        setSubmitted(true); setStorageFailed(!result.draftCleared);
        notify('reviewSubmitted');
        onSubmitted?.(result.reviewId);
      }
    } catch (failure) {
      if (mounted.current) notify('reviewError');
      if (mounted.current) setError(failure instanceof Error && failure.name !== 'TimeoutError' && failure.name !== 'TypeError'
        ? failure.message : 'Submission could not be confirmed. Your draft has been kept; check your connection before retrying.');
    } finally { if (mounted.current) setPending(false); }
  }

  if (submitted) return <ReviewSubmissionSuccess storageFailed={storageFailed} />;

  return <>
    <fieldset disabled={pending} className="review-draft-fields"><ReviewDraftFields draft={draft} onChange={update} /></fieldset>
    <p>Your identity is kept internally and is not included in review responses. Avoid identifying yourself in your feedback text.</p>
    <ReviewSubmitButton draft={draft} pending={pending} onSubmit={submit} />
    {error && <p role="alert">{error}</p>}
    {storageFailed && <p role="status">Draft storage is unavailable. Your changes may be lost when you leave or refresh this page.</p>}
  </>;
}

export function ReviewSubmissionSuccess({ storageFailed }: { storageFailed: boolean }) {
  return <div role="status"><p>Your review was submitted successfully.</p>
    {storageFailed && <p>The local draft could not be cleared because browser storage is unavailable.</p>}</div>;
}

export function ReviewSubmitButton({ draft, pending, onSubmit }: { draft: ReviewDraft; pending: boolean; onSubmit: () => void }) {
  return <button type="button" className="account-button" disabled={pending || !completeDraft(draft)} onClick={onSubmit}>
    {pending ? 'Submitting…' : 'Submit Review'}
  </button>;
}

export function ReviewDraftFields({ draft, onChange }: { draft: ReviewDraft; onChange: (draft: ReviewDraft) => void }) {
  return <>
    <RatingInput value={draft.rating} onChange={rating => onChange({ ...draft, rating })} />
    <div className="feedback-input">
      <label htmlFor="review-feedback">Your feedback</label>
      <textarea id="review-feedback" rows={5} maxLength={FEEDBACK_MAX_LENGTH} value={draft.text}
        aria-describedby="feedback-count feedback-hint"
        onChange={event => onChange({ ...draft, text: event.target.value.slice(0, FEEDBACK_MAX_LENGTH) })} />
      <p id="feedback-count">{draft.text.length} / {FEEDBACK_MAX_LENGTH}</p>
      <p id="feedback-hint">{draft.text.trim() ? 'Draft kept in this browser tab. Nothing has been submitted.' : 'Feedback is required before submission. You can switch courses while drafting.'}</p>
    </div>
  </>;
}
