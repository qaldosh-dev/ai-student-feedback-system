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
const { default: PublicFeedbackPage, PublicFeedbackContent } = await server.ssrLoadModule('/src/pages/PublicFeedbackPage.tsx');
const { getPublicFeedback, publicFeedbackUrl } = await server.ssrLoadModule('/src/api/publicFeedback.ts');
const { TeacherProfileContent } = await server.ssrLoadModule('/src/pages/TeacherProfilePage.tsx');
const { CourseDetailsContent } = await server.ssrLoadModule('/src/pages/CourseDetailsPage.tsx');
const TERM = '2026-2027 1 term';
const data = { teacherId: 7, teacherName: 'Alex Person', courseId: 10, courseCode: 'ABC 100', courseName: 'Alpha Subject',
  courseOfferingId: 100, academicTerm: TERM,
  availableTargets: [{ courseOfferingId: 100, academicTerm: TERM }, { courseOfferingId: 101, academicTerm: TERM },
    { courseOfferingId: 100, academicTerm: '2025-2026 1 term' }, { courseOfferingId: 100, academicTerm: 'Fall 2026' }],
  feedback: [{ rating: 5, text: 'Newer feedback', publishedAt: '2026-10-08T07:00:00Z' },
    { rating: 3, text: 'Earlier feedback', publishedAt: '2026-10-07T07:00:00Z' }] };
const routed = element => renderToStaticMarkup(createElement(MemoryRouter, null, element));
const render = (props = {}) => routed(createElement(PublicFeedbackContent, { data, loading: false, error: '', onRetry() {}, ...props }));

