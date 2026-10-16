import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { AccountSessionExpired } from '../api/auth';
import { listReviewEligibilities } from '../api/scheduleUploads';
import type { ReviewEligibility } from '../api/scheduleUploads';
import { useAuth } from '../auth/AuthContext';
import ReviewDraftEditor from '../components/ReviewDraftEditor';
import StudentBackLink from '../components/StudentBackLink';
import { getOwnReviews } from '../api/reviews';
import type { OwnReview } from '../api/reviews';
import { existingReview, reviewAction } from './reviewAction';
import { reviewTargetQuery } from './reviewTargetQuery';
export { reviewTargetQuery } from './reviewTargetQuery';

export function readReviewRating(search: string): number | null {
  const values = new URLSearchParams(search).getAll('rating');
  return values.length === 1 && /^[1-5]$/.test(values[0]) ? Number(values[0]) : null;
}

export function resolveReviewTarget(rows: ReviewEligibility[], search: string) {
  const query = new URLSearchParams(search);
  // Optional draft input cannot invalidate an otherwise eligible target.
  query.delete('rating');
  if (query.size === 0) return { kind: 'none' } as const;
  const id = query.get('courseOfferingId');
  const term = query.get('academicTerm');
  // The URL expresses intent only. Accept exactly one complete identity, never teacher/course IDs alone.
  if (query.size !== 2 || !id || !/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(Number(id))
      || !term?.trim() || query.getAll('courseOfferingId').length !== 1 || query.getAll('academicTerm').length !== 1)
    return { kind: 'unavailable' } as const;
  const matches = rows.filter(row => row.courseOfferingId === Number(id) && row.academicTerm === term);
  return matches.length === 1 ? { kind: 'selected', target: matches[0] } as const : { kind: 'unavailable' } as const;
}

export default function ReviewTargetPage() {
  const { currentUser } = useAuth();
  const [query, setQuery] = useSearchParams();
  const navigate = useNavigate();
  const search = query.toString();
  const identity = new URLSearchParams(query);
  identity.delete('rating');
  // Recheck changed targets/accounts, but retain keyboard focus when only a star changes.
  return <ReviewTargets key={`${currentUser?.id}:${identity.toString()}`} search={search}
    onSelect={target => setQuery(reviewTargetQuery(target))}
    onSubmitted={reviewId => navigate(`/student/reviews/${reviewId}/status`, { replace: true })} />;
}

function ReviewTargets({ search, onSelect, onSubmitted }: SelectionProps) {
  const { expireSession } = useAuth();
  const [rows, setRows] = useState<ReviewEligibility[]>([]);
  const [reviews, setReviews] = useState<OwnReview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(''); setRows([]); setReviews([]);
    Promise.all([listReviewEligibilities(controller.signal), getOwnReviews(controller.signal)])
      .then(([targets, history]) => {
        if (controller.signal.aborted) return;
        // Fail closed if an older backend lacks the stable target identity.
        if (history.some(review => !Number.isSafeInteger(review.courseOfferingId) || review.courseOfferingId <= 0))
          throw new Error('Review identity unavailable.');
        setRows(targets); setReviews(history);
      })
      .catch(failure => {
        if (controller.signal.aborted) return;
        if (failure instanceof AccountSessionExpired) { setError('Sign in again to load your review targets.'); expireSession(); }
        else setError('Could not load courses available for review. Please try again.');
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [attempt, expireSession]);

  return <main id="main" className="container role-area student-page">
    <StudentBackLink />
    <h1>Review a Course</h1>
    <p>Choose the course, teacher and academic term for your feedback.</p>
    {loading ? <p role="status">Loading available courses…</p> : error ? <div className="state-message error-message" role="alert">
      <p>{error}</p><button type="button" onClick={() => setAttempt(value => value + 1)}>Retry</button>
    </div> : <ReviewTargetSelection rows={rows} reviews={reviews} search={search} onSelect={onSelect} onSubmitted={onSubmitted} />}
  </main>;
}

interface SelectionProps {
  search: string;
  onSelect: (target: ReviewEligibility) => void;
  onSubmitted?: (reviewId: number) => void;
}

function TargetDetails({ target }: { target: ReviewEligibility }) {
  return <>
    <p><strong>Course:</strong> {target.courseCode} — {target.courseName}</p>
    <p><strong>Teacher:</strong> {target.teacherName}</p>
    <p><strong>Academic Term:</strong> {target.academicTerm}</p>
    <p><strong>Group / Section:</strong> {target.groupSections.join(', ')}</p>
  </>;
}

export function ReviewTargetSelection({ rows, reviews, search, onSelect, onSubmitted }: SelectionProps & { rows: ReviewEligibility[]; reviews: OwnReview[] }) {
  const selection = resolveReviewTarget(rows, search);
  const selectedReview = selection.kind === 'selected' ? existingReview(reviews, selection.target) : undefined;
  // US20 supplies unique targets in deterministic order; no catalog matching/grouping happens here.
  return <>
    {selection.kind === 'unavailable' && <p className="state-message error-message" role="alert">
      This course is not available for review. Choose an available course below.
    </p>}
    {selection.kind === 'selected' && <section className="state-message area-details" aria-labelledby="selected-target-title">
      <h2 id="selected-target-title">Selected review target</h2>
      <TargetDetails target={selection.target} />
      {selectedReview ? <ExistingReviewAction review={selectedReview} />
        : <ReviewDraftEditor key={reviewTargetQuery(selection.target).toString()} target={selection.target} urlRating={readReviewRating(search)} onSubmitted={onSubmitted} />}
    </section>}
    {rows.length === 0 ? <div className="state-message">
      <p>No verified courses are currently available for review.</p>
      <p>Upload and verify your Detailed Schedule to make eligible courses available.</p>
    </div> : <>
      <p>{rows.length} available review targets</p>
      <ul className="schedule-list">
        {rows.map(target => {
          const selected = selection.kind === 'selected' && selection.target === target;
          const review = existingReview(reviews, target);
          return <li key={reviewTargetQuery(target).toString()} className={`course-card schedule-card${selected ? ' selected-review-target' : ''}`}>
            <TargetDetails target={target} />
            {review ? <ExistingReviewAction review={review} /> : <>
              <p><span className="student-badge">Not reviewed</span></p>
              <button type="button" className="account-button" aria-pressed={selected}
                aria-label={`Review ${target.courseCode}, ${target.teacherName}, ${target.academicTerm}`}
                onClick={() => { if (!selected) onSelect(target); }}>Review</button>
            </>}
          </li>;
        })}
      </ul>
    </>}
  </>;
}

function ExistingReviewAction({ review }: { review: OwnReview }) {
  const action = reviewAction(review);
  return <div className="student-card-actions">
    <span className={`student-badge ${review.status.toLowerCase()}`}>{action.status}</span>
    <Link className="student-link-button" to={action.href}>{action.label}</Link>
  </div>;
}
