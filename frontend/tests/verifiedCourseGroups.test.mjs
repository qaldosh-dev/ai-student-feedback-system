import test from 'node:test';
import assert from 'node:assert/strict';
import { groupVerifiedCourses } from '../src/pages/verifiedCourseGroups.ts';

const source = {
  verifiedCourseId: 1, courseOfferingId: 100, uploadId: 21, sourceEntryId: 10,
  courseCode: 'ABC 123', courseName: 'Example Subject', teacherName: 'Alex Example',
  groupSection: '15-P', academicTerm: '2026-2027 1 term',
};
const copies = [21, 23, 24].map((uploadId, index) => ({
  ...source, uploadId, verifiedCourseId: index + 1, sourceEntryId: index + 10,
}));

test('three source verifications display once and preserve upload traceability without mutation', () => {
  const input = Object.freeze(copies.map(row => Object.freeze({ ...row })));
  const before = JSON.stringify(input);
  const groups = groupVerifiedCourses(input);
  assert.equal(groups.length, 1);
  assert.deepEqual(groups[0].uploadIds, [21, 23, 24]);
  assert.equal(JSON.stringify(input), before);
  assert.equal(input.length, 3);
});

test('different groups of the same offering and term become one card with every group', () => {
  const groups = groupVerifiedCourses([source, { ...source, groupSection: '04-N' }]);
  assert.equal(groups.length, 1);
  assert.deepEqual(groups[0].groupSections, ['04-N', '15-P']);
  assert.deepEqual(groups[0].uploadIds, [21]);
});

test('identical sections are deduplicated after conservative whitespace cleanup', () => {
  const groups = groupVerifiedCourses([source, source, { ...source, groupSection: ' 15-P ' }]);
  assert.deepEqual(groups[0].groupSections, ['15-P']);
});

test('section ordering does not depend on source order and N/P remain opaque', () => {
  const rows = ['03-P', '01-N', '02-P'].map(groupSection => ({ ...source, groupSection }));
  assert.deepEqual(groupVerifiedCourses(rows)[0].groupSections, ['01-N', '02-P', '03-P']);
  assert.deepEqual(groupVerifiedCourses(rows.toReversed())[0].groupSections, ['01-N', '02-P', '03-P']);
});

test('different teacher/offerings for the same course remain separate', () => {
  assert.equal(groupVerifiedCourses([source, {
    ...source, courseOfferingId: 101, teacherName: 'Jamie Example',
  }]).length, 2);
});

test('different academic terms remain separate', () => {
  assert.equal(groupVerifiedCourses([source, { ...source, academicTerm: '2025-2026 2 term' }]).length, 2);
});

test('thirty source records with the real sample shape produce six cards and three uploads each', () => {
  const sections = [['01-N', '02-P'], ['01-N', '02-P'], ['01-N', '03-P'], ['15-P'], ['04-N'], ['03-N', '04-P']];
  const rows = sections.flatMap((groups, index) => groups.flatMap(groupSection => copies.map(row => ({
    ...row, courseOfferingId: 100 + index, groupSection,
  }))));
  const groups = groupVerifiedCourses(rows);
  assert.equal(rows.length, 30);
  assert.equal(groups.length, 6);
  assert.deepEqual(groups.map(group => group.groupSections), sections);
  assert.ok(groups.every(group => group.uploadIds.length === 3));
  assert.deepEqual(groupVerifiedCourses(rows), groups);
});

test('source count is distinct uploads, not repeated rows from one upload', () => {
  assert.deepEqual(groupVerifiedCourses([source, { ...source, verifiedCourseId: 2 }])[0].uploadIds, [21]);
  assert.deepEqual(groupVerifiedCourses([]), []);
});
