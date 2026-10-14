import { AccountSessionExpired } from './auth';

export interface TeacherAnalyticsScope {
  courses: { courseId: number; courseCode: string; courseName: string; academicTerms: string[] }[];
  selectedCourseId: number | null;
  selectedAcademicTerm: string | null;
  hasPublishedFeedback: boolean;
  privacyThresholdMet: boolean;
}

export class AnalyticsLoadError extends Error { }

export async function getTeacherAnalytics(query: string, signal: AbortSignal): Promise<TeacherAnalyticsScope> {
  const response = await fetch(`/api/teacher/analytics/scope${query ? `?${query}` : ''}`, {
    credentials: 'same-origin', signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (response.status === 401) throw new AccountSessionExpired();
  if (response.status === 403) throw new AnalyticsLoadError('Teacher Analytics requires Teacher access and a linked teacher profile.');
  if (response.status === 400 || response.status === 404) throw new AnalyticsLoadError('This analytics selection is unavailable. Reset the selection to continue.');
  if (!response.ok) throw new AnalyticsLoadError('Could not load teacher analytics. Please try again.');
  return response.json();
}

export function analyticsQuery(courseId: string, term?: string): string {
  const query = new URLSearchParams({ courseId });
  if (term !== undefined) query.set('term', term);
  return query.toString();
}
