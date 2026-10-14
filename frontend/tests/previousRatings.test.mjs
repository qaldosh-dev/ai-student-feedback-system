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
const { default: PreviousRatingsPage, PreviousRatingsContent } = await server.ssrLoadModule('/src/pages/PreviousRatingsPage.tsx');
const { getPreviousRatings } = await server.ssrLoadModule('/src/api/previousRatings.ts');
const { PublicFeedbackContent } = await server.ssrLoadModule('/src/pages/PublicFeedbackPage.tsx');
const data = { teacherId: 7, teacherName: 'Alex Person', courseId: 10, courseCode: 'ABC 100', courseName: 'Alpha Subject',
  ratings: [{ courseOfferingId: 100, academicTerm: 'Fall 2026', averageRating: 4, publishedReviewCount: 2 },
    { courseOfferingId: 100, academicTerm: '2026-2027 1 term', averageRating: 13 / 3, publishedReviewCount: 3 },
    { courseOfferingId: 101, academicTerm: '2026-2027 1 term', averageRating: 1, publishedReviewCount: 1 },
    { courseOfferingId: 100, academicTerm: '2025-2026 2 term', averageRating: 4.6666666667, publishedReviewCount: 3 }] };
const routed = element => renderToStaticMarkup(createElement(MemoryRouter, null, element));
const render = (props = {}) => routed(createElement(PreviousRatingsContent, { data, loading: false, error: '', onRetry() {}, ...props }));