test('public view renders exact teacher/course/term and individual feedback in backend order', () => {
  const html = render();
  for (const text of ['Alex Person', 'ABC 100', 'Alpha Subject', TERM, 'Newer feedback', 'Earlier feedback', '5 / 5', '3 / 5', 'Published:']) assert.ok(html.includes(text));
  assert.ok(html.indexOf('Newer feedback') < html.indexOf('Earlier feedback'));
  assert.match(html, /href="\/teachers\/7"/); assert.match(html, /href="\/courses\/10"/);
});
test('empty result does not reveal private statuses or counts', () => {
  const html = render({ data: { ...data, feedback: [] } });
  assert.match(html, /No published feedback yet for this course and term/);
  assert.doesNotMatch(html, /SUBMITTED|NEEDS_CORRECTION|pending|moderation|Newer feedback/);
});
test('unresolved offering selection is not presented as an empty review result', () => {
  const html = render({ data: { ...data, courseOfferingId: null, feedback: [] } });
  assert.match(html, /Choose a term and, if listed separately, a course offering/);
  assert.doesNotMatch(html, /No published feedback yet/);
});
test('loading and safe errors hide previous feedback and retry reloads', () => {
  assert.match(render({ loading: true }), /Loading published feedback/);
  assert.doesNotMatch(render({ loading: true }), /Newer feedback|Alex Person/);
  const html = render({ error: 'Could not load published feedback. Please try again.' });
  assert.match(html, /role="alert"/); assert.match(html, /Retry/); assert.doesNotMatch(html, /Newer feedback|Alex Person/);
  let attempts = 0;
  PublicFeedbackContent({ data, loading: false, error: 'Could not load', onRetry: () => attempts++ }).props.children[1].props.onClick();
  assert.equal(attempts, 1);
});
test('invalid/not-found selection has safe state', () => assert.match(render({ data: null }), /Feedback selection not found/));
test('only safe content rendered, no author identities, moderation internals or aggregate/edit controls', () => {
  const html = render({ data: { ...data, authorEmail: 'private@example.test', averageRating: 4.6,
    feedback: [{ ...data.feedback[0], text: '<script>unsafe()</script>', authorId: 98765, studentName: 'Private Student',
      email: 'private@example.test', groupSection: '15-P', moderationReasons: ['private reason'], categories: ['THREAT'], rawResponse: 'provider payload' }] } });
  assert.match(html, /&lt;script&gt;/);
  assert.doesNotMatch(html, /<script>|98765|Private Student|private@example|15-P|private reason|THREAT|provider payload|4\.6/);
  assert.doesNotMatch(html, /Student #|Anonymous Student|Edit|Delete|Publish review|Approve|Reject|<textarea|<input|<canvas|average|percentage|distribution|sentiment|summary/i);
});
test('direct route initializes without session or navigation state', () => {
  const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [publicFeedbackUrl(7, 10, TERM, 100)] },
    createElement(Routes, null, createElement(Route, { path: '/teachers/:teacherId/courses/:courseId/feedback', element: createElement(PublicFeedbackPage) }))));
  assert.match(html, /Published Feedback/); assert.match(html, /Loading published feedback/);
});
test('direct requests and refresh reload backend data; no status or author override; cancellation preserved', async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return { ok: true, status: 200, json: async () => data }; };
  const controller = new AbortController();
  for (let i = 0; i < 2; i++) assert.deepEqual(await getPublicFeedback('7', '10', TERM, '100', controller.signal), data);
  assert.equal(calls.length, 2);
  for (const call of calls) {
    const url = new URL(call.url, 'http://localhost');
    assert.equal(url.pathname, '/api/teachers/7/courses/10/feedback');
    assert.deepEqual([...url.searchParams.keys()], ['academicTerm', 'courseOfferingId']);
    assert.equal(url.searchParams.get('academicTerm'), TERM); assert.equal(url.searchParams.get('courseOfferingId'), '100');
    assert.equal(call.options.body, undefined);
  }
  controller.abort(); assert.ok(calls.every(call => call.options.signal.aborted));
});
test('term links retain exact source offering and issue different backend selections', async () => {
  const html = render();
  const hrefs = [...html.matchAll(/href="([^"]+\/feedback\?[^"]+)"/g)].map(match => match[1].replaceAll('&amp;', '&'));
  assert.equal(hrefs.length, 4);
  assert.match(html, /Course offering 100/); assert.match(html, /Course offering 101/);
  const calls = [];
  globalThis.fetch = async url => { calls.push(new URL(url, 'http://localhost')); return { ok: true, status: 200, json: async () => data }; };
  for (const href of hrefs) {
    const url = new URL(href, 'http://localhost');
    await getPublicFeedback('7', '10', url.searchParams.get('term'), url.searchParams.get('offering'), new AbortController().signal);
  }
  assert.deepEqual(calls.map(url => [url.searchParams.get('courseOfferingId'), url.searchParams.get('academicTerm')]),
    data.availableTargets.map(target => [String(target.courseOfferingId), target.academicTerm]));
});
test('term encoding preserves spaces and punctuation without normalization', async () => {
  const term = ' Spring + Autumn / 2026 & term ';
  const url = new URL(publicFeedbackUrl(7, 10, term, 100), 'http://localhost');
  assert.equal(url.searchParams.get('term'), term);
  globalThis.fetch = async address => { assert.equal(new URL(address, 'http://localhost').searchParams.get('academicTerm'), term); return { ok: true, json: async () => data }; };
  await getPublicFeedback('7', '10', term, '100', new AbortController().signal);
});
test('Teacher Profile and Course Details link to one shared experience with exact identities/terms', () => {
  const profile = routed(createElement(TeacherProfileContent, { profile: { id: 7, name: 'Alex Person', courseOfferings:
    [{ courseId: 10, courseCode: 'ABC 100', courseName: 'Alpha Subject', academicTerm: TERM }] }, loading: false, error: '', onRetry() {} }));
  const course = routed(createElement(CourseDetailsContent, { course: { courseId: 10, courseCode: 'ABC 100', courseName: 'Alpha Subject',
    teacherOfferings: [{ teacherId: 7, teacherName: 'Alex Person', academicTerm: TERM }] }, loading: false, error: '', onRetry() {} }));
  for (const html of [profile, course]) {
    const href = [...html.matchAll(/href="([^"]+)"[^>]*>View Feedback/g)][0][1].replaceAll('&amp;', '&');
    assert.equal(href, publicFeedbackUrl(7, 10, TERM));
  }
});
test('404 is neutral, server errors safe, invalid identities never fetched', async () => {
  globalThis.fetch = async () => ({ ok: false, status: 404 });
  assert.equal(await getPublicFeedback('7', '10', TERM, '100', new AbortController().signal), null);
  globalThis.fetch = async () => ({ ok: false, status: 500, json: async () => ({ message: 'Private SQL' }) });
  await assert.rejects(getPublicFeedback('7', '10', TERM, '100', new AbortController().signal), { message: 'Could not load published feedback. Please try again.' });
  globalThis.fetch = async () => { assert.fail('Invalid identity must not be fetched'); };
  assert.equal(await getPublicFeedback('abc', '10', TERM, '100', new AbortController().signal), null);
  assert.equal(await getPublicFeedback('7', '0', TERM, '100', new AbortController().signal), null);
  assert.equal(await getPublicFeedback('7', '10', TERM, '../secret', new AbortController().signal), null);
});
test('network failures cannot become successful feedback', async () => {
  globalThis.fetch = async () => { throw new TypeError('Connection failed'); };
  await assert.rejects(getPublicFeedback('7', '10', TERM, '100', new AbortController().signal));
});
test('production public route and existing protected teacher feedback remain separate', async () => {
  const routes = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  assert.match(routes, /path="\/teachers\/:teacherId\/courses\/:courseId\/feedback" element={<PublicFeedbackPage \/>}/);
  assert.match(routes, /path="\/teacher\/feedback" element={<ProtectedRoute requiredRole="TEACHER"><TeacherFeedbackPage/);
});
