import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { relationshipHighlights } from '../src/pages/relationshipHighlights.ts';
const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)), server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(() => server.close());
const Teachers = (await server.ssrLoadModule('/src/components/TeachersSection.tsx')).default;
const Courses = (await server.ssrLoadModule('/src/components/CoursesSection.tsx')).default;
const rows = [
  { courseOfferingId: 1, courseId: 10, teacherId: 20, courseCode: 'ABC 100', courseName: 'Alpha Subject', teacherName: 'Alex Person', academicTerm: '2026-2027 1 term', groupSections: ['01-N', '02-P'] },
  { courseOfferingId: 2, courseId: 10, teacherId: 21, courseCode: 'ABC 100', courseName: 'Alpha Subject', teacherName: 'Morgan Person', academicTerm: '2026-2027 1 term', groupSections: ['04-N'] },
];
const props = { loading: false, error: false, query: '', hasActiveSearch: false, onQueryChange() {}, onRetry() {} };
test('actual teacher cards render contextual indicator only on eligible identity', () => {
  const html = renderToStaticMarkup(createElement(Teachers, { ...props, relationships: relationshipHighlights(rows, ['STUDENT']).teachers,
    teachers: [{ id: 20, name: 'Alex Person', department: '' }, { id: 99, name: 'Other Person', department: '' }] }));
  assert.equal((html.match(/has-verified-relationship/g) ?? []).length, 1);
  assert.match(html, /Verified with your schedule/); assert.match(html, /ABC 100 — Alpha Subject/);
  assert.match(html, /2026-2027 1 term/); assert.doesNotMatch(html, /Leave Review/);
});
test('actual course card renders both eligible teachers but no global verified-course label', () => {
  const html = renderToStaticMarkup(createElement(Courses, { ...props, relationships: relationshipHighlights(rows, ['STUDENT']).courses,
    courses: [{ id: 10, code: 'ABC 100', name: 'Alpha Subject' }, { id: 11, code: 'ABC 200', name: 'Other Subject' }] }));
  assert.equal((html.match(/has-verified-relationship/g) ?? []).length, 1);
  assert.match(html, /Alex Person/); assert.match(html, /Morgan Person/); assert.doesNotMatch(html, /Verified Course|Leave Review/);
});
test('actual non-student card rendering remains public without personal context', () => {
  const html = renderToStaticMarkup(createElement(Courses, { ...props, relationships: relationshipHighlights(rows, ['TEACHER']).courses,
    courses: [{ id: 10, code: 'ABC 100', name: 'Alpha Subject' }] }));
  assert.match(html, /Alpha Subject/); assert.doesNotMatch(html, /has-verified-relationship|Verified with your schedule|Alex Person/);
});

