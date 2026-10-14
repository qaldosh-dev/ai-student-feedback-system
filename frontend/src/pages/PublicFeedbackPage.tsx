import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import { getPublicFeedback, publicFeedbackUrl } from '../api/publicFeedback';
import type { PublicFeedback } from '../api/publicFeedback';

export default function PublicFeedbackPage() {
  const { teacherId = '', courseId = '' } = useParams();
  const [query] = useSearchParams();
  const term = query.get('term');
  const offeringId = query.get('offering');
  return <Feedback key={JSON.stringify([teacherId, courseId, term, offeringId])}
    teacherId={teacherId} courseId={courseId} term={term} offeringId={offeringId} />;
}

function Feedback({ teacherId, courseId, term, offeringId }: {
  teacherId: string; courseId: string; term: string | null; offeringId: string | null;
}) {
  const [data, setData] = useState<PublicFeedback | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setData(null); setError('');
    getPublicFeedback(teacherId, courseId, term, offeringId, controller.signal)
      .then(result => { if (!controller.signal.aborted) setData(result); })
      .catch(() => { if (!controller.signal.aborted) setError('Could not load published feedback. Please try again.'); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [teacherId, courseId, term, offeringId, attempt]);
  return <main id="main" className="container role-area">
    <Link to="/">Back to Browse</Link>
    <h1>Published Feedback</h1>
    <PublicFeedbackContent data={data} loading={loading} error={error}
      onRetry={() => { setLoading(true); setData(null); setAttempt(value => value + 1); }} />
  </main>;
}

export function PublicFeedbackContent({ data, loading, error, onRetry }: {
  data: PublicFeedback | null; loading: boolean; error: string; onRetry: () => void;
}) {
  if (loading) return <p role="status">Loading published feedback…</p>;
  if (error) return <div className="state-message error-message" role="alert"><p>{error}</p><button onClick={onRetry}>Retry</button></div>;
  if (!data) return <p className="state-message">Feedback selection not found.</p>;
  return <>
    <h2><Link to={`/teachers/${data.teacherId}`}>{data.teacherName}</Link></h2>
    <h2><Link to={`/courses/${data.courseId}`}>{data.courseCode} — {data.courseName}</Link></h2>
    <p><Link to={`/teachers/${data.teacherId}/courses/${data.courseId}/ratings/history`}>Previous Ratings</Link></p>
    <nav aria-label="Academic term selection">
      <p>Choose an academic term:</p>
      <ul>
        {data.availableTargets.map(target => {
          const hasSeveralOfferings = data.availableTargets.filter(item => item.academicTerm === target.academicTerm).length > 1;
          return <li key={JSON.stringify([target.courseOfferingId, target.academicTerm])}>
            <Link to={publicFeedbackUrl(data.teacherId, data.courseId, target.academicTerm, target.courseOfferingId)}
              aria-current={target.courseOfferingId === data.courseOfferingId && target.academicTerm === data.academicTerm ? 'page' : undefined}>
              {target.academicTerm}{hasSeveralOfferings ? ` — Course offering ${target.courseOfferingId}` : ''}
            </Link>
          </li>;
        })}
      </ul>
    </nav>
    {data.courseOfferingId === null ? <p className="state-message">Choose a term and, if listed separately, a course offering to read its feedback.</p> : <>
      <p><strong>Academic Term:</strong> {data.academicTerm}</p>
      {data.feedback.length === 0 ? <p className="state-message">No published feedback yet for this course and term.</p> :
        <ul className="schedule-list">
          {data.feedback.map((feedback, index) => <li key={index} className="course-card">
            <p><strong>Rating:</strong> {feedback.rating} / 5</p>
            <p className="review-text">{feedback.text}</p>
            <p><strong>Published:</strong> {new Date(feedback.publishedAt).toLocaleString()}</p>
          </li>)}
        </ul>}
    </>}
  </>;
}
