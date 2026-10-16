import { test, after, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router';

const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)), server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(() => server.close());
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
const { ReviewRetryControls } = await server.ssrLoadModule('/src/components/ReviewModerationRetry.tsx');
const { ReviewStatusContent } = await server.ssrLoadModule('/src/pages/ReviewStatusPage.tsx');
const { MyReviewsContent } = await server.ssrLoadModule('/src/pages/MyReviewsPage.tsx');
const { retryReviewModeration, ModerationRetryCooldown, getReviewStatus } = await server.ssrLoadModule('/src/api/reviews.ts');
const data = { reviewId: 7, status: 'SUBMITTED', submittedAt: '2026-10-14T01:00:00Z', courseOfferingId: 100,
  courseCode: 'ABC 100', courseName: 'Subject', teacherName: 'Teacher', academicTerm: 'term', rating: 4,
  moderatedAt: null, moderationReasons: [], text: null, retryModerationAvailable: true, retryAfterSeconds: 0 };
const props = { status: 'SUBMITTED', remaining: 0, pending: false, error: '', onRetry() {}, onRefresh() {} };
const render = overrides => renderToStaticMarkup(createElement(ReviewRetryControls, { ...props, ...overrides }));
const statusHtml = result => renderToStaticMarkup(createElement(ReviewStatusContent, { data: result, loading: false, error: '', onRetry() {} }));
function network(handler) {
  globalThis.fetch = async (url, options) => url === '/api/auth/csrf'
    ? { ok: true, json: async () => ({ headerName: 'X-CSRF-TOKEN', token: 'test-only' }) } : handler(url, options);
}
function elements(node) {
  if (!node || typeof node !== 'object') return [];
  if (Array.isArray(node)) return node.flatMap(elements);
  return [node, ...elements(node.props?.children)];
}
test('only Submitted shows manual retry and click invokes supplied action', () => {
  assert.match(render(), /Retry Moderation/); assert.doesNotMatch(render(), /disabled/);
  for (const status of ['PUBLISHED','NEEDS_CORRECTION']) assert.equal(render({ status }), '');
  let count = 0;
  elements(ReviewRetryControls({ ...props, onRetry: () => count++ })).find(node => node.type === 'button').props.onClick();
  assert.equal(count, 1);
});
test('pending disables button and shows checking state', () => {
  const html = render({ pending: true }); assert.match(html, /Checking moderation/); assert.match(html, /disabled/);
});
test('cooldown disables retry; zero enables the UX button', () => {
  const html = render({ remaining: 42 });
  assert.match(html, /try again in 42 seconds/); assert.match(html, /disabled/);
  assert.doesNotMatch(render({ remaining: 0 }), /disabled|seconds/);
});
test('safe error requires refreshing authoritative status before another retry', () => {
  const html = render({ error: 'Moderation retry could not be confirmed. Refresh the status before trying again.' });
  assert.match(html, /role="alert"/); assert.match(html, /Refresh status/); assert.match(html, /disabled/);
});
test('POST sends CSRF and session with no client data; only same Review response accepted', async () => {
  const calls = [];
  network(async (url, options) => { calls.push({ url, options }); return { ok: true, status: 200, json: async () => data }; });
  assert.deepEqual(await retryReviewModeration(7), data);
  assert.equal(calls.length, 1); assert.equal(calls[0].url, '/api/student/reviews/7/moderation/retry');
  assert.equal(calls[0].options.method, 'POST'); assert.equal(calls[0].options.credentials, 'same-origin');
  assert.equal(calls[0].options.headers['X-CSRF-TOKEN'], 'test-only'); assert.equal(calls[0].options.body, undefined);
  network(async () => ({ ok: true, status: 200, json: async () => ({ ...data, reviewId: 8 }) }));
  await assert.rejects(retryReviewModeration(7), /could not be confirmed/);
});
for (const status of ['PUBLISHED','NEEDS_CORRECTION','SUBMITTED']) test(`server ${status} updates same status card without duplicate`, async () => {
  const result = { ...data, status, retryModerationAvailable: false, retryAfterSeconds: status === 'SUBMITTED' ? 52 : 0,
    text: status === 'NEEDS_CORRECTION' ? 'Feedback to correct' : null };
  network(async () => ({ ok: true, status: 200, json: async () => result }));
  const returned = await retryReviewModeration(7); const html = statusHtml(returned);
  assert.equal(returned.reviewId, 7); assert.equal((html.match(/aria-label="Review submission"/g) ?? []).length, 1);
  if (status === 'SUBMITTED') { assert.match(html, /Retry Moderation/); assert.match(html, /52 seconds/); }
  else assert.doesNotMatch(html, /Retry Moderation/);
  if (status === 'NEEDS_CORRECTION') assert.match(html, /Edit Feedback/);
  if (status === 'PUBLISHED') assert.match(html, /passed moderation/);
});
test('429 uses only server wait value, not untrusted provider message', async () => {
  network(async () => ({ ok: false, status: 429, json: async () => ({ retryAfterSeconds: 42, message: 'Gemini 503 SECRET_KEY' }) }));
  await assert.rejects(retryReviewModeration(7), error => {
    assert.ok(error instanceof ModerationRetryCooldown); assert.equal(error.retryAfterSeconds, 42);
    assert.doesNotMatch(error.message, /Gemini|503|SECRET/); return true;
  });
});
test('refresh obtains backend cooldown independently of local countdown', async () => {
  let count = 0;
  network(async () => { count++; return { ok: true, status: 200, json: async () => ({ ...data, retryAfterSeconds: 40, retryModerationAvailable: false }) }; });
  for (let index = 0; index < 2; index++) assert.match(statusHtml(await getReviewStatus('7', new AbortController().signal)), /40 seconds/);
  assert.equal(count, 2);
});
for (const status of [400,403,404,409,503]) test(`HTTP ${status} has safe error`, async () => {
  network(async () => ({ ok: false, status, json: async () => ({ message: 'Gemini PRIVATE_TEXT SECRET_KEY' }) }));
  await assert.rejects(retryReviewModeration(7), error => !/Gemini|PRIVATE|SECRET/.test(error.message));
});
test('401 retains existing expired-session behavior', async () => {
  network(async () => ({ ok: false, status: 401 }));
  await assert.rejects(retryReviewModeration(7), /session has expired/);
});
test('My Reviews uses one status link, preserving edit and correction actions', () => {
  const reviews = ['SUBMITTED','PUBLISHED','NEEDS_CORRECTION'].map((status,index) => ({ ...data, reviewId: 7 + index, status, text: 'Feedback' }));
  const html = renderToStaticMarkup(createElement(MemoryRouter, {}, createElement(MyReviewsContent,
    { reviews, loading: false, error: '', editing: null, onEdit() {}, onCancel() {}, onRefresh() {}, onSessionExpired() {} })));
  assert.match(html, /href="\/student\/reviews\/7\/status"[^>]*>View Status/);
  assert.equal((html.match(/href="\/student\/reviews\/7\/status"/g) ?? []).length, 1);
  assert.doesNotMatch(html, /Retry Moderation/); assert.match(html, /Edit Review/); assert.match(html, /Fix Feedback/);
});
test('pending guard, authoritative response, safe network error and timer cleanup are wired', async () => {
  const source = await readFile(new URL('../src/components/ReviewModerationRetry.tsx', import.meta.url), 'utf8');
  const page = await readFile(new URL('../src/pages/ReviewStatusPage.tsx', import.meta.url), 'utf8');
  assert.match(source, /if \(busy.current \|\| remaining > 0\) return/);
  assert.match(source, /retryReviewModeration\(review.reviewId\)/); assert.match(source, /onUpdated\(result\)/);
  assert.match(source, /Math.max\(0, Math.ceil\(\(deadline - Date.now\(\)\) \/ 1000\)\)/);
  assert.match(source, /window.clearInterval\(timer\)/); assert.match(source, /setRemaining\(failure.retryAfterSeconds\)/);
  assert.doesNotMatch(source, /setError\(failure.message\)|setInterval\([^;\n]*retryReviewModeration/);
  assert.match(page, /onUpdated=\{setData\}/);
});