test('history renders teacher/course, separate terms, one decimal means and matching published counts', () => {
  const html = render();
  for (const text of ['Alex Person', 'ABC 100', 'Alpha Subject', 'Fall 2026', '2026-2027 1 term', '2025-2026 2 term',
    '4.0 / 5', '4.3 / 5', '4.7 / 5', '1.0 / 5', 'Based on 3 published reviews', 'Based on 1 published review']) assert.ok(html.includes(text));
  assert.equal((html.match(/<li /g) ?? []).length, 4);
  assert.match(html, /href="\/teachers\/7"/); assert.match(html, /href="\/courses\/10"/);
  assert.match(html, /href="\/teachers\/7\/courses\/10\/feedback"/);
});
test('multiple source offerings in the same term remain visibly separate', () => {
  const html = render();
  assert.equal((html.match(/<h3>2026-2027 1 term<\/h3>/g) ?? []).length, 2);
  assert.match(html, /Course offering 100/); assert.match(html, /Course offering 101/);
  assert.match(html, /Separate course offerings are not combined/);
});
test('single-offering history avoids unnecessary repeated identity labels', () => {
  const html = render({ data: { ...data, ratings: data.ratings.filter(row => row.courseOfferingId === 100) } });
  assert.doesNotMatch(html, /Course offering 100/); assert.match(html, /4.3 \/ 5/);
});
test('backend text ordering is preserved without chronological claims', () => {
  const html = render();
  assert.ok(html.indexOf('Fall 2026') < html.indexOf('2026-2027 1 term'));
  assert.ok(html.indexOf('2026-2027 1 term') < html.indexOf('2025-2026 2 term'));
  assert.match(html, /in text order/); assert.doesNotMatch(html, /Most recent|Latest term|improved|decreased/);
});
test('no published ratings yields neutral empty state, never zero rating or private statuses', () => {
  const html = render({ data: { ...data, ratings: [] } });
  assert.match(html, /No published ratings yet/); assert.match(html, /Alex Person/);
  assert.doesNotMatch(html, /0\.0|Average rating|SUBMITTED|NEEDS_CORRECTION|pending|moderation/);
});
test('loading and error clear prior results and retry reloads', () => {
  assert.match(render({ loading: true }), /Loading previous ratings/);
  assert.doesNotMatch(render({ loading: true }), /Alex Person|4\.3/);
  const html = render({ error: 'Could not load previous ratings. Please try again.' });
  assert.match(html, /role="alert"/); assert.match(html, /Retry/); assert.doesNotMatch(html, /Alex Person|4\.3/);
  let attempts = 0;
  PreviousRatingsContent({ data, loading: false, error: 'Could not load', onRetry: () => attempts++ }).props.children[1].props.onClick();
  assert.equal(attempts, 1);
});
test('invalid relationship has a safe not-found state', () => assert.match(render({ data: null }), /Rating history not found/));
test('history does not render review texts, authors, internal data, all-time stats or actions', () => {
  const html = render({ data: { ...data, teacherName: '<script>unsafe()</script>', overallRating: 4.9, reviews: [{ text: 'Private review' }],
    feedback: [{ text: 'Published review text' }], ratings: [{ ...data.ratings[0], text: 'Private review', authorId: 98765,
      email: 'private@example.test', studentName: 'Private Student', groupSection: '15-P', categories: ['THREAT'], rawResponse: 'provider payload' }] } });
  assert.match(html, /&lt;script&gt;/);
  assert.doesNotMatch(html, /<script>|Private review|Published review text|98765|private@example|Private Student|15-P|THREAT|provider payload|4\.9/);
  assert.doesNotMatch(html, /overall|all-time|distribution|<canvas|<svg|percentage|sentiment|summary|Edit|Delete|Approve|Reject|<input|<button|<select/i);
});
test('direct route initializes without login or navigation state', () => {
  const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/teachers/7/courses/10/ratings/history'] },
    createElement(Routes, null, createElement(Route, { path: '/teachers/:teacherId/courses/:courseId/ratings/history', element: createElement(PreviousRatingsPage) }))));
  assert.match(html, /Previous Ratings/); assert.match(html, /Loading previous ratings/);
});
test('direct load and refresh independently fetch exact pair, without extra filters; cancellation survives', async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return { ok: true, status: 200, json: async () => data }; };
  const controller = new AbortController();
  for (let i = 0; i < 2; i++) assert.deepEqual(await getPreviousRatings('7', '10', controller.signal), data);
  assert.equal(calls.length, 2);
  for (const call of calls) {
    assert.equal(call.url, '/api/teachers/7/courses/10/ratings/history'); assert.equal(call.options.body, undefined);
  }
  controller.abort(); assert.ok(calls.every(call => call.options.signal.aborted));
});
test('US10 Previous Ratings link carries only teacher/course identity', () => {
  const html = routed(createElement(PublicFeedbackContent, { data: { ...data, courseOfferingId: 100, academicTerm: 'Fall 2026',
    availableTargets: [{ courseOfferingId: 100, academicTerm: 'Fall 2026' }], feedback: [] }, loading: false, error: '', onRetry() {} }));
  assert.match(html, /href="\/teachers\/7\/courses\/10\/ratings\/history"[^>]*>Previous Ratings/);
});
test('404 is neutral; errors do not expose backend details; invalid IDs are never fetched', async () => {
  globalThis.fetch = async () => ({ ok: false, status: 404 });
  assert.equal(await getPreviousRatings('7', '10', new AbortController().signal), null);
  globalThis.fetch = async () => ({ ok: false, status: 500, json: async () => ({ message: 'Private SQL' }) });
  await assert.rejects(getPreviousRatings('7', '10', new AbortController().signal), { message: 'Could not load previous ratings. Please try again.' });
  globalThis.fetch = async () => { assert.fail('Invalid identity must not be fetched'); };
  for (const pair of [['abc', '10'], ['7', '0'], ['7', '../secret']]) assert.equal(await getPreviousRatings(...pair, new AbortController().signal), null);
});
test('network failure cannot become successful history', async () => {
  globalThis.fetch = async () => { throw new TypeError('Connection failed'); };
  await assert.rejects(getPreviousRatings('7', '10', new AbortController().signal));
});
test('production history route is public and US10 remains its separate page', async () => {
  const routes = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  assert.match(routes, /path="\/teachers\/:teacherId\/courses\/:courseId\/ratings\/history" element={<PreviousRatingsPage \/>}/);
  assert.match(routes, /path="\/teachers\/:teacherId\/courses\/:courseId\/feedback" element={<PublicFeedbackPage \/>}/);
});
