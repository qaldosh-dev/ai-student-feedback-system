import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { AccountSessionExpired } from '../api/auth';
import { listVerifiedCourses } from '../api/scheduleUploads';
import type { VerifiedCourse } from '../api/scheduleUploads';
import { useAuth } from '../auth/AuthContext';
import { groupVerifiedCourses } from './verifiedCourseGroups';

export default function VerifiedCoursesPage() {
  const { currentUser } = useAuth();
  return <VerifiedCourses key={currentUser?.id} />;
}

function VerifiedCourses() {
  const { expireSession } = useAuth();
  const [rows, setRows] = useState<VerifiedCourse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(''); setRows([]);
    listVerifiedCourses(controller.signal)
      .then(result => { if (!controller.signal.aborted) setRows(result); })
      .catch(failure => {
        if (controller.signal.aborted) return;
        if (failure instanceof AccountSessionExpired) expireSession();
        else setError(failure instanceof Error ? failure.message : 'Could not load verified courses.');
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [attempt, expireSession]);

  const courses = groupVerifiedCourses(rows);

  return <main id="main" className="container role-area">
    <Link to="/student">Back to Student Area</Link>
    <h1>Verified Courses</h1>
    <p>These schedule entries match a course and teacher relationship in the catalog.
      Academic term and group come from your schedule; the catalog does not independently confirm enrollment.</p>
    {loading ? <p role="status">Loading verified courses…</p> : error ? <div className="state-message error-message" role="alert">
      <p>{error}</p><button onClick={() => setAttempt(value => value + 1)}>Retry</button>
    </div> : courses.length === 0 ? <p className="state-message">No verified courses are available yet.</p> : <>
      <p>{courses.length} verified courses</p>
      <ul className="schedule-list">
        {courses.map(({ key, course: row, uploadIds, groupSections }) => <li key={key} className="course-card schedule-card">
          <h2>{row.courseCode} — {row.courseName}</h2>
          <p><strong>Teacher:</strong> {row.teacherName}</p>
          <p><strong>Group / Section:</strong> {groupSections.join(', ')}</p>
          <p><strong>Academic Term:</strong> {row.academicTerm}</p>
          <p>Verified from {uploadIds.length} schedule {uploadIds.length === 1 ? 'upload' : 'uploads'}</p>
        </li>)}
      </ul>
    </>}
  </main>;
}
