export interface PublicFeedback {
  teacherId: number; teacherName: string; courseId: number; courseCode: string; courseName: string;
  courseOfferingId: number | null; academicTerm: string | null;
  availableTargets: { courseOfferingId: number; academicTerm: string }[];
  feedback: { rating: number; text: string; publishedAt: string }[];
}

export function publicFeedbackUrl(teacherId: number, courseId: number, term: string, offeringId?: number) {
  const query = new URLSearchParams({ term });
  if (offeringId !== undefined) query.set('offering', String(offeringId));
  return `/teachers/${teacherId}/courses/${courseId}/feedback?${query}`;
}

export async function getPublicFeedback(teacherId: string, courseId: string, term: string | null,
  offeringId: string | null, signal: AbortSignal): Promise<PublicFeedback | null> {
  if (![teacherId, courseId, ...(offeringId === null ? [] : [offeringId])].every(id => /^[1-9]\d*$/.test(id))) return null;
  const query = new URLSearchParams();
  if (term !== null) query.set('academicTerm', term);
  if (offeringId !== null) query.set('courseOfferingId', offeringId);
  const response = await fetch(`/api/teachers/${teacherId}/courses/${courseId}/feedback${query.size ? `?${query}` : ''}`, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error('Could not load published feedback. Please try again.');
  return response.json();
}
