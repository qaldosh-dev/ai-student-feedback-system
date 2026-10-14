import { AccountSessionExpired } from './auth';
import { analyticsQuery } from './teacherAnalytics';

export interface TeacherSummary {
  courseId: number;
  courseCode: string;
  courseName: string;
  academicTerm: string;
  status: 'AVAILABLE' | 'PRIVACY_PROTECTED' | 'UNAVAILABLE';
  analyzedReviewCount: number | null;
  summary: string | null;
  analysisAvailable: boolean;
  privacyThresholdMet: boolean;
  enoughFeedback: boolean;
}

export async function getTeacherSummary(courseId: number, term: string, signal: AbortSignal): Promise<TeacherSummary> {
  const response = await fetch(`/api/teacher/analytics/summary?${analyticsQuery(String(courseId), term)}`, {
    credentials: 'same-origin', signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
  });
  if (response.status === 401) throw new AccountSessionExpired();
  if (!response.ok) throw new Error('AI summary is temporarily unavailable.');
  return response.json();
}

