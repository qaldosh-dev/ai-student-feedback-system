import { test, after, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)), server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(() => server.close());
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
const { TeacherFeedbackContent } = await server.ssrLoadModule('/src/pages/TeacherFeedbackPage.tsx');
const { getTeacherFeedback } = await server.ssrLoadModule('/src/api/teacherFeedback.ts');
const group = { courseOfferingId: 100, courseCode: 'ABC 100', courseName: 'Alpha Subject', academicTerm: '2026-2027 1 term',
  feedback: [{ rating: 5, text: 'Newer feedback', publishedAt: '2026-10-08T07:00:00Z' },
    { rating: 3, text: 'Earlier feedback', publishedAt: '2026-10-07T07:00:00Z' }] };
const groups = [group, { ...group, academicTerm: '2025-2026 1 term', feedback: [group.feedback[0]] },
  { ...group, courseOfferingId: 101, feedback: [{ ...group.feedback[0], text: 'Separate offering' }] }];
const render = (props = {}) => renderToStaticMarkup(createElement(TeacherFeedbackContent, { groups, loading: false, error: '', onRefresh() {}, ...props }));

test('renders individual ratings/text and distinct offering/term groups in server order', () => {
  const html = render();
  assert.equal((html.match(/<h2>/g) ?? []).length, 3);
  for (const text of ['ABC 100','Alpha Subject','2026-2027 1 term','2025-2026 1 term','5 / 5','3 / 5','Newer feedback','Earlier feedback','Separate offering','Published Feedback']) assert.ok(html.includes(text));
  assert.ok(html.indexOf('Newer feedback') < html.indexOf('Earlier feedback'));
  assert.doesNotMatch(html, /Anonymous Student|Student 1|Author/);
});
test('empty feedback does not reveal private counts or whether offerings exist', () => {
  const html = render({ groups: [] });
  assert.match(html, /No published feedback yet/);
  assert.doesNotMatch(html, /SUBMITTED|NEEDS_CORRECTION|flagged|pending|Alpha Subject/);
});
test('loading hides previous feedback', () => {
  const html = render({ loading: true });
  assert.match(html, /role="status"/); assert.match(html, /Loading course feedback/); assert.doesNotMatch(html, /Newer feedback|Alpha Subject/);
});
test('error hides previous feedback and offers retry', () => {
  const html = render({ error: 'Could not load course feedback. Please try again.' });
  assert.match(html, /role="alert"/); assert.match(html, /Retry/); assert.doesNotMatch(html, /Newer feedback|Alpha Subject/);
});
test('API independently reloads direct/refresh requests with session, no teacherId, no draft state', async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return { ok: true, status: 200, json: async () => groups }; };
  for (let refresh = 0; refresh < 2; refresh++) assert.deepEqual(await getTeacherFeedback(new AbortController().signal), groups);
  assert.equal(calls.length, 2);
  for (const { url, options } of calls) {
    assert.equal(url, '/api/teacher/feedback'); assert.equal(options.credentials, 'same-origin');
    assert.ok(options.signal instanceof AbortSignal); assert.equal(options.body, undefined);
  }
});
test('refresh button calls reload action', () => {
  let calls = 0;
  const content = TeacherFeedbackContent({ groups, loading: false, error: '', onRefresh: () => calls++ });
  content.props.children[0].props.onClick(); assert.equal(calls, 1);
});
test('401 expires session', async () => {
  globalThis.fetch = async () => ({ ok: false, status: 401 });
  await assert.rejects(getTeacherFeedback(new AbortController().signal), /session has expired/);
});
for (const status of [400,403,500]) test(`HTTP ${status} has safe message without raw backend details`, async () => {
  globalThis.fetch = async () => ({ ok: false, status, json: async () => ({ message: 'Private SQL/identity detail' }) });
  await assert.rejects(getTeacherFeedback(new AbortController().signal), error => !error.message.includes('Private SQL')
    && (status === 403 ? error.message.includes('linked teacher profile') : error.message.includes('Could not load')));
});
test('network failure cannot render a successful result', async () => {
  globalThis.fetch = async () => { throw new TypeError('Connection failed'); };
  await assert.rejects(getTeacherFeedback(new AbortController().signal));
});
test('only safe fields rendered, text escaped; no identities, sections or provider details', () => {
  const html = render({ groups: [{ ...group, teacherId: 98765, groupSection: '15-P', studentEmail: 'private@example.test',
    feedback: [{ ...group.feedback[0], text: '<script>unsafe()</script>', authorId: 98765, studentName: 'Private Person', groupSection: '04-N',
      moderationReasons: ['private reason'], rawResponse: 'provider payload', status: 'PUBLISHED' }] }] });
  assert.match(html, /&lt;script&gt;/);
  assert.doesNotMatch(html, /<script>|98765|15-P|04-N|private@example|Private Person|private reason|provider payload/);
});
test('view-only: no review management, filters, AI or aggregate statistics', () => {
  const html = render();
  assert.doesNotMatch(html, /Edit|Delete|Archive|Reject|Approve|<input|<textarea|<select|<canvas|<svg|average|percentage|distribution|sentiment|summary/i);
  assert.equal((html.match(/<button/g) ?? []).length, 1); // Refresh only.
});
test('Teacher Area navigation and direct route use Teacher protection', async () => {
  const routes = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  const area = await readFile(new URL('../src/pages/RoleAreaPage.tsx', import.meta.url), 'utf8');
  assert.match(routes, /path="\/teacher\/feedback" element={<ProtectedRoute requiredRole="TEACHER"><TeacherFeedbackPage/);
  assert.match(area, /role === 'TEACHER' && home && !loading && !error && <p><Link to="\/teacher\/feedback">Course Feedback/);
});
