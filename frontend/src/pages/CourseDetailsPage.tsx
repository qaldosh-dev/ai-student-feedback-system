import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { getCourseDetails } from '../api/catalog';
import type { CourseDetails } from '../api/catalog';
import { publicFeedbackUrl } from '../api/publicFeedback';

export default function CourseDetailsPage() {
  const { courseId = '' } = useParams();
  return <Details key={courseId} courseId={courseId} />;
}

function Details({ courseId }: { courseId: string }) {
  const [course, setCourse] = useState<CourseDetails | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(''); setCourse(null);
    getCourseDetails(courseId, controller.signal)
      .then(result => { if (!controller.signal.aborted) setCourse(result); })
      .catch(() => { if (!controller.signal.aborted) setError('Could not load course details. Please try again.'); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [courseId, attempt]);

  return <main id="main" className="container role-area">
    <Link to="/">Back to Browse</Link>
    <h1>Course Details</h1>
    <CourseDetailsContent course={course} loading={loading} error={error} onRetry={() => setAttempt(value => value + 1)} />
  </main>;
}

export function CourseDetailsContent({ course, loading, error, onRetry }: {
  course: CourseDetails | null; loading: boolean; error: string; onRetry: () => void;
}) {
  if (loading) return <p role="status">Loading course details…</p>;
  if (error) return <div className="state-message error-message" role="alert"><p>{error}</p><button type="button" onClick={onRetry}>Retry</button></div>;
  if (!course) return <p className="state-message">Course not found.</p>;
  return <>
    <h2>{course.courseCode} — {course.courseName}</h2>
    <section className="catalog-section" aria-labelledby="course-teachers-title">
      <h2 id="course-teachers-title">Teachers teaching this course</h2>
      {course.teacherOfferings.length === 0 ? <p className="state-message">No teachers are currently listed for this course.</p> :
        <ul className="schedule-list">
          {course.teacherOfferings.map(teacher => <li key={JSON.stringify([teacher.teacherId, teacher.academicTerm])} className="course-card schedule-card">
            <h3>{teacher.teacherName}</h3>
            <p><strong>Academic Term:</strong> {teacher.academicTerm}</p>
            <Link to={`/teachers/${teacher.teacherId}`}>View Teacher Profile</Link>
            <Link to={publicFeedbackUrl(teacher.teacherId, course.courseId, teacher.academicTerm)}>View Feedback</Link>
          </li>)}
        </ul>}
    </section>
  </>;
}
