import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)), server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(() => server.close());
const { ReviewTargetSelection, resolveReviewTarget, reviewTargetQuery, readReviewRating } = await server.ssrLoadModule('/src/pages/ReviewTargetPage.tsx');
const first = { courseOfferingId: 1, courseId: 10, teacherId: 20, courseCode: 'ABC 100', courseName: 'Alpha Subject', teacherName: 'Alex Person', academicTerm: '2026-2027 1 term', groupSections: ['01-N', '02-P'] };
const second = { ...first, courseOfferingId: 2, teacherId: 21, teacherName: 'Morgan Person', groupSections: ['04-N'] };
const rows = [first, second];
const query = target => reviewTargetQuery(target).toString();
const render = (data = rows, search = '') => renderToStaticMarkup(createElement(ReviewTargetSelection, { rows: data, search, onSelect() {}, onRate() {} }));
const summary = html => html.match(/<section[\s\S]*?<\/section>/)?.[0] ?? '';

test('eligible targets render once each in server order', () => {
  const html = render(); assert.match(html, /2 available review targets/);
  assert.equal((html.match(/<button /g) ?? []).length, 2);
  assert.ok(html.indexOf('Alex Person') < html.indexOf('Morgan Person'));
});
test('no selection initially and exactly one after choosing a target', () => {
  assert.doesNotMatch(render(), /aria-pressed="true"/);
  assert.equal((render(rows, query(first)).match(/aria-pressed="true"/g) ?? []).length, 1);
  assert.equal((render(rows, query(second)).match(/aria-pressed="true"/g) ?? []).length, 1);
});
test('summary contains the selected course code and name', () => assert.match(summary(render(rows, query(first))), /ABC 100 — Alpha Subject/));
test('same course with different teachers cannot cross-select', () => {
  const html = summary(render(rows, query(first))); assert.match(html, /Alex Person/); assert.doesNotMatch(html, /Morgan Person/);
  assert.equal(resolveReviewTarget(rows, query(second)).target.teacherName, 'Morgan Person');
});
test('summary clearly includes term and aggregated groups', () => {
  const html = summary(render(rows, query(first))); assert.match(html, /Academic Term:/); assert.match(html, /2026-2027 1 term/);
  assert.match(html, /Group \/ Section:/); assert.match(html, /01-N, 02-P/);
});
test('same teacher with different courses remains separately selectable', () => {
  const other = { ...first, courseOfferingId: 3, courseId: 11, courseCode: 'ABC 200', courseName: 'Beta Subject' };
  assert.equal(resolveReviewTarget([first, other], query(other)).target.courseCode, 'ABC 200');
  assert.doesNotMatch(summary(render([first, other], query(other))), /Alpha Subject/);
});
test('aggregated US20 target for repeated uploads and groups yields one option', () => {
  const html = render([first]); assert.match(html, /1 available review targets/);
  assert.equal((html.match(/<button /g) ?? []).length, 1);
  assert.doesNotMatch(query(first), /groupSection|uploadId|teacherId|courseId=/);
});
test('URL identity survives a fresh server response and browser refresh', () => {
  const url = new URL('/student/reviews/new?' + query(first), 'http://localhost:5173');
  const freshRows = JSON.parse(JSON.stringify(rows));
  assert.deepEqual(resolveReviewTarget(freshRows, url.search).target, first);
  assert.match(summary(render(freshRows, url.search)), /Alex Person/);
});
test('different academic terms for the same offering are independent targets', () => {
  const past = { ...first, academicTerm: '2025-2026 2 term' };
  assert.equal(resolveReviewTarget([first, past], query(past)).target.academicTerm, past.academicTerm);
  assert.equal((render([first, past], query(past)).match(/aria-pressed="true"/g) ?? []).length, 1);
});
for (const search of [
  'courseOfferingId=999&academicTerm=2026-2027+1+term',
  'courseOfferingId=1&academicTerm=2025-2026+2+term',
  'teacherId=20', 'courseId=10', 'courseOfferingId=1',
  'courseOfferingId=-1&academicTerm=2026-2027+1+term',
  'courseOfferingId=1.0&academicTerm=2026-2027+1+term',
  'courseOfferingId=9007199254740993&academicTerm=2026-2027+1+term',
  'courseOfferingId=1&academicTerm=',
  'courseOfferingId=1&courseOfferingId=2',
  query(first) + '&academicTerm=other',
]) test(`untrusted identity rejected: ${search}`, () => {
  assert.equal(resolveReviewTarget(rows, search).kind, 'unavailable');
  const html = render(rows, search); assert.match(html, /This course is not available for review/);
  assert.doesNotMatch(html, /Selected review target|aria-pressed="true"/);
});
test('a previously selected relationship removed from current eligibility is rejected', () => {
  assert.equal(resolveReviewTarget([second], query(first)).kind, 'unavailable');
});
test('other student, pending and unchecked targets absent from server list are not inferred', () => {
  for (const id of [50, 51, 52]) assert.equal(resolveReviewTarget(rows, query({ ...first, courseOfferingId: id })).kind, 'unavailable');
});
test('ambiguous duplicate response cannot validate a selected target', () => assert.equal(resolveReviewTarget([first, { ...first }], query(first)).kind, 'unavailable'));
test('empty state has no catalog fallback or select buttons', () => {
  const html = render([]); assert.match(html, /No verified courses are currently available for review/); assert.doesNotMatch(html, /<button|Alpha Subject/);
});
test('selection is read-only and leaves source DTOs untouched', () => {
  const before = JSON.stringify(rows); render(rows, query(first)); assert.equal(JSON.stringify(rows), before);
});
test('no submission or form exists', () => {
  const html = render(rows, query(first));
  assert.doesNotMatch(html, /<input|<form|type="submit"|slider/i);
});



