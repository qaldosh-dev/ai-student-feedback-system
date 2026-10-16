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
const { ReviewCorrectionForm, validCorrection } = await server.ssrLoadModule('/src/components/ReviewCorrectionEditor.tsx');
const { correctReview, getReviewStatus } = await server.ssrLoadModule('/src/api/reviews.ts');
const flagged = { reviewId: 7, status: 'NEEDS_CORRECTION', submittedAt: '2026-10-06T07:00:00Z',
  courseOfferingId: 100, courseCode: 'ABC 100', courseName: 'Alpha Subject', teacherName: 'Alex Example',
  academicTerm: '2026-2027 1 term', rating: 2, moderatedAt: '2026-10-06T07:00:01Z',
  text: 'Existing flagged feedback', moderationReasons: [{ category: 'PERSONAL_INSULT', message: 'Remove personal insults.' }] };
const render = data => renderToStaticMarkup(createElement(ReviewStatusContent, { data, loading: false, error: '', onRetry() {} }));
const form = (text = flagged.text, pending = false) => ReviewCorrectionForm({ text, pending, onTextChange() {}, onSubmit() {} });
function network(handler) {
  globalThis.fetch = async (url, options) => url === '/api/auth/csrf'
    ? { ok: true, json: async () => ({ headerName: 'X-CSRF-TOKEN', token: 'test-only' }) }
    : handler(url, options);
}
const receipt = { reviewId: 7, submitted: true, status: 'SUBMITTED' };

test('flagged status shows reasons before editing and offers Edit Feedback only', () => {
  const html = render(flagged);
  assert.match(html, /Needs Correction/); assert.match(html, /Remove personal insults/); assert.match(html, /Edit Feedback/);
  assert.doesNotMatch(html, /textarea|Submit Review|type="radio"|My Reviews/);
});
test('editor preloads existing feedback with count and no rating or target inputs', () => {
  const html = renderToStaticMarkup(form());
  assert.match(html, /Existing flagged feedback<\/textarea>/);
  assert.ok(html.includes(`${flagged.text.length} / 2000`)); assert.match(html, /maxLength="2000"/i);
  assert.match(html, /Submit Corrected Feedback/);
  assert.doesNotMatch(html, /type="radio"|name="rating"|courseOfferingId|authorId/);
});
for (const text of ['', ' \n\t ', '\u00a0', 'x'.repeat(2001)]) test(`invalid text disables correction (${text.length} chars)`, () => {
  assert.equal(validCorrection(text), false);
  assert.match(renderToStaticMarkup(form(text)), /<button[^>]*disabled/);
});
test('2000 characters allowed with no word limit', () => {
  assert.equal(validCorrection('x'.repeat(2000)), true);
  const html = renderToStaticMarkup(form('x'.repeat(2000)));
  assert.match(html, /2000 \/ 2000/); assert.doesNotMatch(html, /disabled/);
});
test('pending state disables textarea and submit; form ignores another submission', () => {
  const html = renderToStaticMarkup(form(flagged.text, true));
  assert.match(html, /<textarea[^>]*disabled/); assert.match(html, /<button[^>]*disabled/); assert.match(html, /Submitting…/);
  let calls = 0;
  const element = ReviewCorrectionForm({ text: 'Corrected', pending: true, onTextChange() {}, onSubmit() { calls++; } });
  element.props.onSubmit({ preventDefault() {} }); assert.equal(calls, 0);
});
for (const status of ['SUBMITTED', 'PUBLISHED']) test(`${status} has no correction controls even with stale text`, () => {
  const html = render({ ...flagged, status });
  assert.doesNotMatch(html, /Edit Feedback|textarea|Submit Corrected Feedback/);
});
for (const status of ['PUBLISHED', 'NEEDS_CORRECTION', 'SUBMITTED']) test(`after correction fresh GET and refresh show ${status}`, async () => {
  const calls = [];
  const next = { ...flagged, status, text: status === 'NEEDS_CORRECTION' ? 'Corrected text' : null,
    moderationReasons: status === 'NEEDS_CORRECTION' ? [{ category: 'SPAM_OR_IRRELEVANT', message: 'Keep feedback relevant.' }] : [] };
  network(async (url, options) => {
    calls.push({ url, options });
    return { ok: true, status: 200, json: async () => options.method === 'PUT' ? receipt : next };
  });
  await correctReview(7, 'Corrected text');
  assert.equal(calls[0].url, '/api/student/reviews/7/correction');
  assert.equal(calls[0].options.method, 'PUT'); assert.equal(calls[0].options.credentials, 'same-origin');
  assert.equal(calls[0].options.headers['X-CSRF-TOKEN'], 'test-only');
  assert.deepEqual(JSON.parse(calls[0].options.body), { text: 'Corrected text' });
  for (let refresh = 0; refresh < 2; refresh++) {
    const fresh = await getReviewStatus('7', new AbortController().signal);
    const html = render(fresh);
    if (status === 'NEEDS_CORRECTION') {
      assert.match(html, /Keep feedback relevant/); assert.match(html, /Edit Feedback/); assert.doesNotMatch(html, /Remove personal insults/);
      assert.equal(fresh.text, 'Corrected text');
    } else {
      assert.doesNotMatch(html, /Edit Feedback|Remove personal insults/);
      assert.match(html, status === 'PUBLISHED' ? /passed moderation/ : /awaiting moderation/);
    }
  }
  assert.equal(calls.filter(call => call.options.method === 'PUT').length, 1);
  assert.equal(calls.length, 3);
});
for (const status of [400, 401, 403, 404, 409, 500]) test(`correction HTTP ${status} has safe error and no automatic retry`, async () => {
  let calls = 0;
  network(async () => { calls++; return { ok: false, status, json: async () => ({ message: 'private SQL/provider data' }) }; });
  await assert.rejects(correctReview(7, 'Corrected'), error => !error.message.includes('private SQL'));
  assert.equal(calls, 1);
});
test('unconfirmed correction response is not treated as success', async () => {
  network(async () => ({ ok: true, status: 200, json: async () => ({ ...receipt, reviewId: 8 }) }));
  await assert.rejects(correctReview(7, 'Corrected'), /could not be confirmed/);
});
test('own flagged text is escaped and author/provider fields never rendered', () => {
  const html = renderToStaticMarkup(form('<script>unsafe()</script>'));
  assert.doesNotMatch(html, /<script>/); assert.match(html, /&lt;script&gt;/);
  assert.doesNotMatch(render({ ...flagged, email: 'secret@example.test', author: { name: 'Private Person' }, apiKey: 'private-key' }), /secret@example|Private Person|private-key/);
});
