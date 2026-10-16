import { useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { AccountSessionExpired } from '../api/auth';
import { getReviewStatus } from '../api/reviews';
import type { ReviewStatusResponse } from '../api/reviews';
import { useAuth } from '../auth/AuthContext';
import ReviewCorrectionEditor from '../components/ReviewCorrectionEditor';
import ReviewModerationStatus from '../components/ReviewModerationStatus';
import ReviewModerationRetry from '../components/ReviewModerationRetry';
import StudentBackLink from '../components/StudentBackLink';

export default function ReviewStatusPage() {
  const { reviewId = '' } = useParams();
  const { currentUser } = useAuth();
  return <ReviewStatus key={`${currentUser?.id}:${reviewId}`} reviewId={reviewId} />;
}

function ReviewStatus({ reviewId }: { reviewId: string }) {
  const { expireSession } = useAuth();
  const [data, setData] = useState<ReviewStatusResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(''); setData(null);
    if (!/^[1-9]\d*$/.test(reviewId)) {
      setLoading(false);
      return () => controller.abort();
    }
    getReviewStatus(reviewId, controller.signal)
      .then(result => { if (!controller.signal.aborted) setData(result); })
      .catch(failure => {
        if (controller.signal.aborted) return;
        if (failure instanceof AccountSessionExpired) expireSession();
        else setError('Could not load review status. Please try again.');
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [reviewId, attempt, expireSession]);

  return <main id="main" className="container role-area student-page">
    <div className="student-card-actions"><StudentBackLink />
      <StudentBackLink to="/student/reviews">Back to My Reviews</StudentBackLink></div>
    <h1>Review Submission Status</h1>
    <ReviewStatusContent data={data} loading={loading} error={error} onSessionExpired={expireSession}
      onUpdated={setData}
      onRetry={() => { setLoading(true); setData(null); setAttempt(value => value + 1); }} />
  </main>;
}

export function ReviewStatusContent({ data, loading, error, onRetry, onSessionExpired, onUpdated }: {
  data: ReviewStatusResponse | null; loading: boolean; error: string; onRetry: () => void; onSessionExpired?: () => void;
  onUpdated?: (result: ReviewStatusResponse) => void;
}) {
  if (loading) return <p role="status">Loading review status…</p>;
  if (error) return <div className="state-message error-message" role="alert">
    <p>{error}</p><button type="button" onClick={onRetry}>Retry</button>
  </div>;
  if (!data) return <p className="state-message">Review not found or unavailable.</p>;
  return <section className="state-message area-details" aria-label="Review submission">
    <p>Your review was submitted successfully.</p>
    <h2>{data.courseCode} — {data.courseName}</h2>
    <p><strong>Teacher:</strong> {data.teacherName}</p>
    <p><strong>Academic Term:</strong> {data.academicTerm}</p>
    <p><strong>Rating:</strong> {data.rating} / 5</p>
    <p><strong>Submitted:</strong> {new Date(data.submittedAt).toLocaleString()}</p>
    <ReviewModerationStatus status={data.status} moderationReasons={data.moderationReasons} />
    {data.status === 'SUBMITTED' && <ReviewModerationRetry
      key={`${data.reviewId}:${data.retryAfterSeconds}:${data.retryModerationAvailable}`}
      review={data} onUpdated={onUpdated ?? onRetry} onRefresh={onRetry} onSessionExpired={onSessionExpired} />}
    {data.status === 'NEEDS_CORRECTION' && <>
      {typeof data.text === 'string' && <ReviewCorrectionEditor key={`${data.reviewId}:${data.moderatedAt}`}
        reviewId={data.reviewId} initialText={data.text} onSaved={onRetry} onSessionExpired={onSessionExpired} />}
    </>}
  </section>;
}
