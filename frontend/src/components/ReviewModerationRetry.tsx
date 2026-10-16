import { useToast } from './ToastProvider';
import { useEffect, useRef, useState } from 'react';
import { AccountSessionExpired } from '../api/auth';
import { ModerationRetryCooldown, retryReviewModeration } from '../api/reviews';
import type { ReviewStatusResponse } from '../api/reviews';

export default function ReviewModerationRetry({ review, onUpdated, onRefresh, onSessionExpired }: {
  review: ReviewStatusResponse; onUpdated: (result: ReviewStatusResponse) => void;
  onRefresh: () => void; onSessionExpired?: () => void;
}) {
  const notify = useToast();
  const [remaining, setRemaining] = useState(review.retryAfterSeconds);
  const [deadline, setDeadline] = useState(() => Date.now() + review.retryAfterSeconds * 1000);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const busy = useRef(false);
  const alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => {
    const update = () => setRemaining(Math.max(0, Math.ceil((deadline - Date.now()) / 1000)));
    update();
    const timer = window.setInterval(update, 1000);
    return () => window.clearInterval(timer);
  }, [deadline]);

  async function retry() {
    if (busy.current || remaining > 0) return;
    busy.current = true; setPending(true); setError('');
    try {
      const result = await retryReviewModeration(review.reviewId);
      if (alive.current) { notify('retryCompleted'); onUpdated(result); }
    } catch (failure) {
      if (!alive.current) return;
      if (failure instanceof AccountSessionExpired) onSessionExpired?.();
      else if (failure instanceof ModerationRetryCooldown) {
        setRemaining(failure.retryAfterSeconds); setDeadline(Date.now() + failure.retryAfterSeconds * 1000);
      } else {
        // No untrusted network/provider exception messages are displayed.
        notify('reviewError');
        setError('Moderation retry could not be confirmed. Refresh the status before trying again.');
      }
    } finally {
      if (alive.current) { busy.current = false; setPending(false); }
    }
  }

  return <ReviewRetryControls status={review.status} remaining={remaining} pending={pending} error={error}
    onRetry={() => void retry()} onRefresh={onRefresh} />;
}

export function ReviewRetryControls({ status, remaining, pending, error, onRetry, onRefresh }: {
  status: ReviewStatusResponse['status']; remaining: number; pending: boolean; error: string;
  onRetry: () => void; onRefresh: () => void;
}) {
  if (status !== 'SUBMITTED') return null;
  return <div>
    {remaining > 0 && <p role="status">Moderation is temporarily unavailable or was recently attempted. Please try again in {remaining} seconds.</p>}
    {error && <p role="alert">{error}</p>}
    <button type="button" disabled={pending || remaining > 0 || !!error} onClick={onRetry}>
      {pending ? 'Checking moderation...' : 'Retry Moderation'}
    </button>
    {error && <button type="button" onClick={onRefresh}>Refresh status</button>}
  </div>;
}
