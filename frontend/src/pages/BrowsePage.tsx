import { useEffect, useState } from 'react';
import { getCourses, getTeachers } from '../api/catalog';
import type { Course, Teacher } from '../api/catalog';
import CoursesSection from '../components/CoursesSection';
import TeachersSection from '../components/TeachersSection';

export default function BrowsePage() {
  const [teachers, setTeachers] = useState<Teacher[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [teachersLoading, setTeachersLoading] = useState(true);
  const [coursesLoading, setCoursesLoading] = useState(true);
  const [teachersError, setTeachersError] = useState(false);
  const [coursesError, setCoursesError] = useState(false);
  const [teacherQuery, setTeacherQuery] = useState('');
  const [courseQuery, setCourseQuery] = useState('');
  const [teacherAttempt, setTeacherAttempt] = useState(0);
  const [courseAttempt, setCourseAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setTeachersLoading(true);
    setTeachersError(false);

    // Ignore cancelled results so an older search cannot replace the current one.
    getTeachers(controller.signal, teacherQuery, courseQuery)
      .then((data) => {
        if (!controller.signal.aborted) setTeachers(data);
      })
      .catch(() => {
        if (!controller.signal.aborted) setTeachersError(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setTeachersLoading(false);
      });

    return () => controller.abort();
  }, [teacherQuery, courseQuery, teacherAttempt]);

  useEffect(() => {
    const controller = new AbortController();
    setCoursesLoading(true);
    setCoursesError(false);

    getCourses(controller.signal, courseQuery, teacherQuery)
      .then((data) => {
        if (!controller.signal.aborted) setCourses(data);
      })
      .catch(() => {
        if (!controller.signal.aborted) setCoursesError(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setCoursesLoading(false);
      });

    return () => controller.abort();
  }, [courseQuery, teacherQuery, courseAttempt]);

  return (
    <>
      <a className="skip-link" href="#main">Skip to content</a>
      <header className="site-header">
        <div className="container header-content">
          <a className="brand" href="/" aria-label="Student Feedback home">
            <span className="brand-mark" aria-hidden="true">sdu</span>
            <span className="brand-text">Student Feedback<span>University project</span></span>
          </a>
          <nav aria-label="Catalog sections">
            <a href="#teachers">Teachers</a>
            <a href="#courses">Courses</a>
          </nav>
        </div>
      </header>

      <main id="main" className="container">
        <div className="intro">
          <p className="eyebrow">THE UNIVERSITY CATALOG</p>
          <h1>Explore teachers &amp; courses.</h1>
          <p className="intro-description">A starting point for understanding your learning experience.<br className="desktop-break" /> Browse the people and subjects that make up your university.</p>
          <p className="sample-note"><span aria-hidden="true" />Sample catalog · Names and teaching assignments are for demonstration.</p>
        </div>

        <TeachersSection
          teachers={teachers}
          loading={teachersLoading}
          error={teachersError}
          query={teacherQuery}
          hasActiveSearch={Boolean(teacherQuery.trim() || courseQuery.trim())}
          onQueryChange={setTeacherQuery}
          onRetry={() => setTeacherAttempt((previous) => previous + 1)}
        />
        <CoursesSection
          courses={courses}
          loading={coursesLoading}
          error={coursesError}
          query={courseQuery}
          hasActiveSearch={Boolean(teacherQuery.trim() || courseQuery.trim())}
          onQueryChange={setCourseQuery}
          onRetry={() => setCourseAttempt((previous) => previous + 1)}
        />
      </main>

      <footer className="container site-footer">
        <p>AI Student Feedback System</p>
        <p>SDU University · Student MVP</p>
      </footer>
    </>
  );
}
