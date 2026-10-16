import { useToast } from './ToastProvider';
import { useEffect, useRef, useState } from 'react';
import { AccountSessionExpired } from '../api/auth';
import { correctReview } from '../api/reviews';
import { FEEDBACK_MAX_LENGTH } from '../pages/reviewDraft';

export default function ReviewCorrectionEditor({ reviewId, initialText, onSaved, onSessionExpired }: {
  reviewId: number; initialText: string; onSaved: () => void; onSessionExpired?: () => void;
}) {
  const notify = useToast();
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(initialText);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);

  async function submit() {
    if (inFlight.current || !validCorrection(text)) return;
    inFlight.current = true; setPending(true); setError('');
    try {
      await correctReview(reviewId, text);
      if (mounted.current) { notify('correctionSubmitted'); onSaved(); } // Reload server status; never guess the new moderation outcome.
    } catch (failure) {
      if (!mounted.current) return;
      notify('reviewError');
      if (failure instanceof AccountSessionExpired) onSessionExpired?.();
      setError(failure instanceof Error && failure.name !== 'TypeError' && failure.name !== 'TimeoutError'
        ? failure.message : 'Correction could not be confirmed. Refresh the status before trying again.');
    } finally {
      inFlight.current = false;
      if (mounted.current) setPending(false);
    }
  }

  if (!editing) return <button type="button" className="account-button" onClick={() => setEditing(true)}>Edit Feedback</button>;
  return <>
    <ReviewCorrectionForm text={text} pending={pending} onTextChange={setText} onSubmit={submit} />
    {error && <div role="alert"><p>{error}</p><button type="button" disabled={pending} onClick={onSaved}>Refresh status</button></div>}
  </>;
}

export function validCorrection(text: string) {
  return text.trim().length > 0 && text.length <= FEEDBACK_MAX_LENGTH;
}

export function ReviewCorrectionForm({ text, pending, onTextChange, onSubmit }: {
  text: string; pending: boolean; onTextChange: (text: string) => void; onSubmit: () => void;
}) {
  return <form onSubmit={event => { event.preventDefault(); if (!pending && validCorrection(text)) onSubmit(); }}>
    <div className="feedback-input">
      <label htmlFor="corrected-feedback">Your feedback</label>
      <textarea id="corrected-feedback" rows={5} maxLength={FEEDBACK_MAX_LENGTH} value={text} disabled={pending}
        aria-describedby="correction-count correction-hint" onChange={event => onTextChange(event.target.value)} />
      <p id="correction-count">{text.length} / {FEEDBACK_MAX_LENGTH}</p>
      <p id="correction-hint">Only feedback text can be changed. Your rating and course remain the same.</p>
    </div>
    <button type="submit" className="account-button" disabled={pending || !validCorrection(text)}>
      {pending ? 'Submitting…' : 'Submit Corrected Feedback'}
    </button>
  </form>;
}
