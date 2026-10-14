import { test, after, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter, Routes, Route } from 'react-router';

const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)), server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(() => server.close());
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
const { default: CourseDetailsPage, CourseDetailsContent } = await server.ssrLoadModule('/src/pages/CourseDetailsPage.tsx');
const { getCourseDetails } = await server.ssrLoadModule('/src/api/catalog.ts');
const { TeacherProfileContent } = await server.ssrLoadModule('/src/pages/TeacherProfilePage.tsx');
const { default: CoursesSection } = await server.ssrLoadModule('/src/components/CoursesSection.tsx');
const teacher = { teacherId: 7, teacherName: 'Alex Person', academicTerm: 'Fall 2026' };
const course = { courseId: 10, courseCode: 'ABC 100', courseName: 'Alpha Subject', teacherOfferings: [teacher,
  { ...teacher, academicTerm: 'Spring 2025' }, { ...teacher, teacherId: 8, teacherName: 'Morgan Person' }] };
const routed = element => renderToStaticMarkup(createElement(MemoryRouter, null, element));
const render = (props = {}) => routed(createElement(CourseDetailsContent, { course, loading: false, error: '', onRetry() {}, ...props }));

test('course details render code/name, all teachers and separate terms with correct profile links', () => {
  const html = render();
  for (const text of ['ABC 100', 'Alpha Subject', 'Teachers teaching this course', 'Alex Person', 'Morgan Person', 'Fall 2026', 'Spring 2025']) assert.ok(html.includes(text));
  assert.equal((html.match(/<li /g) ?? []).length, 3);
  assert.equal((html.match(/href="\/teachers\/7"/g) ?? []).length, 2);
  assert.equal((html.match(/href="\/teachers\/8"/g) ?? []).length, 1);
  assert.equal((html.match(/>View Teacher Profile<\/a>/g) ?? []).length, 3);
});
test('empty offerings keep the course and show a clear empty state', () => {
  const html = render({ course: { ...course, teacherOfferings: [] } });
  assert.match(html, /ABC 100/); assert.match(html, /No teachers are currently listed for this course/);
  assert.doesNotMatch(html, /Course not found/);
});
test('not-found state is safe', () => { assert.match(render({ course: null }), /Course not found/); });
test('loading and error hide previous data; retry calls reload', () => {
  assert.match(render({ loading: true }), /Loading course details/);
  assert.doesNotMatch(render({ loading: true }), /Alpha Subject|Alex Person/);
  const html = render({ error: 'Could not load course details. Please try again.' });
  assert.match(html, /role="alert"/); assert.match(html, /Retry/); assert.doesNotMatch(html, /Alpha Subject|Alex Person/);
  let retries = 0;
  CourseDetailsContent({ course, loading: false, error: 'Could not load', onRetry: () => retries++ }).props.children[1].props.onClick();
  assert.equal(retries, 1);
});
test('public view escapes source text and ignores private/review/statistic fields without management controls', () => {
  const html = render({ course: { ...course, courseName: '<script>unsafe()</script>', reviews: [{ text: 'Private feedback' }], averageRating: 4.5,
    teacherOfferings: [{ ...teacher, email: 'private@example.test', roles: ['ADMIN'], passwordHash: 'private hash',
      moodleTeacherId: 99999, section: '15-P', studentName: 'Private Student', moderationStatus: 'PUBLISHED' }] } });
  assert.match(html, /&lt;script&gt;/);
  assert.doesNotMatch(html, /<script>|Private feedback|4\.5|private@example|ADMIN|private hash|99999|15-P|Private Student|PUBLISHED/);
  assert.doesNotMatch(html, /review|rating|average|statistics|edit|manage|<input|<button|<select|<canvas/i);
});
test('direct public route initializes without auth or navigation state', () => {
  const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/courses/10'] },
    createElement(Routes, null, createElement(Route, { path: '/courses/:courseId', element: createElement(CourseDetailsPage) }))));
  assert.match(html, /Course Details/); assert.match(html, /Loading course details/); assert.match(html, /Back to Browse/);
});
test('API independently fetches backend data on direct navigation and refresh, and retains cancellation', async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return { ok: true, status: 200, json: async () => course }; };
  const controller = new AbortController();
  for (let i = 0; i < 2; i++) assert.deepEqual(await getCourseDetails('10', controller.signal), course);
  assert.equal(calls.length, 2);
  for (const { url, options } of calls) {
    assert.equal(url, '/api/courses/10'); assert.equal(options.body, undefined); assert.ok(options.signal instanceof AbortSignal);
  }
  controller.abort(); assert.ok(calls.every(call => call.options.signal.aborted));
});
test('404 and invalid IDs become not-found without exposing response details', async () => {
  globalThis.fetch = async () => ({ ok: false, status: 404 });
  assert.equal(await getCourseDetails('987654', new AbortController().signal), null);
  globalThis.fetch = async () => { assert.fail('Invalid ID must not be fetched'); };
  for (const id of ['0', '-1', 'abc', '1/secret', '']) assert.equal(await getCourseDetails(id, new AbortController().signal), null);
});
test('backend errors use safe text; network failures cannot become successful data', async () => {
  globalThis.fetch = async () => ({ ok: false, status: 500, json: async () => ({ message: 'Private SQL' }) });
  await assert.rejects(getCourseDetails('10', new AbortController().signal), { message: 'Could not load course details. Please try again.' });
  globalThis.fetch = async () => { throw new TypeError('Connection failed'); };
  await assert.rejects(getCourseDetails('10', new AbortController().signal));
});
test('Browse Courses links correct course identities to details', () => {
  const html = routed(createElement(CoursesSection, { courses: [{ id: 10, code: 'ABC 100', name: 'Alpha Subject' }, { id: 11, code: 'DEF 200', name: 'Beta Subject' }],
    loading: false, error: false, query: '', hasActiveSearch: false, onQueryChange() {}, onRetry() {} }));
  assert.match(html, /href="\/courses\/10"[^>]*>View Course/); assert.match(html, /href="\/courses\/11"[^>]*>View Course/);
});
test('Teacher Profile links to the same Course Details across different terms', () => {
  const html = routed(createElement(TeacherProfileContent, { profile: { id: 7, name: 'Alex Person', courseOfferings:
    ['Fall 2026', 'Spring 2025'].map(academicTerm => ({ courseId: 10, courseCode: 'ABC 100', courseName: 'Alpha Subject', academicTerm })) },
    loading: false, error: '', onRetry() {} }));
  assert.equal((html.match(/href="\/courses\/10"/g) ?? []).length, 2);
  assert.match(html, /Fall 2026/); assert.match(html, /Spring 2025/);
});
test('production course and teacher routes remain public', async () => {
  const routes = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  assert.match(routes, /path="\/courses\/:courseId" element={<CourseDetailsPage \/>}/);
  assert.match(routes, /path="\/teachers\/:teacherId" element={<TeacherProfilePage \/>}/);
});
