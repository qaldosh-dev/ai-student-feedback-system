export interface PreviousRatings {
  teacherId: number; teacherName: string; courseId: number; courseCode: string; courseName: string;
  ratings: { courseOfferingId: number; academicTerm: string; averageRating: number; publishedReviewCount: number }[];
}

export async function getPreviousRatings(teacherId: string, courseId: string, signal: AbortSignal): Promise<PreviousRatings | null> {
  if (![teacherId, courseId].every(id => /^[1-9]\d*$/.test(id))) return null;
  const response = await fetch(`/api/teachers/${teacherId}/courses/${courseId}/ratings/history`, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error('Could not load previous ratings. Please try again.');
  return response.json();
}
