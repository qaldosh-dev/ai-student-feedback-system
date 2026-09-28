import type { ReviewEligibility } from '../api/scheduleUploads';

export interface RelationshipContext { key: string; label: string; term: string; target: ReviewEligibility }

// Identity comes from the backend's offering + term, never a course/teacher name alone.
export function relationshipHighlights(rows: ReviewEligibility[], roles: readonly string[]) {
  const teachers = new Map<number, RelationshipContext[]>();
  const courses = new Map<number, RelationshipContext[]>();
  if (!roles.includes('STUDENT')) return { teachers, courses };
  const seen = new Set<string>();
  for (const row of rows) {
    const key = JSON.stringify([row.courseOfferingId, row.academicTerm]);
    if (seen.has(key)) continue;
    seen.add(key);
    teachers.set(row.teacherId, [...(teachers.get(row.teacherId) ?? []),
      { key, label: `${row.courseCode} — ${row.courseName}`, term: row.academicTerm, target: row }]);
    courses.set(row.courseId, [...(courses.get(row.courseId) ?? []),
      { key, label: row.teacherName, term: row.academicTerm, target: row }]);
  }
  return { teachers, courses };
}