const RatingInput = (await server.ssrLoadModule('/src/components/RatingInput.tsx')).default;
const ratedQuery = (target, rating) => reviewTargetQuery(target, rating).toString();
const stars = (value, onChange = () => {}) => RatingInput({ value, onChange }).props.children[1].props.children;
function findElement(element, predicate) {
  if (!element || typeof element !== 'object') return undefined;
  if (Array.isArray(element)) return element.map(child => findElement(child, predicate)).find(Boolean);
  if (predicate(element)) return element;
  return findElement(element.props?.children, predicate);
}

test('only a valid eligible target shows five rating controls', () => {
  assert.equal((render(rows, query(first)).match(/out of 5/g) ?? []).length, 5);
  for (const search of ['', 'rating=4', 'courseOfferingId=999&academicTerm=wrong&rating=5'])
    assert.doesNotMatch(render(rows, search), /out of 5|Your rating/);
});
for (const value of [1, 2, 3, 4, 5]) test(`rating ${value} uses semantic button callback and restores from URL`, () => {
  let chosen = null;
  const button = stars(null, rating => { chosen = rating; })[value - 1];
  assert.equal(button.type, 'button'); assert.equal(button.props.type, 'button');
  assert.equal(button.props['aria-label'], `${value} out of 5`);
  button.props.onClick(); assert.equal(chosen, value);
  const url = new URL('/student/reviews/new?' + ratedQuery(first, chosen), 'http://localhost:5173');
  assert.equal(readReviewRating(url.search), value);
  assert.equal(resolveReviewTarget(JSON.parse(JSON.stringify(rows)), url.search).target.teacherName, first.teacherName);
  assert.match(render(rows, url.search), new RegExp(`Your rating: ${value} / 5`));
});
test('4 fills exactly four stars while only option four is pressed', () => {
  const buttons = stars(4);
  assert.equal(buttons.filter(button => button.props.className.includes('is-filled')).length, 4);
  assert.equal(buttons.filter(button => button.props['aria-pressed']).length, 1);
  assert.equal(buttons[3].props['aria-pressed'], true);
});
test('rating callbacks can replace four stars with two', () => {
  let value = null;
  stars(value, next => { value = next; })[3].props.onClick();
  assert.equal(value, 4);
  stars(value, next => { value = next; })[1].props.onClick();
  assert.equal(value, 2);
  assert.equal(stars(2).filter(b => b.props.className.includes('is-filled')).length, 2);
});
for (const value of ['0', '6', '-1', '4.5', 'abc', '', '04', '4.0', ' 4', '4 ', 'NaN', 'Infinity'])
  test(`invalid rating ${JSON.stringify(value)} ignored without losing valid target`, () => {
    const params = reviewTargetQuery(first); params.set('rating', value);
    assert.equal(readReviewRating(params.toString()), null);
    assert.equal(resolveReviewTarget(rows, params.toString()).kind, 'selected');
    const html = render(rows, params.toString());
    assert.match(html, /No rating selected/); assert.match(html, /Selected review target/); assert.doesNotMatch(html, /is-filled/);
  });
