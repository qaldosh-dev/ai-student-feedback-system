import { useEffect, useState } from 'react';
import { getCourses, getTeachers } from '../api/catalog';
import type { Course, Teacher } from '../api/catalog';
import CoursesSection from '../components/CoursesSection';
import TeachersSection from '../components/TeachersSection';
import { useAuth } from '../auth/AuthContext';
import { AccountSessionExpired } from '../api/auth';
import { listReviewEligibilities } from '../api/scheduleUploads';
import type { ReviewEligibility } from '../api/scheduleUploads';
import { relationshipHighlights } from './relationshipHighlights';

export default function BrowsePage() {
  const { currentUser, expireSession } = useAuth();
  const studentId = currentUser?.roles.includes('STUDENT') ? currentUser.id : null;
  const [eligibility, setEligibility] = useState<{ owner: number; rows: ReviewEligibility[] } | null>(null);
  const [eligibilityError, setEligibilityError] = useState<number | null>(null);
  const [eligibilityAttempt, setEligibilityAttempt] = useState(0);
  const highlights = relationshipHighlights(eligibility?.owner === studentId ? eligibility.rows : [], currentUser?.roles ?? []);

  useEffect(() => {
    setEligibility(null);
    setEligibilityError(null);
    if (studentId === null) return;
    const controller = new AbortController();
    listReviewEligibilities(controller.signal)
      .then(rows => { if (!controller.signal.aborted) setEligibility({ owner: studentId, rows }); })
      .catch(error => {
        if (controller.signal.aborted) return;
        if (error instanceof AccountSessionExpired) expireSession();
        else setEligibilityError(studentId);
      });
    return () => controller.abort();
  }, [studentId, expireSession, eligibilityAttempt]);
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
      <main id="main" className="container">
        <div className="intro">
          <p className="eyebrow">THE UNIVERSITY CATALOG</p>
          <h1>Explore teachers &amp; courses.</h1>
          <p className="intro-description">A starting point for understanding your learning experience.<br className="desktop-break" /> Browse the people and subjects that make up your university.</p>
          <p className="sample-note"><span aria-hidden="true" />Sample catalog · Names and teaching assignments are for demonstration.</p>
        </div>

        {studentId !== null && eligibilityError === studentId && <div className="state-message" role="status">
          <p>Your verified relationships could not be loaded. You can still browse the catalog.</p>
          <button type="button" onClick={() => setEligibilityAttempt(value => value + 1)}>Try again</button>
        </div>}
        <TeachersSection
          relationships={highlights.teachers}
          teachers={teachers}
          loading={teachersLoading}
          error={teachersError}
          query={teacherQuery}
          hasActiveSearch={Boolean(teacherQuery.trim() || courseQuery.trim())}
          onQueryChange={setTeacherQuery}
          onRetry={() => setTeacherAttempt((previous) => previous + 1)}
        />
        <CoursesSection
          relationships={highlights.courses}
          courses={courses}
          loading={coursesLoading}
          error={coursesError}
          query={courseQuery}
          hasActiveSearch={Boolean(teacherQuery.trim() || courseQuery.trim())}
          onQueryChange={setCourseQuery}
          onRetry={() => setCourseAttempt((previous) => previous + 1)}
        />
      </main>
  );
}
