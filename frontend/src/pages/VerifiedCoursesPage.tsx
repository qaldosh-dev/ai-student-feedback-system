import EmptyState from '../components/EmptyState';
import LoadingState from '../components/LoadingState';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { AccountSessionExpired } from '../api/auth';
import { listVerifiedCourses } from '../api/scheduleUploads';
import type { VerifiedCourse } from '../api/scheduleUploads';
import { useAuth } from '../auth/AuthContext';
import { groupVerifiedCourses } from './verifiedCourseGroups';
import { reviewTargetQuery } from './reviewTargetQuery';
import StudentBackLink from '../components/StudentBackLink';

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

  return <main id="main" className="container role-area student-page">
    <StudentBackLink />
    <h1>Verified Courses</h1>
    <p>These schedule entries match a course and teacher relationship in the catalog.
      Academic term and group come from your schedule; the catalog does not independently confirm enrollment.</p>
    {loading ? <LoadingState label="Loading verified courses…" /> : error ? <div className="state-message error-message" role="alert">
      <p>{error}</p><button onClick={() => setAttempt(value => value + 1)}>Retry</button>
    </div> : <VerifiedCourseCards rows={rows} />}
  </main>;
}

export function VerifiedCourseCards({ rows }: { rows: readonly VerifiedCourse[] }) {
  const courses = groupVerifiedCourses(rows);
  return courses.length === 0 ? <EmptyState title="No verified courses are available yet." description="Upload a schedule to verify your courses." action={{ to: '/student/schedules', label: 'Upload Schedule' }} /> : <>
      <p>{courses.length} verified courses</p>
      <ul className="schedule-list">
        {courses.map(({ key, course: row, uploadIds, groupSections }) => <li key={key} className="course-card schedule-card">
          <div className="catalog-card-main">
            <h2>{row.courseCode} — {row.courseName}</h2>
            <p><strong>Teacher:</strong> {row.teacherName}</p>
            <p><strong>Group / Section:</strong> {groupSections.join(', ')}</p>
            <p><strong>Academic Term:</strong> {row.academicTerm}</p>
            <p>Verified from {uploadIds.length} schedule {uploadIds.length === 1 ? 'upload' : 'uploads'}</p>
          </div>
          <Link className="leave-review-link" to={`/student/reviews/new?${reviewTargetQuery(row)}`}
            aria-label={`Leave review for ${row.courseCode} with ${row.teacherName}, ${row.academicTerm}`}>
            Leave Review
          </Link>
        </li>)}
      </ul>
    </>;
}