for (const suffix of ['rating=4&rating=2', 'rating=4&rating=4'])
  test(`ambiguous repeated rating ignored: ${suffix}`, () => {
    const search = query(first) + '&' + suffix;
    assert.equal(readReviewRating(search), null); assert.equal(resolveReviewTarget(rows, search).kind, 'selected');
  });
test('invalid optional rating cannot grant eligibility to unavailable/pending/other-user target', () => {
  for (const rating of [1, 5]) {
    const search = ratedQuery({ ...first, courseOfferingId: 999 }, rating);
    assert.equal(resolveReviewTarget(rows, search).kind, 'unavailable');
    assert.doesNotMatch(render(rows, search), /out of 5|Your rating/);
  }
});
test('switching targets omits legacy URL rating; per-target storage restores drafts separately', () => {
  let search = ratedQuery(first, 4);
  const view = ReviewTargetSelection({ rows, search, onRate() {}, onSelect(target) { search = query(target); } });
  findElement(view, e => e.type === 'button' && e.props['aria-label']?.startsWith('Select ABC 100, Morgan')).props.onClick();
  assert.equal(resolveReviewTarget(rows, search).target, second); assert.equal(readReviewRating(search), null);
});
test('clicking the already-selected target does not erase its rating', () => {
  let calls = 0;
  const view = ReviewTargetSelection({ rows, search: ratedQuery(first, 4), onRate() {}, onSelect() { calls++; } });
  findElement(view, e => e.type === 'button' && e.props['aria-pressed']).props.onClick();
  assert.equal(calls, 0);
});
test('new target URL omits rating; legacy direct URL rating is readable', () => {
  for (const target of [{ ...first, academicTerm: '2025-2026 2 term' }, { ...first, courseOfferingId: 3, courseId: 12 }]) {
    assert.equal(readReviewRating(query(target)), null);
    assert.equal(readReviewRating(ratedQuery(target, 5)), 5);
    assert.equal(resolveReviewTarget([first, target], ratedQuery(target, 5)).target, target);
  }
});
test('multiple group sections have exactly one overall rating control', () => {
  const html = render([first], ratedQuery(first, 4));
  assert.equal((html.match(/class="rating-input"/g) ?? []).length, 1); assert.match(html, /01-N, 02-P/);
});
test('all five values have keyboard-native buttons and screen-reader labels', () => {
  const element = RatingInput({ value: null, onChange() {} });
  assert.equal(element.type, 'fieldset');
  for (const [index, button] of stars(null).entries()) {
    assert.equal(button.type, 'button'); assert.equal(button.props.disabled, undefined);
    assert.equal(button.props['aria-label'], `${index + 1} out of 5`);
    assert.equal(button.props.children.props['aria-hidden'], 'true');
  }
});
test('invalid programmatic rating is not written as a draft value', () => {
  for (const value of [0, 6, -1, 4.5, NaN, Infinity]) assert.equal(reviewTargetQuery(first, value).has('rating'), false);
});
test('rated view has no publish or history controls', () => {
  assert.doesNotMatch(render(rows, ratedQuery(first, 4)), /<form|type="submit"|Save Review|Publish/i);
});
