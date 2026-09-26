import test from 'node:test';
import assert from 'node:assert/strict';
import { summarizeVerification, verificationPresentation } from '../src/pages/verificationPresentation.ts';

const pendingExamples = {
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

for (const [reason, message] of Object.entries(pendingExamples)) {
  test(`student-friendly pending presentation for ${reason}`, () => {
    assert.deepEqual(verificationPresentation({ verificationStatus: 'PENDING', verificationReason: reason }),
      { label: 'Pending', reason: message });
  });
}

test('verified and neutral entries do not display pending reasons', () => {
  assert.deepEqual(verificationPresentation({ verificationStatus: 'VERIFIED', verificationReason: 'TEACHER_NOT_FOUND' }),
    { label: 'Verified', reason: null });
  assert.deepEqual(verificationPresentation({ verificationStatus: 'NOT_CHECKED', verificationReason: null }),
    { label: 'Not checked', reason: null });
});

test('unexpected values use safe text rather than leaking machine reasons', () => {
  for (const reason of [null, 'SQL_INTERNAL_DETAIL', 'toString']) {
    assert.deepEqual(verificationPresentation({ verificationStatus: 'PENDING', verificationReason: reason }),
      { label: 'Pending', reason: 'This entry could not be verified yet.' });
  }
  assert.deepEqual(verificationPresentation({}), { label: 'Not checked', reason: null });
});

test('summary counts source rows, not grouped courses, and leaves input unchanged', () => {
  const entries = Object.freeze(Array.from({ length: 10 }, () => Object.freeze({
    courseCode: 'ABC 100', verificationStatus: 'VERIFIED', verificationReason: null,
  })));
  assert.deepEqual(summarizeVerification(entries), { extracted: 10, verified: 10, pending: 0, notChecked: 0 });
  assert.equal(entries.length, 10);
});

test('mixed summary counts verified, pending and not checked separately', () => {
  const entries = [
    ...Array.from({ length: 8 }, () => ({ verificationStatus: 'VERIFIED', verificationReason: null })),
    { verificationStatus: 'PENDING', verificationReason: 'COURSE_NOT_FOUND' },
    { verificationStatus: 'PENDING', verificationReason: 'TEACHER_NOT_FOUND' },
  ];
  assert.deepEqual(summarizeVerification(entries), { extracted: 10, verified: 8, pending: 2, notChecked: 0 });
  entries.push({ verificationStatus: 'NOT_CHECKED', verificationReason: null });
  assert.deepEqual(summarizeVerification(entries), { extracted: 11, verified: 8, pending: 2, notChecked: 1 });
});

test('empty summary stays zero', () => {
  assert.deepEqual(summarizeVerification([]), { extracted: 0, verified: 0, pending: 0, notChecked: 0 });
});
