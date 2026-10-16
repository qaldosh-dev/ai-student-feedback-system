import { useEffect, useState } from 'react';
import LoadingState from './LoadingState';
import StatusBadge from './StatusBadge';
import AnalyticsPrivacyNotice from './AnalyticsPrivacyNotice';
import { useAuth } from '../auth/AuthContext';
import { AccountSessionExpired } from '../api/auth';
import { getTeacherTopics } from '../api/teacherTopics';
import type { TeacherTopics } from '../api/teacherTopics';

export default function TopicsAnalysis({ courseId, term, onPrivacyProtected }: { courseId: number; term: string; onPrivacyProtected?: () => void }) {
  const { expireSession } = useAuth();
  const [data, setData] = useState<TeacherTopics | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setData(null);
    getTeacherTopics(courseId, term, controller.signal)
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
  return <TopicsContent data={data} loading={loading}
    onRetry={() => { setLoading(true); setData(null); setAttempt(value => value + 1); }} />;
}

export function TopicsContent({ data, loading, onRetry }: {
  data: TeacherTopics | null; loading: boolean; onRetry: () => void;
}) {
  const available = data?.status === 'AVAILABLE' && data.analysisAvailable;
  if (!loading && data?.privacyThresholdMet === false) return <AnalyticsPrivacyNotice />;
  return <section aria-labelledby="topics-heading">
    <h2 id="topics-heading">Common Topics</h2>
    {loading ? <LoadingState label="Analyzing recurring themes…" />
      : available && !data.enoughRecurringFeedback
        ? <p className="state-message">Not enough recurring feedback to identify common topics yet.</p>
      : available ? <>
        <StatusBadge status={data.status} />
        {data.topics.length ? <ul>{data.topics.map(label => <li key={label}>{label}</li>)}</ul>
          : <p>No recurring topics identified.</p>}
      </> : <div className="state-message" role="status">
        {data?.status === 'UNAVAILABLE' && <StatusBadge status={data.status} />}
        <p>Topic analysis is temporarily unavailable.</p>
        <button type="button" onClick={onRetry}>Retry</button>
      </div>}
    <h2>Common Concerns</h2>
    {!loading && available && data.enoughRecurringFeedback && <>
      {data.concerns.length ? <ul>{data.concerns.map(label => <li key={label}>{label}</li>)}</ul>
        : <p>No recurring concerns identified.</p>}
    </>}
  </section>;
}

