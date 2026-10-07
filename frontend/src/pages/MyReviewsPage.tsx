import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { AccountSessionExpired } from '../api/auth';
import { getOwnReviews } from '../api/reviews';
import type { OwnReview } from '../api/reviews';
import { useAuth } from '../auth/AuthContext';
import ReviewModerationStatus from '../components/ReviewModerationStatus';
import PublishedReviewEditor from '../components/PublishedReviewEditor';

export default function MyReviewsPage() {
  const { currentUser } = useAuth();
  return <MyReviews key={currentUser?.id} />;
}

function MyReviews() {
  const { expireSession } = useAuth();
  const [reviews, setReviews] = useState<OwnReview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [editing, setEditing] = useState<number | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError('');
    getOwnReviews(controller.signal)
      .then(result => { if (!controller.signal.aborted) setReviews(result); })
      .catch(failure => {
        if (controller.signal.aborted) return;
        if (failure instanceof AccountSessionExpired) expireSession();
        else setError('Could not load your reviews. Please try again.');
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [attempt, expireSession]);

  function refresh() { setEditing(null); setLoading(true); setAttempt(value => value + 1); }
  return <main id="main" className="container role-area">
    <Link to="/student">Back to Student Area</Link>
    <h1>My Reviews</h1>
    <MyReviewsContent reviews={reviews} loading={loading} error={error} editing={editing}
      onEdit={setEditing} onCancel={() => setEditing(null)} onRefresh={refresh} onSessionExpired={expireSession} />
  </main>;
}

export function MyReviewsContent({ reviews, loading, error, editing, onEdit, onCancel, onRefresh, onSessionExpired }: {
  reviews: OwnReview[]; loading: boolean; error: string; editing: number | null;
  onEdit: (id: number) => void; onCancel: () => void; onRefresh: () => void; onSessionExpired: () => void;
}) {
  return <>
    {loading && <p role="status">Loading your reviews…</p>}
    {error && <p role="alert">{error} Previously loaded reviews may be out of date.</p>}
    <button type="button" disabled={loading || editing !== null} onClick={onRefresh}>Refresh reviews</button>
    {!loading && !error && reviews.length === 0 && <p className="state-message">You haven't submitted any reviews yet. <Link to="/student/verified-courses">View Verified Courses</Link></p>}
    <ul className="schedule-list">
      {reviews.map(review => <li key={review.reviewId} className="state-message schedule-card">
        <h2>{review.courseCode} — {review.courseName}</h2>
        <p><strong>Teacher:</strong> {review.teacherName}</p>
        <p><strong>Academic Term:</strong> {review.academicTerm}</p>
        <p><strong>Rating:</strong> {review.rating} / 5</p>
        <p className="review-text">{review.text}</p>
        <p><strong>Submitted:</strong> {new Date(review.submittedAt).toLocaleString()}</p>
        <ReviewModerationStatus status={review.status} moderationReasons={review.moderationReasons} />
        {!loading && !error && review.status === 'PUBLISHED' && (editing === review.reviewId
          ? <PublishedReviewEditor key={review.reviewId} reviewId={review.reviewId} initialRating={review.rating} initialText={review.text}
              onSaved={onRefresh} onCancel={onCancel} onSessionExpired={onSessionExpired} />
          : <button type="button" className="account-button" disabled={editing !== null} onClick={() => onEdit(review.reviewId)}>Edit Review</button>)}
        {!loading && !error && review.status === 'NEEDS_CORRECTION' && <Link to={`/student/reviews/${review.reviewId}/status`}>Fix Feedback</Link>}
        {review.status !== 'NEEDS_CORRECTION' && <Link to={`/student/reviews/${review.reviewId}/status`}>View Status</Link>}
      </li>)}
    </ul>
  </>;
}
