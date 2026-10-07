import { test, after, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)), server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(() => server.close());
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
const { ReviewStatusContent } = await server.ssrLoadModule('/src/pages/ReviewStatusPage.tsx');
const { getReviewStatus } = await server.ssrLoadModule('/src/api/reviews.ts');
const data = { reviewId: 7, status: 'SUBMITTED', submittedAt: '2026-09-28T07:00:00Z', courseOfferingId: 100,
  courseCode: 'ABC 100', courseName: 'Alpha Subject', teacherName: 'Alex Example', academicTerm: '2026-2027 1 term', rating: 4,
  moderatedAt: null, moderationReasons: [], text: null };
const render = (props = {}) => renderToStaticMarkup(createElement(ReviewStatusContent, { data, loading: false, error: '', onRetry() {}, ...props }));

test('Submitted remains awaiting moderation and never implies publication', () => {
  const html = render();
  for (const text of ['Your review was submitted successfully.', 'ABC 100', 'Alpha Subject', 'Alex Example', '2026-2027 1 term', '4 / 5', 'Submission Status:', 'Submitted']) assert.ok(html.includes(text));
  assert.doesNotMatch(html, /Published|Approved|AI checked|Edit|Correct|My Reviews|history|<form|textarea|button/);
  assert.match(html, /awaiting moderation/);
});
test('Published shows successful moderation without correction controls', () => {
  const html = render({ data: { ...data, status: 'PUBLISHED', moderatedAt: '2026-10-06T01:00:00Z' } });
  assert.match(html, /Published/); assert.match(html, /passed moderation/);
  assert.doesNotMatch(html, /awaiting moderation|<form|textarea|button/);
});
test('Needs correction displays backend-defined reasons and Edit Feedback, without provider internals', () => {
  const html = render({ data: { ...data, status: 'NEEDS_CORRECTION', text: 'Flagged feedback', moderationReasons: [
    { category: 'THREAT', message: 'Remove threatening language.' },
    { category: 'PERSONAL_INSULT', message: 'Remove personal insults and focus on the learning experience.' },
  ], rawResponse: 'private provider payload', email: 'private@example.test' } });
  assert.match(html, /Needs correction/); assert.match(html, /Remove threatening language/); assert.match(html, /Remove personal insults/);
  assert.match(html, /Edit Feedback/);
  assert.doesNotMatch(html, /private provider|private@example|<form|textarea/);
});
test('only safe explicit DTO fields are rendered even if extra identity data arrives', () => {
  const html = render({ data: { ...data, authorId: 123456, email: 'private@example.test', author: { name: 'Private Person' }, text: 'Private feedback' } });
  assert.doesNotMatch(html, /123456|private@example|Private Person|Private feedback/);
});
test('loading hides old data', () => {
  const html = render({ loading: true });
  assert.match(html, /role="status"/); assert.match(html, /Loading review status/); assert.doesNotMatch(html, /Alpha Subject/);
});
test('missing/inaccessible status uses the same safe not-found view', () => {
  const html = render({ data: null });
  assert.match(html, /Review not found or unavailable/); assert.doesNotMatch(html, /Alex Example|Submitted/);
});
test('network errors expose retry without stale review information', () => {
  const html = render({ error: 'Could not load review status. Please try again.' });
  assert.match(html, /role="alert"/); assert.match(html, /Retry/); assert.doesNotMatch(html, /Alex Example/);
});
test('each status read independently fetches the route ID with session and cancellation, no draft/navigation state', async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return { ok: true, status: 200, json: async () => data }; };
  for (let i = 0; i < 2; i++) assert.deepEqual(await getReviewStatus('7', new AbortController().signal), data);
  assert.equal(calls.length, 2);
  for (const { url, options } of calls) {
    assert.equal(url, '/api/student/reviews/7/status'); assert.equal(options.credentials, 'same-origin');
    assert.ok(options.signal instanceof AbortSignal); assert.equal(options.body, undefined);
  }
});
test('404 has no data and 401 expires authentication', async () => {
  globalThis.fetch = async () => ({ ok: false, status: 404 });
  assert.equal(await getReviewStatus('7', new AbortController().signal), null);
  globalThis.fetch = async () => ({ ok: false, status: 401 });
  await assert.rejects(getReviewStatus('7', new AbortController().signal), /session has expired/);
});
for (const status of [403, 500]) test(`HTTP ${status} exposes safe message only`, async () => {
  globalThis.fetch = async () => ({ ok: false, status, json: async () => ({ message: 'SQL private detail' }) });
  await assert.rejects(getReviewStatus('7', new AbortController().signal), error => !error.message.includes('SQL'));
});
