import { AccountSessionExpired } from './auth';
import { analyticsQuery } from './teacherAnalytics';

export interface TeacherSentiment {
  courseId: number;
  courseCode: string;
  courseName: string;
  academicTerm: string;
  status: 'AVAILABLE' | 'PRIVACY_PROTECTED' | 'UNAVAILABLE';
  analysisAvailable: boolean;
  privacyThresholdMet: boolean;
  analyzedReviewCount: number | null;
  overallSentiment: 'MOSTLY_POSITIVE' | 'MOSTLY_NEUTRAL' | 'MOSTLY_NEGATIVE' | 'MIXED' | null;
  positiveCount: number | null;
  neutralCount: number | null;
  negativeCount: number | null;
  positivePercentage: number | null;
  neutralPercentage: number | null;
  negativePercentage: number | null;
}

export async function getTeacherSentiment(courseId: number, term: string, signal: AbortSignal): Promise<TeacherSentiment> {
  const response = await fetch(`/api/teacher/analytics/sentiment?${analyticsQuery(String(courseId), term)}`, {
    credentials: 'same-origin', signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
  });
  if (response.status === 401) throw new AccountSessionExpired();
  if (!response.ok) throw new Error('Sentiment analysis is temporarily unavailable.');
  return response.json();
}

