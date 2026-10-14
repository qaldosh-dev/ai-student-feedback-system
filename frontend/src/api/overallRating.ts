export interface OverallRating {
  teacherId: number;
  teacherName: string;
  averageRating: number | null;
  publishedReviewCount: number;
  ratingDistribution: Record<string, number>;
}

export async function getOverallRating(teacherId: string, signal: AbortSignal): Promise<OverallRating | null> {
  if (!/^[1-9]\d*$/.test(teacherId)) return null;
  const response = await fetch(`/api/teachers/${teacherId}/ratings/overall`, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error('Could not load overall rating. Please try again.');
  return response.json();
}
