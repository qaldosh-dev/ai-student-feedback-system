import { AccountSessionExpired } from './auth';
import { analyticsQuery } from './teacherAnalytics';

export interface TeacherTopics {
  courseId: number;
  courseCode: string;
  courseName: string;
  academicTerm: string;
  status: 'AVAILABLE' | 'PRIVACY_PROTECTED' | 'UNAVAILABLE';
  analyzedReviewCount: number | null;
  topics: string[];
  concerns: string[];
  analysisAvailable: boolean;
  privacyThresholdMet: boolean;
  enoughRecurringFeedback: boolean;
}

export async function getTeacherTopics(courseId: number, term: string, signal: AbortSignal): Promise<TeacherTopics> {
  const response = await fetch(`/api/teacher/analytics/topics?${analyticsQuery(String(courseId), term)}`, {
    credentials: 'same-origin', signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
  });
  if (response.status === 401) throw new AccountSessionExpired();
  if (!response.ok) throw new Error('Topic analysis is temporarily unavailable.');
  return response.json();
}

