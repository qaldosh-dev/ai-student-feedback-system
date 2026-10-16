import LoadingState from './LoadingState';
import StatusBadge from './StatusBadge';
import { useEffect, useState } from 'react';
import AnalyticsPrivacyNotice from './AnalyticsPrivacyNotice';
import { useAuth } from '../auth/AuthContext';
import { AccountSessionExpired } from '../api/auth';
import { getTeacherSentiment } from '../api/teacherSentiment';
import type { TeacherSentiment } from '../api/teacherSentiment';

export default function SentimentAnalysis({ courseId, term, onPrivacyProtected }: { courseId: number; term: string; onPrivacyProtected?: () => void }) {
  const { expireSession } = useAuth();
  const [data, setData] = useState<TeacherSentiment | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setData(null);
    getTeacherSentiment(courseId, term, controller.signal)
      .then(result => {
        if (controller.signal.aborted) return;
        setData(result);
        if (!result.privacyThresholdMet) onPrivacyProtected?.();
      })
      .catch(error => {
        if (!controller.signal.aborted && error instanceof AccountSessionExpired) expireSession();
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [courseId, term, attempt, expireSession, onPrivacyProtected]);
  return <SentimentContent data={data} loading={loading}
    onRetry={() => { setLoading(true); setData(null); setAttempt(value => value + 1); }} />;
}

const labels = { MOSTLY_POSITIVE: 'Mostly Positive', MOSTLY_NEUTRAL: 'Mostly Neutral', MOSTLY_NEGATIVE: 'Mostly Negative', MIXED: 'Mixed' };

export function SentimentContent({ data, loading, onRetry }: {
  data: TeacherSentiment | null; loading: boolean; onRetry: () => void;
}) {
  if (!loading && data?.privacyThresholdMet === false) return <AnalyticsPrivacyNotice />;
  return <section aria-labelledby="sentiment-heading">
    <h2 id="sentiment-heading">Sentiment Analysis</h2>
    {loading ? <LoadingState label="Analyzing published feedback…" />
      : data?.status === 'AVAILABLE' && data.analysisAvailable && data.overallSentiment ? <div className="state-message">
        <StatusBadge status={data.status} />
        <p><strong>Overall sentiment:</strong> {labels[data.overallSentiment]}</p>
        <p>Positive: {data.positivePercentage}%</p>
        <p>Neutral: {data.neutralPercentage}%</p>
        <p>Negative: {data.negativePercentage}%</p>
        <p>Based on {data.analyzedReviewCount} published reviews</p>
      </div> : <div className="state-message" role="status">
        {data?.status === 'UNAVAILABLE' && <StatusBadge status={data.status} />}
        <p>Sentiment analysis is temporarily unavailable.</p>
        <button type="button" onClick={onRetry}>Retry</button>
      </div>}
  </section>;
}

