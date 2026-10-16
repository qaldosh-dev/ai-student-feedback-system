import EmptyState from '../components/EmptyState';
import LoadingState from '../components/LoadingState';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { AccountSessionExpired } from '../api/auth';
import { getTeacherFeedback } from '../api/teacherFeedback';
import type { TeacherFeedbackGroup } from '../api/teacherFeedback';
import { useAuth } from '../auth/AuthContext';

export default function TeacherFeedbackPage() {
  const { currentUser } = useAuth();
  return <TeacherFeedback key={`${currentUser?.id}:${currentUser?.linkedTeacher?.id}`} />;
}

function TeacherFeedback() {
  const { expireSession } = useAuth();
  const [groups, setGroups] = useState<TeacherFeedbackGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(''); setGroups([]);
    getTeacherFeedback(controller.signal)
      .then(result => { if (!controller.signal.aborted) setGroups(result); })
      .catch(failure => {
        if (controller.signal.aborted) return;
        if (failure instanceof AccountSessionExpired) expireSession();
        else setError(failure instanceof Error && failure.name !== 'TypeError' && failure.name !== 'TimeoutError'
          ? failure.message : 'Could not load course feedback. Please try again.');
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [attempt, expireSession]);

  return <main id="main" className="container role-area">
    <Link to="/teacher">Back to Teacher Area</Link>
    <h1>Course Feedback</h1>
    <TeacherFeedbackContent groups={groups} loading={loading} error={error}
      onRefresh={() => { setLoading(true); setGroups([]); setAttempt(value => value + 1); }} />
  </main>;
}

export function TeacherFeedbackContent({ groups, loading, error, onRefresh }: {
  groups: TeacherFeedbackGroup[]; loading: boolean; error: string; onRefresh: () => void;
}) {
  if (loading) return <LoadingState label="Loading course feedback…" />;
  if (error) return <div className="state-message error-message" role="alert">
    <p>{error}</p><button type="button" onClick={onRefresh}>Retry</button>
  </div>;
  return <>
    <button type="button" onClick={onRefresh}>Refresh feedback</button>
    {groups.length === 0 ? <EmptyState title="No published feedback yet." description="Published student feedback will appear here." /> :
      <ul className="schedule-list">
        {groups.map(group => <li key={JSON.stringify([group.courseOfferingId, group.academicTerm])} className="state-message schedule-card">
          <h2>{group.courseCode} — {group.courseName}</h2>
          <p><strong>Academic Term:</strong> {group.academicTerm}</p>
          <h3>Published Feedback</h3>
          <ul className="schedule-list">
            {group.feedback.map((feedback, index) => <li key={index} className="course-card">
              <p><strong>Rating:</strong> {feedback.rating} / 5</p>
              <p className="review-text">{feedback.text}</p>
              <p><strong>Published:</strong> {new Date(feedback.publishedAt).toLocaleString()}</p>
            </li>)}
          </ul>
        </li>)}
      </ul>}
  </>;
}
