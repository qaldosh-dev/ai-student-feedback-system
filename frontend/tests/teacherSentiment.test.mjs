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
const { SentimentContent } = await server.ssrLoadModule('/src/components/SentimentAnalysis.tsx');
const { getTeacherSentiment } = await server.ssrLoadModule('/src/api/teacherSentiment.ts');
const data = { courseId: 10, courseCode: 'ABC 100', courseName: 'Subject', academicTerm: '2026-2027 1 term',
  status: 'AVAILABLE', privacyThresholdMet: true, analysisAvailable: true, analyzedReviewCount: 10, overallSentiment: 'MOSTLY_POSITIVE',
  positiveCount: 7, neutralCount: 2, negativeCount: 1, positivePercentage: 70, neutralPercentage: 20, negativePercentage: 10 };
const props = { data, loading: false, onRetry() {} };
const render = (overrides = {}) => renderToStaticMarkup(createElement(SentimentContent, { ...props, ...overrides }));
function elements(node) {
  if (!node || typeof node !== 'object') return [];
  if (Array.isArray(node)) return node.flatMap(elements);
  return [node, ...elements(node.props?.children)];
}

test('aggregate section shows overall, all percentages and sample size', () => {
  const html = render();
  for (const text of ['Sentiment Analysis', 'Mostly Positive', 'Positive: 70%', 'Neutral: 20%', 'Negative: 10%', 'Based on 10 published reviews'])
    assert.ok(html.includes(text), text);
  assert.doesNotMatch(html, /<canvas|<svg|<select|<input|Review 1|topics|summary|trend/i);
});
for (const [value, label] of [['MOSTLY_NEUTRAL','Mostly Neutral'], ['MOSTLY_NEGATIVE','Mostly Negative'], ['MIXED','Mixed']])
  test(`${value} displays its defined label`, () => assert.ok(render({ data: { ...data, overallSentiment: value } }).includes(label)));
test('loading hides even a supplied previous-scope result', () => {
  const html = render({ loading: true });
  assert.match(html, /Analyzing published feedback/); assert.match(html, /role="status"/);
  assert.doesNotMatch(html, /Mostly Positive|70%|Based on 10/);
});
test('empty published feedback is not fake neutral or zero percentages', () => {
  const html = render({ data: { ...data, status: 'PRIVACY_PROTECTED', privacyThresholdMet: false, analysisAvailable: false } });
  assert.match(html, /Not enough published feedback to display analytics while protecting student privacy/);
  assert.doesNotMatch(html, /Mostly|%|Based on|Retry/);
});
test('unavailable or failed network response never retains successful result', () => {
  for (const result of [null, { ...data, status: 'UNAVAILABLE', analysisAvailable: false }]) {
    const html = render({ data: result });
    assert.match(html, /Sentiment analysis is temporarily unavailable/); assert.match(html, /Retry/);
    assert.doesNotMatch(html, /Mostly|%|Based on/);
  }
});
test('Retry triggers the reload action', () => {
  let calls = 0;
  const tree = SentimentContent({ ...props, data: null, onRetry: () => calls++ });
  elements(tree).find(node => node.type === 'button').props.onClick(); assert.equal(calls, 1);
});
test('individual labels/texts/identities and future analytics are not rendered', () => {
  const html = render({ data: { ...data, sentiments: ['NEGATIVE'], reviewId: 987654, reviewText: 'PRIVATE_TEXT',
    authorEmail: 'private@example.test', groupSection: '15-P', moderationReasons: ['THREAT'], topics: ['SECRET_TOPIC'], summary: 'SECRET_SUMMARY' } });
  assert.doesNotMatch(html, /NEGATIVE|987654|PRIVATE_TEXT|private@example|15-P|THREAT|SECRET_TOPIC|SECRET_SUMMARY/);
});
test('course/term changes and refresh request exact new scope without teacher identity', async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return { ok: true, status: 200, json: async () => data }; };
  const controller = new AbortController();
  for (const [id, term] of [[10,'term A'],[20,'term B'],[20,' term C '],[20,' term C ']])
    assert.deepEqual(await getTeacherSentiment(id, term, controller.signal), data);
  assert.equal(calls.length, 4);
  assert.equal(calls[0].url, '/api/teacher/analytics/sentiment?courseId=10&term=term+A');
  assert.equal(calls[1].url, '/api/teacher/analytics/sentiment?courseId=20&term=term+B');
  assert.equal(new URL(calls[2].url, 'http://localhost').searchParams.get('term'), ' term C ');
  assert.equal(calls[2].url, calls[3].url);
  for (const call of calls) { assert.equal(call.options.credentials, 'same-origin'); assert.equal(call.options.body, undefined); assert.doesNotMatch(call.url, /teacherId/); }
  controller.abort(); assert.ok(calls.every(call => call.options.signal.aborted));
});
test('401 expires session', async () => {
  globalThis.fetch = async () => ({ ok: false, status: 401 });
  await assert.rejects(getTeacherSentiment(10, 'term', new AbortController().signal), /session has expired/);
});
for (const status of [400,403,404,429,503]) test(`HTTP ${status} never exposes provider or backend body`, async () => {
  globalThis.fetch = async () => ({ ok: false, status, json: async () => ({ message: 'SECRET_KEY PRIVATE_TEXT' }) });
  await assert.rejects(getTeacherSentiment(10, 'term', new AbortController().signal), { message: 'Sentiment analysis is temporarily unavailable.' });
});
test('network error rejects instead of inventing neutral', async () => {
  globalThis.fetch = async () => { throw new Error('Network error'); };
  await assert.rejects(getTeacherSentiment(10, 'term', new AbortController().signal));
});
test('valid US31 selection mounts keyed sentiment; effects reload, cancel stale requests and retry', async () => {
  const page = await readFile(new URL('../src/pages/TeacherAnalyticsPage.tsx', import.meta.url), 'utf8');
  const section = await readFile(new URL('../src/components/SentimentAnalysis.tsx', import.meta.url), 'utf8');
  assert.match(page, /if \(loading \|\| error \|\| data\?\.selectedCourseId == null \|\| data.selectedAcademicTerm == null\) return null/);
  assert.match(page, /<SentimentAnalysis key=\{`\$\{data.selectedCourseId\}:\$\{data.selectedAcademicTerm\}`\}/);
  assert.match(page, /courseId=\{data.selectedCourseId\} term=\{data.selectedAcademicTerm\}/);
  assert.match(section, /getTeacherSentiment\(courseId, term, controller.signal\)/);
  assert.match(section, /\[courseId, term, attempt, expireSession, onPrivacyProtected\]/);
  assert.match(section, /return \(\) => controller.abort\(\)/);
  assert.match(section, /if \(controller.signal.aborted\) return;/);
  assert.match(section, /setLoading\(true\); setData\(null\); setAttempt/);
  assert.match(section, /AccountSessionExpired\) expireSession\(\)/);
});

