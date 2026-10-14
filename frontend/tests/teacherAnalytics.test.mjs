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
const { TeacherAnalyticsContent } = await server.ssrLoadModule('/src/pages/TeacherAnalyticsPage.tsx');
const { getTeacherAnalytics, analyticsQuery } = await server.ssrLoadModule('/src/api/teacherAnalytics.ts');
const term = '2026-2027 1 term';
const courses = [
  { courseId: 10, courseCode: 'ABC 100', courseName: 'Alpha Subject', academicTerms: ['Fall 2026', term, ` ${term} `] },
  { courseId: 20, courseCode: 'DEF 200', courseName: 'Second Subject', academicTerms: ['Spring 2025'] },
];
const data = { courses, selectedCourseId: 10, selectedAcademicTerm: term, hasPublishedFeedback: true, privacyThresholdMet: true };
const props = { data, loading: false, error: '', onSelection() {}, onRetry() {} };
const render = (overrides = {}) => renderToStaticMarkup(createElement(TeacherAnalyticsContent, { ...props, ...overrides }));
function elements(node) {
  if (!node || typeof node !== 'object') return [];
  if (Array.isArray(node)) return node.flatMap(elements);
  return [node, ...elements(node.props?.children)];
}

test('renders only server-owned courses, exact terms and selected scope', () => {
  const html = render();
  assert.equal((html.match(/<select/g) ?? []).length, 2);
  for (const label of ['Course', 'Academic Term', 'ABC 100', 'Alpha Subject', 'DEF 200', 'Second Subject', term, 'Fall 2026'])
    assert.ok(html.includes(label));
  assert.match(html, /Selected: ABC 100 — Alpha Subject/);
  assert.match(html, /Published feedback is available for this selection/);
  assert.ok(html.includes(`value=" ${term} "`));
  assert.doesNotMatch(html, /Spring 2025|Foreign Subject/);
});
test('course selection discards stale term; server response supplies the new course terms', () => {
  let selection;
  const tree = TeacherAnalyticsContent({ ...props, onSelection: query => selection = query });
  elements(tree).find(node => node.props?.id === 'analytics-course').props.onChange({ target: { value: '20' } });
  assert.equal(selection, 'courseId=20');
  const html = render({ data: { ...data, selectedCourseId: 20, selectedAcademicTerm: 'Spring 2025', hasPublishedFeedback: false } });
  assert.match(html, /Selected: DEF 200 — Second Subject/);
  assert.match(html, /Spring 2025/); assert.doesNotMatch(html, /Fall 2026|2026-2027/);
});
test('term selection preserves exact strings and selected course in URL', () => {
  let selection;
  const tree = TeacherAnalyticsContent({ ...props, onSelection: query => selection = query });
  elements(tree).find(node => node.props?.id === 'analytics-term').props.onChange({ target: { value: ` ${term} ` } });
  const parsed = new URLSearchParams(selection);
  assert.equal(parsed.get('courseId'), '10'); assert.equal(parsed.get('term'), ` ${term} `);
  assert.deepEqual([...parsed.keys()], ['courseId', 'term']);
});
test('empty courses have a safe state without controls', () => {
  const html = render({ data: { courses: [], selectedCourseId: null, selectedAcademicTerm: null, hasPublishedFeedback: false } });
  assert.match(html, /No courses available for analytics/); assert.doesNotMatch(html, /<select|Alpha Subject/);
});
test('no published feedback reveals no private review existence', () => {
  const html = render({ data: { ...data, hasPublishedFeedback: false, privacyThresholdMet: false } });
  assert.doesNotMatch(html, /No published feedback|Published feedback is available/);
  assert.doesNotMatch(html, /SUBMITTED|NEEDS_CORRECTION|pending|hidden|sentiment|summary/i);
});
test('loading hides previous scope', () => {
  const html = render({ loading: true });
  assert.match(html, /role="status"/); assert.match(html, /Loading teacher analytics/);
  assert.doesNotMatch(html, /Alpha Subject|<select|Published feedback is available/);
});
test('safe invalid scope error hides data and supports reset/retry', () => {
  let retry = 0, selection;
  const error = 'This analytics selection is unavailable. Reset the selection to continue.';
  const tree = TeacherAnalyticsContent({ ...props, error, onRetry: () => retry++, onSelection: query => selection = query });
  const buttons = elements(tree).filter(node => node.type === 'button');
  buttons[0].props.onClick(); buttons[1].props.onClick();
  assert.equal(retry, 1); assert.equal(selection, '');
  const html = render({ error });
  assert.match(html, /role="alert"/); assert.match(html, /Reset selection/); assert.doesNotMatch(html, /Alpha Subject|<select/);
});
test('linked teacher error displays clearly without another teacher scope', () => {
  const html = render({ error: 'Teacher Analytics requires Teacher access and a linked teacher profile.' });
  assert.match(html, /linked teacher profile/); assert.doesNotMatch(html, /Alpha Subject|<select/);
});
test('does not render identities, review text, groups, moderation or statistics', () => {
  const html = render({ data: { ...data, authorId: 7654321, studentEmail: 'private@example.test', groupSection: '15-P',
    reviewText: 'Private feedback', moderationReasons: ['THREAT'], averageRating: 4.2, publishedReviewCount: 99,
    courses: courses.map(course => ({ ...course, groupSection: '04-N', studentName: 'Private Person' })) } });
  assert.doesNotMatch(html, /7654321|private@example|15-P|04-N|Private feedback|Private Person|THREAT|4\.2|99/);
  assert.doesNotMatch(html, /sentiment|topics|summary|chart|distribution|<canvas|<svg|<textarea|<input/i);
});
test('direct URL and refresh fetch the same exact query with a session and cancellation', async () => {
  const calls = [];
  const query = analyticsQuery('10', ` ${term} `);
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return { ok: true, status: 200, json: async () => data }; };
  const controller = new AbortController();
  for (let refresh = 0; refresh < 2; refresh++) assert.deepEqual(await getTeacherAnalytics(query, controller.signal), data);
  assert.equal(calls.length, 2);
  for (const { url, options } of calls) {
    assert.equal(url, `/api/teacher/analytics/scope?${query}`); assert.equal(options.credentials, 'same-origin');
    assert.equal(options.body, undefined); assert.ok(options.signal instanceof AbortSignal);
  }
  controller.abort(); assert.ok(calls.every(call => call.options.signal.aborted));
});
test('initial request has no client teacher ownership input; tampered URL reaches server validation', async () => {
  const urls = [];
  globalThis.fetch = async url => { urls.push(url); return { ok: true, status: 200, json: async () => data }; };
  await getTeacherAnalytics('', new AbortController().signal);
  await getTeacherAnalytics('teacherId=123', new AbortController().signal);
  assert.deepEqual(urls, ['/api/teacher/analytics/scope', '/api/teacher/analytics/scope?teacherId=123']);
});
test('401 expires session', async () => {
  globalThis.fetch = async () => ({ ok: false, status: 401 });
  await assert.rejects(getTeacherAnalytics('', new AbortController().signal), /session has expired/);
});
for (const status of [400,403,404,500]) test(`HTTP ${status} produces a safe error, never backend details`, async () => {
  globalThis.fetch = async () => ({ ok: false, status, json: async () => ({ message: 'Private SQL/identity detail' }) });
  await assert.rejects(getTeacherAnalytics('', new AbortController().signal), error => {
    assert.doesNotMatch(error.message, /Private SQL/);
    assert.match(error.message, status === 403 ? /linked teacher profile/ : status === 500 ? /Could not load/ : /selection is unavailable/);
    return true;
  });
});
test('network failure never becomes a successful result', async () => {
  globalThis.fetch = async () => { throw new TypeError('Network failure'); };
  await assert.rejects(getTeacherAnalytics('', new AbortController().signal));
});
test('routing, URL reload, session expiry and Teacher-only navigation remain wired', async () => {
  const routes = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  const area = await readFile(new URL('../src/pages/RoleAreaPage.tsx', import.meta.url), 'utf8');
  const page = await readFile(new URL('../src/pages/TeacherAnalyticsPage.tsx', import.meta.url), 'utf8');
  assert.match(routes, /path="\/teacher\/analytics" element={<ProtectedRoute requiredRole="TEACHER"><TeacherAnalyticsPage/);
  assert.match(area, /role === 'TEACHER' && home && !loading && !error && <p><Link to="\/teacher\/analytics">Teacher Analytics/);
  assert.match(area, /to="\/teacher\/feedback">Course Feedback/); assert.match(area, /My Profile/);
  assert.match(page, /useSearchParams\(\)/); assert.match(page, /getTeacherAnalytics\(query, controller.signal\)/);
  assert.match(page, /key=\{`\$\{currentUser\?\.id\}:\$\{currentUser\?\.linkedTeacher\?\.id\}:\$\{query\}`\}/);
  assert.match(page, /AccountSessionExpired\) expireSession\(\)/); assert.match(page, /return \(\) => controller.abort\(\)/);
  assert.match(page, /Could not load teacher analytics. Please try again./);
  assert.doesNotMatch(page, /Gemini|summary|moderation|groupSection|studentEmail/);
});

