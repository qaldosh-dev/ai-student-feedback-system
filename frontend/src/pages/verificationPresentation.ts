import type { PendingVerificationReason, VerificationStatus } from '../api/scheduleUploads';

type Outcome = { verificationStatus: VerificationStatus; verificationReason: PendingVerificationReason | null };

const pendingMessages: Record<PendingVerificationReason, string> = {
  COURSE_NOT_FOUND: 'Course not found in the catalog.',
  TEACHER_NOT_FOUND: 'Teacher not found in the catalog.',
  COURSE_AND_TEACHER_NOT_FOUND: 'Course and teacher were not found in the catalog.',
  NO_COURSE_OFFERING: 'The course and teacher exist, but their relationship was not found in the catalog.',
  AMBIGUOUS_COURSE: 'The course match is ambiguous and needs review.',
  AMBIGUOUS_TEACHER: 'The teacher match is ambiguous and needs review.',
  AMBIGUOUS_OFFERING: 'The course and teacher relationship is ambiguous and needs review.',
  COURSE_DATA_CONFLICT: 'The course code was found, but the course name does not match the catalog.',
  INCOMPLETE_ENTRY: 'The extracted schedule entry does not contain enough information to verify.',
};

export function verificationPresentation(entry: Outcome) {
  if (entry.verificationStatus === 'VERIFIED') return { label: 'Verified', reason: null };
  if (entry.verificationStatus === 'PENDING') return {
    label: 'Pending',
    reason: entry.verificationReason && Object.hasOwn(pendingMessages, entry.verificationReason)
      ? pendingMessages[entry.verificationReason] : 'This entry could not be verified yet.',
  };
  return { label: 'Not checked', reason: null };
}

export function summarizeVerification(entries: readonly Outcome[]) {
  const summary = { extracted: entries.length, verified: 0, pending: 0, notChecked: 0 };
  for (const entry of entries) {
    if (entry.verificationStatus === 'VERIFIED') summary.verified++;
    else if (entry.verificationStatus === 'PENDING') summary.pending++;
    else summary.notChecked++;
  }
  return summary;
}
