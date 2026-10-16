import LoadingState from './LoadingState';
import StatusBadge from './StatusBadge';
import { useEffect, useState } from 'react';
import AnalyticsPrivacyNotice from './AnalyticsPrivacyNotice';
import { useAuth } from '../auth/AuthContext';
import { AccountSessionExpired } from '../api/auth';
import { getTeacherSummary } from '../api/teacherSummary';
import type { TeacherSummary } from '../api/teacherSummary';

export default function SummaryAnalysis({ courseId, term, onPrivacyProtected }: { courseId: number; term: string; onPrivacyProtected?: () => void }) {
  const { expireSession } = useAuth();
  const [data, setData] = useState<TeacherSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setData(null);
    getTeacherSummary(courseId, term, controller.signal)
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
  return <SummaryContent data={data} loading={loading}
    onRetry={() => { setLoading(true); setData(null); setAttempt(value => value + 1); }} />;
}

export function SummaryContent({ data, loading, onRetry }: {
  data: TeacherSummary | null; loading: boolean; onRetry: () => void;
}) {
  if (!loading && data?.privacyThresholdMet === false) return <AnalyticsPrivacyNotice />;
  return <section aria-labelledby="summary-heading">
    <h2 id="summary-heading">AI Summary</h2>
    {loading ? <LoadingState label="Generating AI summary..." />
      : data?.status === 'AVAILABLE' && data.analysisAvailable && data.enoughFeedback && data.summary
        ? <div className="state-message"><StatusBadge status={data.status} /><p>{data.summary}</p></div>
      : <div className="state-message" role="status">
        {data?.status === 'UNAVAILABLE' && <StatusBadge status={data.status} />}
        <p>AI summary is temporarily unavailable.</p>
        <button type="button" onClick={onRetry}>Retry</button>
      </div>}
  </section>;
}

