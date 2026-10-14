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
const { OverallRatingContent } = await server.ssrLoadModule('/src/components/OverallRatingStats.tsx');
const { getOverallRating } = await server.ssrLoadModule('/src/api/overallRating.ts');
const { default: TeacherProfilePage, TeacherProfileContent } = await server.ssrLoadModule('/src/pages/TeacherProfilePage.tsx');
const { default: TeachersSection } = await server.ssrLoadModule('/src/components/TeachersSection.tsx');
const data = { teacherId: 7, teacherName: 'Alex Person', averageRating: 14 / 3, publishedReviewCount: 3,
  ratingDistribution: { 5: 2, 4: 1, 3: 0, 2: 0, 1: 0 } };
const render = (props = {}) => renderToStaticMarkup(createElement(OverallRatingContent, { data, loading: false, error: '', onRetry() {}, ...props }));
const profile = { id: 7, name: 'Alex Person', courseOfferings: [{ courseId: 8, courseCode: 'ABC 100', courseName: 'Subject', academicTerm: 'Fall 2026' }] };
const renderProfile = (props = {}) => renderToStaticMarkup(createElement(MemoryRouter, null,
  createElement(TeacherProfileContent, { profile, loading: false, error: '', onRetry() {}, ...props })));

test('profile has Overall Rating before unchanged course list and public navigation', () => {
  const html = renderProfile();
  assert.match(html, /Overall Rating/); assert.match(html, /Loading overall rating/);
  assert.ok(html.indexOf('Overall Rating') < html.indexOf('Courses taught'));
  assert.match(html, /ABC 100/); assert.match(html, /Subject/); assert.match(html, /Fall 2026/);
  assert.match(html, /href="\/courses\/8"[^>]*>View Course/);
  assert.match(html, /href="\/teachers\/7\/courses\/8\/feedback\?term=Fall\+2026"/);
});
test('numeric mean displays one decimal and total published count', () => {
  assert.match(render(), /4\.7 \/ 5/); assert.match(render(), /Based on 3 published reviews/);
  assert.match(render({ data: { ...data, averageRating: 4 } }), /4\.0 \/ 5/);
  assert.match(render({ data: { ...data, publishedReviewCount: 1 } }), /Based on 1 published review<\/p>/);
  assert.match(render(), /Across all courses and academic terms/);
});
test('distribution shows all five buckets in descending order including zero counts', () => {
  const html = render(); let previous = -1;
  for (const rating of [5, 4, 3, 2, 1]) {
    const index = html.indexOf(`${rating}★ — ${data.ratingDistribution[rating]}`);
    assert.ok(index > previous); previous = index;
  }
  assert.equal((html.match(/<li>/g) ?? []).length, 5);
});
test('empty state never fabricates zero rating or shows a distribution', () => {
  const html = render({ data: { ...data, averageRating: null, publishedReviewCount: 0, ratingDistribution: { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 } } });
  assert.match(html, /No ratings yet/); assert.match(html, /0 published reviews/);
  assert.doesNotMatch(html, /0\.0|\/ 5|Rating distribution|★/);
});
test('loading, failure and not-found hide stale stats, failure offers retry', () => {
  assert.match(render({ loading: true }), /role="status"/);
  assert.doesNotMatch(render({ loading: true }), /4\.7|Based on|★/);
  const error = 'Could not load overall rating. Please try again.';
  assert.match(render({ error }), /role="alert"/); assert.doesNotMatch(render({ error }), /4\.7|Based on|★/);
  let count = 0;
  OverallRatingContent({ data, loading: false, error, onRetry: () => count++ }).props.children[1].props.onClick();
  assert.equal(count, 1); assert.match(render({ data: null }), /Teacher not found/);
  assert.doesNotMatch(renderProfile({ profile: null }), /Overall Rating|ABC 100/);
});
test('only aggregate values render, never review text, student, source or moderation data', () => {
  const html = render({ data: { ...data, authorId: 98765, reviewId: 87654, email: 'private@example.test',
    text: 'Secret review', groupSection: '15-P', academicTerm: 'Private term', moderation: 'THREAT', provider: 'Gemini',
    summary: 'Secret summary', teacherName: '<script>attack()</script>' } });
  assert.doesNotMatch(html, /98765|87654|private@example|Secret|15-P|Private term|THREAT|Gemini|script/);
  assert.doesNotMatch(html, /<svg|<canvas|<select|<input|trend|increased|decreased|percent|%/i);
});
test('direct teacher route remains public and initializes loading on fresh navigation', () => {
  const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/teachers/7'] },
    createElement(Routes, null, createElement(Route, { path: '/teachers/:teacherId', element: createElement(TeacherProfilePage) }))));
  assert.match(html, /Teacher Profile/); assert.match(html, /Loading teacher profile/); assert.match(html, /Back to Browse/);
});
test('API fetches fresh public stats for repeated loads with no course/term filters or body', async () => {
  const calls = []; const controller = new AbortController();
  globalThis.fetch = async (url, config) => { calls.push({ url, config }); return { ok: true, json: async () => ({ ...data, publishedReviewCount: calls.length }) }; };
  assert.equal((await getOverallRating('7', controller.signal)).publishedReviewCount, 1);
  assert.equal((await getOverallRating('7', controller.signal)).publishedReviewCount, 2);
  for (const { url, config } of calls) { assert.equal(url, '/api/teachers/7/ratings/overall'); assert.equal(config.body, undefined); }
  controller.abort(); assert.ok(calls.every(c => c.config.signal.aborted));
});
test('404 and malformed identity are safe, invalid IDs do not trigger a request', async () => {
  globalThis.fetch = async () => ({ status: 404, ok: false });
  assert.equal(await getOverallRating('999999', new AbortController().signal), null);
  globalThis.fetch = async () => { assert.fail('Must not fetch invalid teacher ID'); };
  for (const id of ['0', '-1', 'abc', '1/private', '']) assert.equal(await getOverallRating(id, new AbortController().signal), null);
});
test('server and network failures cannot render raw backend errors or fabricated success', async () => {
  globalThis.fetch = async () => ({ status: 500, ok: false, json: async () => ({ message: 'Private SQL' }) });
  await assert.rejects(getOverallRating('7', new AbortController().signal), { message: 'Could not load overall rating. Please try again.' });
  globalThis.fetch = async () => { throw new TypeError('Network failed'); };
  await assert.rejects(getOverallRating('7', new AbortController().signal));
});
test('browse cards remain free of overall stats', () => {
  const html = renderToStaticMarkup(createElement(MemoryRouter, null, createElement(TeachersSection, {
    teachers: [{ id: 7, name: 'Alex Person', department: null, averageRating: 4.7, publishedReviewCount: 555 }],
    query: '', sort: 'RATING_DESC', hasActiveSearch: false, loading: false, error: false, onSortChange() {}, onQueryChange() {}, onRetry() {},
  })));
  assert.doesNotMatch(html, /Overall Rating|4\.7|555|★|Based on/); assert.match(html, /View Profile/);
});
test('stats fetch lifecycle cancels old requests and retries independently of courses', async () => {
  const source = await readFile(new URL('../src/components/OverallRatingStats.tsx', import.meta.url), 'utf8');
  const page = await readFile(new URL('../src/pages/TeacherProfilePage.tsx', import.meta.url), 'utf8');
  assert.match(source, /getOverallRating\(String\(teacherId\), controller.signal\)/);
  assert.match(source, /\[teacherId, attempt\]/); assert.match(source, /return \(\) => controller.abort\(\)/);
  assert.match(source, /if \(!controller.signal.aborted\) setData\(result\)/);
  assert.match(page, /<OverallRatingStats key={profile.id} teacherId={profile.id} \/>/);
  assert.match(page, /<Profile key={teacherId} teacherId={teacherId} \/>/);
});
