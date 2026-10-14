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
const { default: TeacherProfilePage, TeacherProfileContent } = await server.ssrLoadModule('/src/pages/TeacherProfilePage.tsx');
const { getTeacherProfile } = await server.ssrLoadModule('/src/api/catalog.ts');
const { MyProfileLink } = await server.ssrLoadModule('/src/pages/RoleAreaPage.tsx');
const { default: TeachersSection } = await server.ssrLoadModule('/src/components/TeachersSection.tsx');
const course = { courseId: 10, courseCode: 'ABC 100', courseName: 'Alpha Subject', academicTerm: 'Fall 2026' };
const profile = { id: 7, name: 'Alex Person', courseOfferings: [course, { ...course, academicTerm: 'Spring 2025' }] };
const render = (props = {}) => renderToStaticMarkup(createElement(MemoryRouter, null,
  createElement(TeacherProfileContent, { profile, loading: false, error: '', onRetry() {}, ...props })));
const routed = element => renderToStaticMarkup(createElement(MemoryRouter, null, element));

test('profile renders teacher and course data with distinct terms', () => {
  const html = render();
  for (const text of ['Alex Person', 'Courses taught', 'ABC 100', 'Alpha Subject', 'Fall 2026', 'Spring 2025']) assert.ok(html.includes(text));
  assert.equal((html.match(/<li /g) ?? []).length, 2);
});
test('empty offerings keep the teacher and show a clear empty state', () => {
  const html = render({ profile: { ...profile, courseOfferings: [] } });
  assert.match(html, /Alex Person/); assert.match(html, /No courses listed for this teacher yet/);
});
test('not-found state is safe', () => { assert.match(render({ profile: null }), /Teacher not found/); });
test('loading and error hide previous profile; retry calls the reload action', () => {
  assert.match(render({ loading: true }), /Loading teacher profile/);
  assert.doesNotMatch(render({ loading: true }), /Alex Person/);
  const html = render({ error: 'Could not load teacher profile. Please try again.' });
  assert.match(html, /role="alert"/); assert.match(html, /Retry/); assert.doesNotMatch(html, /Alex Person/);
  let retries = 0;
  TeacherProfileContent({ profile, loading: false, error: 'Could not load', onRetry: () => retries++ }).props.children[1].props.onClick();
  assert.equal(retries, 1);
});
test('public view renders only allowed content, escapes names, has no owner controls or review data', () => {
  const html = render({ profile: { ...profile, name: '<script>unsafe()</script>', email: 'private@example.test', roles: ['ADMIN'],
    reviews: [{ text: 'Private review' }], averageRating: 4.5, passwordHash: 'private hash',
    courseOfferings: [{ ...course, moodleCourseId: 99999, section: '15-P', studentName: 'Private Student' }] } });
  assert.match(html, /&lt;script&gt;/);
  assert.doesNotMatch(html, /<script>|private@example|ADMIN|Private review|4\.5|private hash|99999|15-P|Private Student/);
  assert.doesNotMatch(html, /Private review|edit|<input|<button/i);
  assert.match(html, /Overall Rating/); // US32 loads its own safe aggregate DTO.
  assert.match(html, /href="\/courses\/10"/);
});
test('direct public route initializes profile page without auth or navigation state', () => {
  const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/teachers/7'] },
    createElement(Routes, null, createElement(Route, { path: '/teachers/:teacherId', element: createElement(TeacherProfilePage) }))));
  assert.match(html, /Teacher Profile/); assert.match(html, /Loading teacher profile/); assert.match(html, /Back to Browse/);
});
test('API loads independently for direct navigation and refresh, using only public teacher ID', async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return { ok: true, status: 200, json: async () => profile }; };
  const controller = new AbortController();
  for (let i = 0; i < 2; i++) assert.deepEqual(await getTeacherProfile('7', controller.signal), profile);
  assert.equal(calls.length, 2);
  for (const { url, options } of calls) {
    assert.equal(url, '/api/teachers/7'); assert.equal(options.body, undefined); assert.ok(options.signal instanceof AbortSignal);
  }
  controller.abort(); assert.ok(calls.every(call => call.options.signal.aborted));
});
test('404 and invalid route IDs become not-found without exposing response details', async () => {
  globalThis.fetch = async () => ({ ok: false, status: 404 });
  assert.equal(await getTeacherProfile('987654', new AbortController().signal), null);
  globalThis.fetch = async () => { assert.fail('Invalid ID must not be fetched'); };
  for (const id of ['0', '-1', 'abc', '1/secret', '']) assert.equal(await getTeacherProfile(id, new AbortController().signal), null);
});
test('backend errors use a safe message, and network failures cannot become successful profiles', async () => {
  globalThis.fetch = async () => ({ ok: false, status: 500, json: async () => ({ message: 'Private SQL' }) });
  await assert.rejects(getTeacherProfile('7', new AbortController().signal), { message: 'Could not load teacher profile. Please try again.' });
  globalThis.fetch = async () => { throw new TypeError('Connection failed'); };
  await assert.rejects(getTeacherProfile('7', new AbortController().signal));
});
test('Browse Teachers links each teacher to their public profile', () => {
  const html = routed(createElement(TeachersSection, { teachers: [{ id: 7, name: 'Alex Person', department: null }],
    loading: false, error: false, query: '', hasActiveSearch: false, onQueryChange() {}, onRetry() {} }));
  assert.match(html, /href="\/teachers\/7"[^>]*>View Profile/); assert.match(html, /Alex Person/);
});
test('My Profile uses authenticated linkedTeacher identity and omits the link when missing', () => {
  assert.match(routed(createElement(MyProfileLink, { linkedTeacher: { id: 7, name: 'Alex Person' } })), /href="\/teachers\/7"[^>]*>My Profile/);
  assert.equal(routed(createElement(MyProfileLink, { linkedTeacher: null })), '');
});
test('production routing is public; My Profile is beside unchanged protected Course Feedback', async () => {
  const routes = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  const area = await readFile(new URL('../src/pages/RoleAreaPage.tsx', import.meta.url), 'utf8');
  assert.match(routes, /path="\/teachers\/:teacherId" element={<TeacherProfilePage \/>}/);
  assert.match(area, /role === 'TEACHER' && home && !loading && !error && <MyProfileLink linkedTeacher={home.linkedTeacher}/);
  assert.match(area, /No teacher profile is linked to this account/);
  assert.match(area, /to="\/teacher\/feedback">Course Feedback/);
});
