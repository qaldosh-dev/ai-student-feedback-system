import EmptyState from '../components/EmptyState';
import LoadingState from '../components/LoadingState';
import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { AccountSessionExpired } from '../api/auth';
import { AnalyticsLoadError, analyticsQuery, getTeacherAnalytics } from '../api/teacherAnalytics';
import type { TeacherAnalyticsScope } from '../api/teacherAnalytics';
import SentimentAnalysis from '../components/SentimentAnalysis';
import TopicsAnalysis from '../components/TopicsAnalysis';
import SummaryAnalysis from '../components/SummaryAnalysis';
import AnalyticsPrivacyNotice from '../components/AnalyticsPrivacyNotice';

export default function TeacherAnalyticsPage() {
  const { currentUser } = useAuth();
  const [parameters, setParameters] = useSearchParams();
  const query = parameters.toString();
  return <Analytics key={`${currentUser?.id}:${currentUser?.linkedTeacher?.id}:${query}`}
    query={query} onSelection={setParameters} />;
}

function Analytics({ query, onSelection }: { query: string; onSelection: (query: string) => void }) {
  const { expireSession } = useAuth();
  const [data, setData] = useState<TeacherAnalyticsScope | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [privacyBlocked, setPrivacyBlocked] = useState(false);
  const protectPrivacy = useCallback(() => setPrivacyBlocked(true), []);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setData(null); setError('');
    getTeacherAnalytics(query, controller.signal)
      .then(result => { if (!controller.signal.aborted) setData(result); })
      .catch(failure => {
        if (controller.signal.aborted) return;
        if (failure instanceof AccountSessionExpired) expireSession();
        else setError(failure instanceof AnalyticsLoadError ? failure.message : 'Could not load teacher analytics. Please try again.');
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [query, attempt, expireSession]);
  return <main id="main" className="container role-area">
    <Link to="/teacher">Back to Teacher Area</Link>
    <h1>Teacher Analytics</h1>
    <TeacherAnalyticsContent data={data} loading={loading} error={error} onSelection={onSelection}
      onRetry={() => { setLoading(true); setData(null); setAttempt(value => value + 1); }} />
    <TeacherAnalysisSections data={data} loading={loading} error={error}
      privacyBlocked={privacyBlocked} onPrivacyProtected={protectPrivacy} />
  </main>;
}

export function TeacherAnalysisSections({ data, loading, error, privacyBlocked, onPrivacyProtected }: {
  data: TeacherAnalyticsScope | null; loading: boolean; error: string;
  privacyBlocked: boolean; onPrivacyProtected: () => void;
}) {
  if (loading || error || data?.selectedCourseId == null || data.selectedAcademicTerm == null) return null;
  // Fail closed if eligibility is missing. Each endpoint checks again against current published reviews.
  if (privacyBlocked || data.privacyThresholdMet !== true) return <AnalyticsPrivacyNotice />;
  return <>
    <SentimentAnalysis key={`${data.selectedCourseId}:${data.selectedAcademicTerm}`}
      courseId={data.selectedCourseId} term={data.selectedAcademicTerm} onPrivacyProtected={onPrivacyProtected} />
    <TopicsAnalysis key={`${data.selectedCourseId}:${data.selectedAcademicTerm}`}
      courseId={data.selectedCourseId} term={data.selectedAcademicTerm} onPrivacyProtected={onPrivacyProtected} />
    <SummaryAnalysis key={`${data.selectedCourseId}:${data.selectedAcademicTerm}`}
      courseId={data.selectedCourseId} term={data.selectedAcademicTerm} onPrivacyProtected={onPrivacyProtected} />
  </>;
}

export function TeacherAnalyticsContent({ data, loading, error, onSelection, onRetry }: {
  data: TeacherAnalyticsScope | null; loading: boolean; error: string;
  onSelection: (query: string) => void; onRetry: () => void;
}) {
  if (loading) return <LoadingState label="Loading teacher analytics…" />;
  if (error) return <div className="state-message error-message" role="alert">
    <p>{error}</p><button type="button" onClick={onRetry}>Retry</button>
    <button type="button" onClick={() => onSelection('')}>Reset selection</button>
  </div>;
  if (!data || data.courses.length === 0) return <EmptyState title="No courses available for analytics." description="Analytics will appear when eligible courses and feedback are available." />;
  const course = data.courses.find(option => option.courseId === data.selectedCourseId);
  return <>
    <div className="section-search">
      <label htmlFor="analytics-course">Course</label>
      <select id="analytics-course" value={data.selectedCourseId ?? ''} onChange={event => onSelection(analyticsQuery(event.target.value))}>
        {data.courses.map(option => <option key={option.courseId} value={option.courseId}>{option.courseCode} — {option.courseName}</option>)}
      </select>
    </div>
    <div className="section-search">
      <label htmlFor="analytics-term">Academic Term</label>
      <select id="analytics-term" value={data.selectedAcademicTerm ?? ''}
        onChange={event => onSelection(analyticsQuery(String(data.selectedCourseId), event.target.value))}>
        {course?.academicTerms.map(term => <option key={term} value={term}>{term}</option>)}
      </select>
    </div>
    <p>Selected: {course?.courseCode} — {course?.courseName} · {data.selectedAcademicTerm}</p>
    <p>Terms are shown exactly as recorded, in text order; the first option does not mean the current term.</p>
    {data.privacyThresholdMet && <p className="state-message" role="status">Published feedback is available for this selection.</p>}
  </>;
}
