import { AccountSessionExpired, csrfHeaders } from './auth';

export interface ScheduleUpload {
  id: number;
  originalFilename: string;
  uploadedAt: string;
  status: 'UPLOADED' | 'VALIDATING' | 'VALID' | 'INVALID';
  validationMessage: string | null;
  validatedAt: string | null;
}

export interface ScheduleUploadView {
  id: number;
  originalFilename: string;
  uploadedAt: string;
  status: ScheduleUpload['status'];
  validationMessage: string | null;
  academicTerms: string[];
  extractedCount: number;
}

export interface ExtractedSchedule {
  upload: ScheduleUploadView;
  entries: {
    id: number;
    courseCode: string;
    courseName: string;
    teacherName: string;
    groupSection: string;
    academicTerm: string;
  }[];
}

async function readSchedule<T>(path: string, signal: AbortSignal): Promise<T> {
  const response = await fetch(path, {
    credentials: 'same-origin', signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (response.status === 401) throw new AccountSessionExpired();
  if (response.status === 403) throw new Error('You do not have access to this area.');
  if (response.status === 404) throw new Error('Schedule upload not found.');
  if (!response.ok) throw new Error('Could not load schedule information. Please try again.');
  return response.json();
}

export function listScheduleUploads(signal: AbortSignal) {
  return readSchedule<ScheduleUploadView[]>('/api/student/schedule-uploads', signal);
}

export function getExtractedSchedule(uploadId: string, signal: AbortSignal) {
  return readSchedule<ExtractedSchedule>(`/api/student/schedule-uploads/${encodeURIComponent(uploadId)}/extracted`, signal);
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
