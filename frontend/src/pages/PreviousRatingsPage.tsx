import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { getPreviousRatings } from '../api/previousRatings';
import type { PreviousRatings } from '../api/previousRatings';

export default function PreviousRatingsPage() {
  const { teacherId = '', courseId = '' } = useParams();
  return <History key={JSON.stringify([teacherId, courseId])} teacherId={teacherId} courseId={courseId} />;
}

function History({ teacherId, courseId }: { teacherId: string; courseId: string }) {
  const [data, setData] = useState<PreviousRatings | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setData(null); setError('');
    getPreviousRatings(teacherId, courseId, controller.signal)
      .then(result => { if (!controller.signal.aborted) setData(result); })
      .catch(() => { if (!controller.signal.aborted) setError('Could not load previous ratings. Please try again.'); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [teacherId, courseId, attempt]);
  return <main id="main" className="container role-area">
    <Link to="/">Back to Browse</Link>
    <h1>Previous Ratings</h1>
    <PreviousRatingsContent data={data} loading={loading} error={error}
      onRetry={() => { setLoading(true); setData(null); setAttempt(value => value + 1); }} />
  </main>;
}

export function PreviousRatingsContent({ data, loading, error, onRetry }: {
  data: PreviousRatings | null; loading: boolean; error: string; onRetry: () => void;
}) {
  if (loading) return <p role="status">Loading previous ratings…</p>;
  if (error) return <div className="state-message error-message" role="alert"><p>{error}</p><button onClick={onRetry}>Retry</button></div>;
  if (!data) return <p className="state-message">Rating history not found.</p>;
  const separateOfferings = new Set(data.ratings.map(row => row.courseOfferingId)).size > 1;
  return <>
    <h2><Link to={`/teachers/${data.teacherId}`}>{data.teacherName}</Link></h2>
    <h2><Link to={`/courses/${data.courseId}`}>{data.courseCode} — {data.courseName}</Link></h2>
    <p><Link to={`/teachers/${data.teacherId}/courses/${data.courseId}/feedback`}>View Feedback</Link></p>
    {data.ratings.length === 0 ? <p className="state-message">No published ratings yet.</p> : <>
      <p>Terms are shown as recorded, in text order. Separate course offerings are not combined.</p>
      <ul className="schedule-list">
        {data.ratings.map(row => <li key={JSON.stringify([row.courseOfferingId, row.academicTerm])} className="course-card schedule-card">
          <h3>{row.academicTerm}</h3>
          {separateOfferings && <p>Course offering {row.courseOfferingId}</p>}
          <p><strong>Average rating:</strong> {row.averageRating.toFixed(1)} / 5</p>
          <p>Based on {row.publishedReviewCount} published {row.publishedReviewCount === 1 ? 'review' : 'reviews'}</p>
        </li>)}
      </ul>
    </>}
  </>;
}
