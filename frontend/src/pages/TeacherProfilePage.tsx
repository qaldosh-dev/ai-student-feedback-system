import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { getTeacherProfile } from '../api/catalog';
import type { TeacherProfile } from '../api/catalog';
import { publicFeedbackUrl } from '../api/publicFeedback';
import OverallRatingStats from '../components/OverallRatingStats';

export default function TeacherProfilePage() {
  const { teacherId = '' } = useParams();
  return <Profile key={teacherId} teacherId={teacherId} />;
}

function Profile({ teacherId }: { teacherId: string }) {
  const [profile, setProfile] = useState<TeacherProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(''); setProfile(null);
    getTeacherProfile(teacherId, controller.signal)
      .then(result => { if (!controller.signal.aborted) setProfile(result); })
      .catch(() => { if (!controller.signal.aborted) setError('Could not load teacher profile. Please try again.'); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [teacherId, attempt]);

  return <main id="main" className="container role-area">
    <Link to="/">Back to Browse</Link>
    <h1>Teacher Profile</h1>
    <TeacherProfileContent profile={profile} loading={loading} error={error} onRetry={() => setAttempt(value => value + 1)} />
  </main>;
}

export function TeacherProfileContent({ profile, loading, error, onRetry }: {
  profile: TeacherProfile | null; loading: boolean; error: string; onRetry: () => void;
}) {
  if (loading) return <p role="status">Loading teacher profile…</p>;
  if (error) return <div className="state-message error-message" role="alert"><p>{error}</p><button type="button" onClick={onRetry}>Retry</button></div>;
  if (!profile) return <p className="state-message">Teacher not found.</p>;
  return <>
    <h2>{profile.name}</h2>
    <OverallRatingStats key={profile.id} teacherId={profile.id} />
    <section className="catalog-section" aria-labelledby="profile-courses-title">
      <h2 id="profile-courses-title">Courses taught</h2>
      {profile.courseOfferings.length === 0 ? <p className="state-message">No courses listed for this teacher yet.</p> :
        <ul className="schedule-list">
          {profile.courseOfferings.map(course => <li key={JSON.stringify([course.courseId, course.academicTerm])} className="course-card schedule-card">
            <h3>{course.courseCode} — {course.courseName}</h3>
            <p><strong>Academic Term:</strong> {course.academicTerm}</p>
            <Link to={`/courses/${course.courseId}`}>View Course</Link>
            <Link to={publicFeedbackUrl(profile.id, course.courseId, course.academicTerm)}>View Feedback</Link>
          </li>)}
        </ul>}
    </section>
  </>;
}
