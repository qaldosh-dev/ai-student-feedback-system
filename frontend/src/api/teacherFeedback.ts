import { AccountSessionExpired } from './auth';

export interface TeacherFeedbackGroup {
  courseOfferingId: number; courseCode: string; courseName: string; academicTerm: string;
  feedback: { rating: number; text: string; publishedAt: string }[];
}

export async function getTeacherFeedback(signal: AbortSignal): Promise<TeacherFeedbackGroup[]> {
  const response = await fetch('/api/teacher/feedback', {
    credentials: 'same-origin', signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (response.status === 401) throw new AccountSessionExpired();
  if (response.status === 403) throw new Error('Course feedback is unavailable. A linked teacher profile and Teacher access are required.');
  if (!response.ok) throw new Error('Could not load course feedback. Please try again.');
  return response.json();
}
