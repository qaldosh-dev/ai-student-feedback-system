import { AccountSessionExpired, csrfHeaders } from './auth';

export interface ScheduleUpload {
  id: number;
  originalFilename: string;
  uploadedAt: string;
  status: 'UPLOADED' | 'VALIDATING' | 'VALID' | 'INVALID';
  validationMessage: string | null;
  validatedAt: string | null;
}

export async function uploadSchedule(file: File): Promise<ScheduleUpload> {
  const body = new FormData();
  body.append('file', file);
  const response = await fetch('/api/student/schedule-uploads', {
    method: 'POST', credentials: 'same-origin', headers: await csrfHeaders(), body,
    signal: AbortSignal.timeout(60000),
  });
  // The browser supplies the multipart boundary; do not set Content-Type manually.
  if (response.status === 401) throw new AccountSessionExpired();
  if (response.status === 403) throw new Error('Student access and a valid session are required. Please sign in again.');
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(typeof error?.message === 'string' ? error.message : 'Upload failed. Please try again.');
  }
  return response.json();
}
