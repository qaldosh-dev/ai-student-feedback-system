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
const { default: TeachersSection } = await server.ssrLoadModule('/src/components/TeachersSection.tsx');
const { getTeachers } = await server.ssrLoadModule('/src/api/catalog.ts');
const options = [
  ['NAME_ASC', 'Name A–Z'], ['NAME_DESC', 'Name Z–A'], ['RATING_DESC', 'Highest rated'],
  ['RATING_ASC', 'Lowest rated'], ['REVIEW_COUNT_DESC', 'Most reviewed'], ['HAS_FEEDBACK_FIRST', 'Has published feedback first'],
];
const teachers = [{ id: 8, name: 'Zeta Person', department: null }, { id: 7, name: 'Alpha Person', department: 'Science' }];
const base = { teachers, loading: false, error: false, query: 'Person', hasActiveSearch: true,
  sort: 'NAME_ASC', onSortChange() {}, onQueryChange() {}, onRetry() {} };
const render = (props = {}) => renderToStaticMarkup(createElement(MemoryRouter, null, createElement(TeachersSection, { ...base, ...props })));
function find(node, predicate) {
  if (!node || typeof node !== 'object') return undefined;
  if (predicate(node)) return node;
  for (const child of [node.props?.children].flat(Infinity)) {
    const found = find(child, predicate); if (found) return found;
  }
}

test('one labelled select contains exactly the six approved choices and defaults to name ascending', () => {
  const html = render();
  assert.match(html, /<label for="teacher-sort">Sort by<\/label>/);
  assert.equal((html.match(/<option /g) ?? []).length, 6);
  for (const [value, label] of options) assert.ok(html.includes(`value="${value}"`)), assert.ok(html.includes(label));
  assert.match(html, /value="NAME_ASC" selected=""/);
});

for (const [sort] of options) {
  test(`${sort} change preserves both search values and sends the controlled selection in the existing request`, async () => {
    let selection = 'NAME_ASC'; let searched = false;
    const tree = TeachersSection({ ...base, onSortChange: value => { selection = value; }, onQueryChange: () => { searched = true; } });
    find(tree, node => node.type === 'select').props.onChange({ target: { value: sort } });
    assert.equal(selection, sort); assert.equal(searched, false);
    const calls = [];
    globalThis.fetch = async (url, config) => { calls.push({ url, config }); return { ok: true, json: async () => teachers }; };
    const controller = new AbortController();
    assert.deepEqual(await getTeachers(controller.signal, '  Person  ', ' INF 345 ', selection), teachers);
    assert.equal(calls.length, 1);
    const url = new URL(calls[0].url, 'http://localhost');
    assert.equal(url.pathname, '/api/teachers'); assert.equal(url.searchParams.get('q'), 'Person');
    assert.equal(url.searchParams.get('courseQ'), 'INF 345');
    assert.equal(url.searchParams.get('sort'), sort === 'NAME_ASC' ? null : sort);
    assert.equal(calls[0].config.body, undefined);
    controller.abort(); assert.equal(calls[0].config.signal.aborted, true);
    assert.ok(render({ sort: selection }).includes(`value="${sort}" selected=""`));
  });
}

test('API preserves returned order; cards keep all teachers and profile links without client sorting', async () => {
  globalThis.fetch = async () => ({ ok: true, json: async () => teachers });
  const rows = await getTeachers(new AbortController().signal, '', '', 'HAS_FEEDBACK_FIRST');
  const html = render({ teachers: rows });
  assert.ok(html.indexOf('Zeta Person') < html.indexOf('Alpha Person'));
  assert.match(html, /2 teachers/);
  assert.match(html, /href="\/teachers\/8"[^>]*>View Profile/);
  assert.match(html, /href="\/teachers\/7"[^>]*>View Profile/);
});

test('unrated teachers stay visible; no aggregate widgets or review/private fields are rendered', () => {
  const html = render({ teachers: teachers.map(t => ({ ...t, averageRating: 4.625, publishedReviewCount: 777,
    authorId: 99999, reviewId: 12345, text: 'Secret review', groupSection: '15-P', moderation: 'NEEDS_CORRECTION' })) });
  assert.doesNotMatch(html, /4\.625|777|99999|12345|Secret review|15-P|NEEDS_CORRECTION|Based on|★/);
  assert.match(html, /Zeta Person/); assert.match(html, /Alpha Person/);
});

test('loading and failures hide stale cards while keeping current search and sort; retry remains available', () => {
  for (const props of [{ loading: true }, { error: true }]) {
    const html = render({ ...props, sort: 'RATING_ASC' });
    assert.doesNotMatch(html, /Zeta Person|Alpha Person/);
    assert.match(html, /value="Person"/); assert.match(html, /value="RATING_ASC" selected=""/);
  }
  assert.match(render({ loading: true }), /Loading teachers/);
  assert.match(render({ error: true }), /role="alert"/);
  let retries = 0;
  const tree = TeachersSection({ ...base, error: true, onRetry: () => retries++ });
  find(tree, node => node.type === 'button').props.onClick(); assert.equal(retries, 1);
});

test('empty filtered and empty catalog states remain distinct under sorting', () => {
  assert.match(render({ teachers: [], sort: 'RATING_DESC' }), /No teachers found/);
  assert.match(render({ teachers: [], hasActiveSearch: false, sort: 'HAS_FEEDBACK_FIRST' }), /No teachers in the catalog yet/);
});

test('default API remains backward compatible and ignores whitespace-only filters', async () => {
  globalThis.fetch = async url => { assert.equal(url, '/api/teachers'); return { ok: true, json: async () => teachers }; };
  await getTeachers(new AbortController().signal);
  await getTeachers(new AbortController().signal, '  ', '  ', 'NAME_ASC');
});

test('errors never expose backend response details and retry uses the same sort and filters', async () => {
  const calls = [];
  globalThis.fetch = async url => { calls.push(url); return { ok: calls.length > 1, json: async () => calls.length > 1 ? teachers : { message: 'Private SQL' } }; };
  const signal = new AbortController().signal;
  await assert.rejects(getTeachers(signal, 'Person', 'INF', 'REVIEW_COUNT_DESC'), { message: 'Could not load teachers.' });
  assert.deepEqual(await getTeachers(signal, 'Person', 'INF', 'REVIEW_COUNT_DESC'), teachers);
  assert.equal(calls[0], calls[1]);
});

test('Browse wires independent sort state to teacher fetch/retry with stale-request protection', async () => {
  const source = await readFile(new URL('../src/pages/BrowsePage.tsx', import.meta.url), 'utf8');
  assert.match(source, /useState<TeacherSort>\('NAME_ASC'\)/);
  assert.match(source, /getTeachers\(controller.signal, teacherQuery, courseQuery, teacherSort\)/);
  assert.match(source, /\[teacherQuery, courseQuery, teacherSort, teacherAttempt\]/);
  assert.match(source, /sort={teacherSort}/); assert.match(source, /onSortChange={setTeacherSort}/);
  assert.match(source, /if \(!controller.signal.aborted\) setTeachers\(data\)/);
  assert.match(source, /return \(\) => controller.abort\(\)/);
  assert.match(source, /getCourses\(controller.signal, courseQuery, teacherQuery\)/);
  assert.match(source, /\[courseQuery, teacherQuery, courseAttempt\]/);
});
