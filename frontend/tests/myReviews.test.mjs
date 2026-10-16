import { test, after, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router';

const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)), server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(() => server.close());
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
const { MyReviewsContent } = await server.ssrLoadModule('/src/pages/MyReviewsPage.tsx');
const { PublishedReviewForm } = await server.ssrLoadModule('/src/components/PublishedReviewEditor.tsx');
const { getOwnReviews, updatePublishedReview, correctReview } = await server.ssrLoadModule('/src/api/reviews.ts');
const published = { reviewId: 7, status: 'PUBLISHED', submittedAt: '2026-10-06T07:00:00Z',
  courseCode: 'ABC 100', courseName: 'Alpha Subject', teacherName: 'Alex Example',
  academicTerm: '2026-2027 1 term', rating: 4, moderatedAt: '2026-10-06T07:00:01Z', text: 'Current feedback', moderationReasons: [] };
const flagged = { ...published, reviewId: 8, status: 'NEEDS_CORRECTION', text: 'Flagged feedback',
  submittedAt: '2026-10-05T07:00:00Z', moderationReasons: [{ category: 'THREAT', message: 'Remove threatening language.' }] };
const submitted = { ...published, reviewId: 9, status: 'SUBMITTED', text: 'Awaiting feedback', moderatedAt: null, submittedAt: '2026-10-04T07:00:00Z' };
const defaults = { reviews: [published, flagged, submitted], loading: false, error: '', editing: null,
  onEdit() {}, onCancel() {}, onRefresh() {}, onSessionExpired() {} };
const render = (props = {}) => renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/student/reviews'] },
  createElement(MyReviewsContent, { ...defaults, ...props })));
function network(handler) {
  globalThis.fetch = async (url, options) => url === '/api/auth/csrf'
    ? { ok: true, json: async () => ({ headerName: 'X-CSRF-TOKEN', token: 'test-only' }) }
    : handler(url, options);
}
const receipt = { reviewId: 7, submitted: true, status: 'SUBMITTED' };

