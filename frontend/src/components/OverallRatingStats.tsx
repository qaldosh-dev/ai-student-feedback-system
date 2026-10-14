import { useEffect, useState } from 'react';
import { getOverallRating } from '../api/overallRating';
import type { OverallRating } from '../api/overallRating';

export default function OverallRatingStats({ teacherId }: { teacherId: number }) {
  const [data, setData] = useState<OverallRating | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setData(null); setError('');
    getOverallRating(String(teacherId), controller.signal)
      .then(result => { if (!controller.signal.aborted) setData(result); })
      .catch(() => { if (!controller.signal.aborted) setError('Could not load overall rating. Please try again.'); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [teacherId, attempt]);
  return <section className="catalog-section" aria-labelledby="overall-rating-title">
    <h2 id="overall-rating-title">Overall Rating</h2>
    <OverallRatingContent data={data} loading={loading} error={error}
      onRetry={() => { setLoading(true); setData(null); setAttempt(value => value + 1); }} />
  </section>;
}

export function OverallRatingContent({ data, loading, error, onRetry }: {
  data: OverallRating | null; loading: boolean; error: string; onRetry: () => void;
}) {
  if (loading) return <p role="status">Loading overall rating…</p>;
  if (error) return <div className="state-message error-message" role="alert"><p>{error}</p><button type="button" onClick={onRetry}>Retry</button></div>;
  if (!data) return <p className="state-message">Teacher not found.</p>;
  if (data.publishedReviewCount === 0 || data.averageRating === null)
    return <><p>No ratings yet</p><p>0 published reviews</p></>;
  return <>
    <p><strong>{data.averageRating.toFixed(1)} / 5</strong></p>
    <p>Based on {data.publishedReviewCount} published {data.publishedReviewCount === 1 ? 'review' : 'reviews'}</p>
    <p>Across all courses and academic terms.</p>
    <h3>Rating distribution</h3>
    <ul>
      {[5, 4, 3, 2, 1].map(rating => <li key={rating}>{rating}★ — {data.ratingDistribution[String(rating)]}</li>)}
    </ul>
  </>;
}
