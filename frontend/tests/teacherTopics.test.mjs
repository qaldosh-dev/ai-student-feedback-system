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
const { TopicsContent } = await server.ssrLoadModule('/src/components/TopicsAnalysis.tsx');
const { getTeacherTopics } = await server.ssrLoadModule('/src/api/teacherTopics.ts');
const data = { courseId: 10, courseCode: 'ABC 100', courseName: 'Subject', academicTerm: 'term A',
  status: 'AVAILABLE', privacyThresholdMet: true, analysisAvailable: true, enoughRecurringFeedback: true, analyzedReviewCount: 4,
  topics: ['Clear explanations', 'Useful examples'], concerns: ['Short deadlines', 'Fast pacing'] };
const props = { data, loading: false, onRetry() {} };
const render = (overrides = {}) => renderToStaticMarkup(createElement(TopicsContent, { ...props, ...overrides }));
function elements(node) {
  if (!node || typeof node !== 'object') return [];
  if (Array.isArray(node)) return node.flatMap(elements);
  return [node, ...elements(node.props?.children)];
}

test('Common Topics and Common Concerns render separate short lists', () => {
  const html = render();
  for (const label of ['Common Topics', 'Common Concerns', ...data.topics, ...data.concerns]) assert.ok(html.includes(label));
  assert.equal((html.match(/<li>/g) ?? []).length, 4);
  assert.doesNotMatch(html, /<canvas|<svg|<select|<input|%|summary|sentiment/i);
});

test('loading hides all prior topics and concerns', () => {
  const html = render({ loading: true });
  assert.match(html, /Analyzing recurring themes/);
  for (const label of [...data.topics, ...data.concerns]) assert.ok(!html.includes(label));
});

test('zero published feedback shows specific empty message', () => {
  const html = render({ data: { ...data, status: 'PRIVACY_PROTECTED', privacyThresholdMet: false, analysisAvailable: false } });
  assert.match(html, /Not enough published feedback to display analytics while protecting student privacy/);
  assert.doesNotMatch(html, /Clear explanations|Short deadlines|Retry/);
});

test('privacy and eligible no-recurrence states remain distinct without invented labels', () => {
  for (const status of ['PRIVACY_PROTECTED', 'AVAILABLE']) {
    const html = render({ data: { ...data, status, privacyThresholdMet: status === 'AVAILABLE', enoughRecurringFeedback: false } });
    assert.match(html, status === 'AVAILABLE' ? /Not enough recurring feedback to identify common topics yet/ : /Analytics Privacy/);
    assert.doesNotMatch(html, /Clear explanations|Short deadlines|Retry/);
  }
});

test('unavailable states hide all previous results', () => {
  for (const result of [null, { ...data, status: 'UNAVAILABLE', analysisAvailable: false }]) {
    const html = render({ data: result });
    assert.match(html, /Topic analysis is temporarily unavailable/); assert.match(html, /Retry/);
    assert.doesNotMatch(html, /Clear explanations|Short deadlines/);
  }
});

test('Retry invokes reload callback', () => {
  let calls = 0;
  const tree = TopicsContent({ ...props, data: null, onRetry: () => calls++ });
  elements(tree).find(node => node.type === 'button').props.onClick(); assert.equal(calls, 1);
});

test('empty topic or concern category does not invent filler', () => {
  assert.match(render({ data: { ...data, concerns: [] } }), /No recurring concerns identified/);
  assert.match(render({ data: { ...data, topics: [] } }), /No recurring topics identified/);
});

test('no private fields, sentiment or summary rendered; labels are escaped', () => {
  const html = render({ data: { ...data, topics: ['<script>unsafe()</script>'],
    reviewText: 'PRIVATE_TEXT', authorEmail: 'private@example.test', reviewId: 987654, groupSection: '15-P',
    positivePercentage: 99, overallSentiment: 'MOSTLY_POSITIVE', summary: 'SECRET_SUMMARY' } });
  assert.doesNotMatch(html, /PRIVATE_TEXT|private@example|987654|15-P|99%|MOSTLY_POSITIVE|SECRET_SUMMARY|<script>/);
  assert.match(html, /&lt;script&gt;/);
});
test('course/term changes and refresh request exact new scope without teacher identity', async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return { ok: true, status: 200, json: async () => data }; };
  const controller = new AbortController();
  for (const [id, term] of [[10,'term A'],[20,'term B'],[20,' term C '],[20,' term C ']])
    assert.deepEqual(await getTeacherTopics(id, term, controller.signal), data);
  assert.equal(calls.length, 4);
  assert.equal(calls[0].url, '/api/teacher/analytics/topics?courseId=10&term=term+A');
  assert.equal(calls[1].url, '/api/teacher/analytics/topics?courseId=20&term=term+B');
  assert.equal(new URL(calls[2].url, 'http://localhost').searchParams.get('term'), ' term C ');
  assert.equal(calls[2].url, calls[3].url);
  for (const call of calls) { assert.equal(call.options.credentials, 'same-origin'); assert.equal(call.options.body, undefined); assert.doesNotMatch(call.url, /teacherId/); }
  controller.abort(); assert.ok(calls.every(call => call.options.signal.aborted));
});
test('401 expires session', async () => {
  globalThis.fetch = async () => ({ ok: false, status: 401 });
  await assert.rejects(getTeacherTopics(10, 'term', new AbortController().signal), /session has expired/);
});
for (const status of [400,403,404,429,503]) test(`HTTP ${status} never exposes provider or backend body`, async () => {
  globalThis.fetch = async () => ({ ok: false, status, json: async () => ({ message: 'SECRET_KEY PRIVATE_TEXT' }) });
  await assert.rejects(getTeacherTopics(10, 'term', new AbortController().signal), { message: 'Topic analysis is temporarily unavailable.' });
});
test('network error rejects instead of inventing topics', async () => {
  globalThis.fetch = async () => { throw new Error('Network error'); };
  await assert.rejects(getTeacherTopics(10, 'term', new AbortController().signal));
});
test('valid US31 selection mounts keyed topics; effects reload, cancel stale requests and retry', async () => {
  const page = await readFile(new URL('../src/pages/TeacherAnalyticsPage.tsx', import.meta.url), 'utf8');
  const section = await readFile(new URL('../src/components/TopicsAnalysis.tsx', import.meta.url), 'utf8');
  assert.match(page, /if \(loading \|\| error \|\| data\?\.selectedCourseId == null \|\| data.selectedAcademicTerm == null\) return null/);
  assert.match(page, /<TopicsAnalysis key=\{`\$\{data.selectedCourseId\}:\$\{data.selectedAcademicTerm\}`\}/);
  assert.match(page, /courseId=\{data.selectedCourseId\} term=\{data.selectedAcademicTerm\}/);
  assert.match(section, /getTeacherTopics\(courseId, term, controller.signal\)/);
  assert.match(section, /\[courseId, term, attempt, expireSession, onPrivacyProtected\]/);
  assert.match(section, /return \(\) => controller.abort\(\)/);
  assert.match(section, /if \(controller.signal.aborted\) return;/);
  assert.match(section, /setLoading\(true\); setData\(null\); setAttempt/);
  assert.match(section, /AccountSessionExpired\) expireSession\(\)/);
});