test('own history loads independently on every direct load/refresh with session, no client owner or drafts', async () => {
  const calls = [];
  network(async (url, options) => { calls.push({ url, options }); return { ok: true, status: 200, json: async () => defaults.reviews }; });
  for (let i = 0; i < 2; i++) {
    const data = await getOwnReviews(new AbortController().signal);
    const html = render({ reviews: data });
    assert.ok(html.indexOf('Current feedback') < html.indexOf('Flagged feedback'));
    assert.ok(html.indexOf('Flagged feedback') < html.indexOf('Awaiting feedback'));
    assert.match(html, /ABC 100/); assert.match(html, /Alpha Subject/); assert.match(html, /Alex Example/);
    assert.match(html, /2026-2027 1 term/); assert.match(html, /4 \/ 5/); assert.match(html, /Submitted:/);
  }
  assert.equal(calls.length, 2);
  for (const { url, options } of calls) {
    assert.equal(url, '/api/student/reviews'); assert.equal(options.credentials, 'same-origin');
    assert.equal(options.body, undefined); assert.ok(options.signal instanceof AbortSignal);
  }
});
test('loading, empty and failed states are distinct; failure retains existing review', () => {
  assert.match(render({ reviews: [], loading: true }), /Loading your reviews/);
  assert.doesNotMatch(render({ reviews: [], loading: true }), /haven&#x27;t/);
  assert.match(render({ reviews: [] }), /No reviews yet/);
  assert.match(render({ reviews: [] }), /\/student\/reviews\/new/);
  const html = render({ error: 'Could not load your reviews. Please try again.' });
  assert.match(html, /role="alert"/); assert.match(html, /Current feedback/); assert.match(html, /Refresh reviews/);
  assert.doesNotMatch(html, /Edit Review|Fix Feedback/);
});
test('Published offers editing; flagged offers existing US27 status route and friendly reasons; Submitted has no edit/fix', () => {
  assert.match(render({ reviews: [published] }), /Edit Review/);
  const flaggedHtml = render({ reviews: [flagged] });
  assert.match(flaggedHtml, /Remove threatening language/); assert.match(flaggedHtml, /href="\/student\/reviews\/8\/status"[^>]*>Fix Feedback/);
  assert.doesNotMatch(flaggedHtml, /THREAT|Edit Review|textarea/);
  const submittedHtml = render({ reviews: [submitted] });
  assert.match(submittedHtml, /awaiting moderation/); assert.doesNotMatch(submittedHtml, /Edit Review|Fix Feedback|textarea/);
});
test('Published editor preloads current rating/text, shares rating component and can cancel', () => {
  const html = render({ reviews: [published], editing: 7 });
  assert.match(html, /Current feedback<\/textarea>/); assert.match(html, /Your rating: 4 \/ 5/);
  assert.match(html, /maxLength="2000"/i); assert.match(html, /Save Changes/); assert.match(html, /Cancel/);
  assert.match(html, /moderation again/); assert.doesNotMatch(html, /courseOfferingId|authorId|Delete|Archive/);
});
const formProps = { reviewId: 7, draft: { rating: 4, text: 'Current feedback' }, pending: false, onChange() {}, onSubmit() {}, onCancel() {} };
// Traverse element props for the real controlled form callbacks, without a second implementation.
function elements(element) {
  if (!element || typeof element !== 'object') return [];
  return [element, ...[element.props?.children].flat(Infinity).flatMap(elements)];
}
test('rating/text controls emit changes and Cancel calls the supplied reset action', () => {
  const changes = []; let cancelled = false;
  const form = PublishedReviewForm({ ...formProps, onChange: next => changes.push(next), onCancel: () => { cancelled = true; } });
  const nodes = elements(form);
  nodes.find(n => n.type?.name === 'RatingInput').props.onChange(2);
  nodes.find(n => n.type === 'textarea').props.onChange({ target: { value: 'Changed text' } });
  nodes.find(n => n.type === 'button' && n.props.children === 'Cancel').props.onClick();
  assert.deepEqual(changes, [{ rating: 2, text: 'Current feedback' }, { rating: 4, text: 'Changed text' }]); assert.equal(cancelled, true);
});
for (const draft of [{ rating: null, text: 'Text' }, { rating: 6, text: 'Text' }, { rating: 4, text: ' \n ' }, { rating: 4, text: 'x'.repeat(2001) }]) {
  test(`invalid update prevented: rating=${draft.rating}, length=${draft.text.length}`, () => {
    let submitted = false;
    const form = PublishedReviewForm({ ...formProps, draft, onSubmit: () => { submitted = true; } });
    form.props.onSubmit({ preventDefault() {} }); assert.equal(submitted, false);
    assert.match(renderToStaticMarkup(form), /type="submit"[^>]*disabled/);
  });
}
test('pending disables all editing and prevents a second submit; 2000 chars accepted when idle', () => {
  let calls = 0;
  const form = PublishedReviewForm({ ...formProps, pending: true, onSubmit: () => calls++ });
  form.props.onSubmit({ preventDefault() {} }); assert.equal(calls, 0);
  assert.match(renderToStaticMarkup(form), /<fieldset disabled/); assert.match(renderToStaticMarkup(form), /Saving…/);
  const allowed = PublishedReviewForm({ ...formProps, draft: { rating: 1, text: 'x'.repeat(2000) }, onSubmit: () => calls++ });
  allowed.props.onSubmit({ preventDefault() {} }); assert.equal(calls, 1);
});
for (const status of ['PUBLISHED', 'NEEDS_CORRECTION', 'SUBMITTED']) test(`US28 PUT then server reload renders ${status} once with current content`, async () => {
  const calls = [];
  const next = { ...published, status, rating: 2, text: 'Updated content', moderationReasons: status === 'NEEDS_CORRECTION' ? flagged.moderationReasons : [] };
  network(async (url, options) => { calls.push({ url, options }); return { ok: true, status: 200, json: async () => options.method === 'PUT' ? receipt : [next] }; });
  await updatePublishedReview(7, 2, 'Updated content');
  const html = render({ reviews: await getOwnReviews(new AbortController().signal) });
  assert.equal(calls[0].url, '/api/student/reviews/7'); assert.equal(calls[0].options.method, 'PUT');
  assert.equal(calls[0].options.credentials, 'same-origin'); assert.equal(calls[0].options.headers['X-CSRF-TOKEN'], 'test-only');
  assert.deepEqual(JSON.parse(calls[0].options.body), { rating: 2, text: 'Updated content' });
  assert.equal(calls[1].url, '/api/student/reviews'); assert.equal((html.match(/<h2>/g) ?? []).length, 1);
  assert.match(html, /Updated content/); assert.match(html, /2 \/ 5/); assert.doesNotMatch(html, /Current feedback/);
  if (status === 'PUBLISHED') assert.match(html, /Edit Review/);
  if (status === 'NEEDS_CORRECTION') assert.match(html, /Fix Feedback/);
  if (status === 'SUBMITTED') assert.doesNotMatch(html, /Edit Review|Fix Feedback/);
});
test('US27 correction is visible when My Reviews is revisited', async () => {
  network(async (url, options) => ({ ok: true, status: 200, json: async () => options.method === 'PUT'
    ? { ...receipt, reviewId: 8 } : [{ ...flagged, status: 'PUBLISHED', text: 'Corrected content', moderationReasons: [] }] }));
  await correctReview(8, 'Corrected content');
  const html = render({ reviews: await getOwnReviews(new AbortController().signal) });
  assert.match(html, /Corrected content/); assert.doesNotMatch(html, /Flagged feedback|Fix Feedback/);
  assert.equal((html.match(/<h2>/g) ?? []).length, 1);
});
for (const status of [400, 401, 403, 404, 409, 500]) test(`update HTTP ${status}: safe failure, no success or mutation`, async () => {
  const original = structuredClone(published); let calls = 0;
  network(async () => { calls++; return { ok: false, status, json: async () => ({ message: 'private SQL data' }) }; });
  await assert.rejects(updatePublishedReview(7, 2, 'Changed'), error => !error.message.includes('private SQL'));
  assert.equal(calls, 1); assert.deepEqual(published, original); assert.match(render({ reviews: [published] }), /Current feedback/);
});
test('network errors and unexpected update receipt never imply success', async () => {
  network(async () => { throw new TypeError('Network failed'); });
  await assert.rejects(updatePublishedReview(7, 2, 'Changed'));
  network(async () => ({ ok: true, status: 200, json: async () => ({ ...receipt, reviewId: 999 }) }));
  await assert.rejects(updatePublishedReview(7, 2, 'Changed'), /could not be confirmed/);
});
test('history 401 expires session; other errors do not expose server internals', async () => {
  network(async () => ({ ok: false, status: 401 }));
  await assert.rejects(getOwnReviews(new AbortController().signal), /session has expired/);
  network(async () => ({ ok: false, status: 500, json: async () => ({ message: 'private SQL' }) }));
  await assert.rejects(getOwnReviews(new AbortController().signal), /Could not load your reviews/);
});
test('feedback is escaped; author identity, internal IDs, provider data and delete/archive controls never rendered', () => {
  const html = render({ reviews: [{ ...published, text: '<script>attack()</script>', authorId: 93222, email: 'secret@example.test', rawResponse: 'private-provider' }] });
  assert.match(html, /&lt;script&gt;/); assert.doesNotMatch(html, /<script>|93222|secret@example|private-provider|Delete|Archive/);
});
