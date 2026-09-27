import { test } from 'node:test';
import assert from 'node:assert/strict';
import { relationshipHighlights } from '../src/pages/relationshipHighlights.ts';
const row = (changes = {}) => ({ courseOfferingId: 1, courseId: 10, teacherId: 20, courseCode: 'ABC 100',
  courseName: 'Alpha Subject', teacherName: 'Alex Person', academicTerm: '2026-2027 1 term', groupSections: ['01-N'], ...changes });
const show = rows => relationshipHighlights(rows, ['STUDENT']);
test('teacher context names the exact eligible course and term', () => {
  assert.deepEqual(show([row()]).teachers.get(20).map(r => [r.label, r.term]), [['ABC 100 — Alpha Subject', '2026-2027 1 term']]);
});
test('teacher without eligibility has no indicator', () => assert.equal(show([row()]).teachers.has(21), false));
test('one teacher has two distinct eligible course contexts', () => {
  const result = show([row(), row({ courseOfferingId: 2, courseId: 11, courseCode: 'ABC 200', courseName: 'Beta Subject' })]);
  assert.deepEqual(result.teachers.get(20).map(r => r.label), ['ABC 100 — Alpha Subject', 'ABC 200 — Beta Subject']);
});
test('course context names the exact eligible teacher and term', () => assert.equal(show([row()]).courses.get(10)[0].label, 'Alex Person'));
test('same course retains two eligible teachers', () => {
  const result = show([row(), row({ courseOfferingId: 2, teacherId: 21, teacherName: 'Morgan Person' })]);
  assert.deepEqual(result.courses.get(10).map(r => r.label), ['Alex Person', 'Morgan Person']);
});
test('unrelated course and identically named different teacher are not highlighted', () => {
  const result = show([row()]); assert.equal(result.courses.has(11), false); assert.equal(result.teachers.has(99), false);
});
for (const roles of [[], ['TEACHER'], ['ADMIN']]) test(`no student highlights for ${roles.join() || 'anonymous'}`, () => {
  const result = relationshipHighlights([row()], roles); assert.equal(result.teachers.size, 0); assert.equal(result.courses.size, 0);
});
test('multi-role student keeps highlights', () => assert.equal(relationshipHighlights([row()], ['STUDENT', 'TEACHER']).courses.size, 1));
test('duplicate uploads cannot duplicate displayed relationship', () => assert.equal(show([row(), row(), row()]).courses.get(10).length, 1));
test('group sections do not create extra targets', () => assert.equal(show([row(), row({ groupSections: ['02-P'] })]).teachers.get(20).length, 1));
test('different academic terms remain visible separately', () => {
  assert.deepEqual(show([row(), row({ academicTerm: '2025-2026 2 term' })]).courses.get(10).map(r => r.term), ['2026-2027 1 term', '2025-2026 2 term']);
});
