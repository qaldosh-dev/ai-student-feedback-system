import { test, after, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)), server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(() => server.close());
afterEach(() => { delete globalThis.sessionStorage; });
const { draftKey, validDraft, loadDraft, saveDraft, clearReviewDrafts, FEEDBACK_MAX_LENGTH } = await server.ssrLoadModule('/src/pages/reviewDraft.ts');
const { ReviewDraftFields } = await server.ssrLoadModule('/src/components/ReviewDraftEditor.tsx');
const { ReviewTargetSelection, reviewTargetQuery } = await server.ssrLoadModule('/src/pages/ReviewTargetPage.tsx');
const first = { courseOfferingId: 1, courseId: 10, teacherId: 20, courseCode: 'ABC 100', courseName: 'Alpha', teacherName: 'Alex Person', academicTerm: '2026-2027 1 term', groupSections: ['01-N', '02-P'] };
const second = { ...first, courseOfferingId: 2, teacherId: 21, teacherName: 'Morgan Person' };
function storage() {
  const data = new Map();
  globalThis.sessionStorage = { get length() { return data.size; }, key: i => [...data.keys()][i], getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) };
  return data;
}
const render = (target = first, rows = [first, second], rating) => renderToStaticMarkup(createElement(ReviewTargetSelection, { rows, search: reviewTargetQuery(target, rating).toString(), onSelect() {} }));
function find(element, type) {
  if (!element || typeof element !== 'object') return undefined;
  if (Array.isArray(element)) return element.map(child => find(child, type)).find(Boolean);
  return element.type === type ? element : find(element.props?.children, type);
}

test('eligible target has labelled textarea, limit, count and no submission', () => {
  storage();
  const html = render();
  assert.match(html, /<label for="review-feedback">Your feedback/);
  assert.match(html, /maxLength="2000"/i); assert.match(html, /0 \/ 2000/);
  assert.match(html, /Feedback is required before submission/);
  assert.doesNotMatch(html, /type="submit"|<form|Publish|Save to server/);
});
test('stored draft cannot activate an ineligible or wrong-term target', () => {
  storage(); saveDraft(first, { rating: 4, text: 'Private draft' });
  for (const [target, rows] of [[first, [second]], [{ ...first, academicTerm: 'other' }, [first]]]) {
    const html = render(target, rows, 4);
    assert.match(html, /not available for review/); assert.doesNotMatch(html, /textarea|Private draft|out of 5/);
  }
});
test('typing persists rating and original punctuation/whitespace together across reload', () => {
  storage(); let draft = { rating: 4, text: '' };
  const fields = ReviewDraftFields({ draft, onChange(next) { draft = next; saveDraft(first, next); } });
  find(fields, 'textarea').props.onChange({ target: { value: '  Clear explanations!\nUseful examples.  ' } });
  assert.deepEqual(loadDraft(JSON.parse(JSON.stringify(first)), null), draft);
  assert.equal(draft.rating, 4); assert.equal(draft.text, '  Clear explanations!\nUseful examples.  ');
  assert.match(render(), /Your rating: 4 \/ 5/);
  assert.match(render(), /Clear explanations!/);
});
test('A to B to A restores independent ratings and text for different teachers', () => {
  storage(); const a = { rating: 4, text: 'Clear explanations' }, b = { rating: 2, text: 'More examples please' };
  saveDraft(first, a); assert.deepEqual(loadDraft(second, null), { rating: null, text: '' });
  saveDraft(second, b); assert.deepEqual(loadDraft(first, null), a); assert.deepEqual(loadDraft(second, null), b);
  assert.match(render(first), /Clear explanations/); assert.doesNotMatch(render(first), /More examples please/);
});
for (const [label, other] of [
  ['different course, same teacher', { ...first, courseOfferingId: 3, courseId: 11 }],
  ['different academic term', { ...first, academicTerm: '2025-2026 2 term' }],
]) test(`independent draft: ${label}`, () => {
  storage(); saveDraft(first, { rating: 4, text: 'First' }); saveDraft(other, { rating: 1, text: 'Other' });
  assert.equal(loadDraft(first, null).text, 'First'); assert.equal(loadDraft(other, null).text, 'Other');
});
test('groups and repeated source uploads cannot multiply a target draft', () => {
  const data = storage(); saveDraft(first, { rating: 4, text: 'One draft' });
  saveDraft({ ...first, groupSections: ['02-P'], uploadId: 99 }, { rating: 4, text: 'One draft' });
  assert.equal(data.size, 1); const html = render();
  assert.equal((html.match(/<textarea/g) ?? []).length, 1);
  assert.equal((html.match(/class="rating-input"/g) ?? []).length, 1);
  assert.match(html, /01-N, 02-P/);
});
for (const rating of [0, 6, -1, 1.5, '4', {}, null]) test(`reject stored rating ${JSON.stringify(rating)}`, () => {
  storage(); sessionStorage.setItem(draftKey(first), JSON.stringify({ rating, text: 'Valid text' }));
  assert.deepEqual(loadDraft(first, null), { rating: null, text: 'Valid text' });
});
test('all five valid rating values survive storage', () => {
  storage(); for (let rating = 1; rating <= 5; rating++) { saveDraft(first, { rating, text: '' }); assert.equal(loadDraft(first, null).rating, rating); }
});
test('malformed JSON, text shapes and oversized text are rejected safely', () => {
  storage(); sessionStorage.setItem(draftKey(first), '{bad JSON');
  assert.deepEqual(loadDraft(first, null), { rating: null, text: '' });
  for (const text of [null, {}, 42, 'x'.repeat(2001)]) assert.equal(validDraft({ rating: 4, text }).text, '');
});
test('textarea prevents more than 2000 characters including programmatic input', () => {
  let draft = { rating: 4, text: '' };
  const field = find(ReviewDraftFields({ draft, onChange(next) { draft = next; } }), 'textarea');
  assert.equal(field.props.maxLength, FEEDBACK_MAX_LENGTH);
  field.props.onChange({ target: { value: 'x'.repeat(2001) } }); assert.equal(draft.text.length, 2000);
  assert.match(renderToStaticMarkup(createElement(ReviewDraftFields, { draft, onChange() {} })), /2000 \/ 2000/);
});
test('whitespace-only is empty for completion but original text and count are preserved', () => {
  const html = renderToStaticMarkup(createElement(ReviewDraftFields, { draft: { rating: null, text: '   ' }, onChange() {} }));
  assert.match(html, /3 \/ 2000/); assert.match(html, /Feedback is required before submission/);
});
test('legacy URL initializes missing rating, but stored rating wins deterministic conflict', () => {
  storage(); assert.deepEqual(loadDraft(first, 4), { rating: 4, text: '' });
  saveDraft(first, { rating: null, text: 'Text already drafted' });
  assert.deepEqual(loadDraft(first, 4), { rating: 4, text: 'Text already drafted' });
  saveDraft(first, { rating: 2, text: 'Stored draft' });
  assert.deepEqual(loadDraft(first, 4), { rating: 2, text: 'Stored draft' });
  assert.match(render(first, [first], 4), /Your rating: 2 \/ 5/);
});
test('storage contains only rating/text, never account or backend objects; no network while drafting', () => {
  const data = storage(); const originalFetch = globalThis.fetch;
  globalThis.fetch = () => { throw new Error('Unexpected draft network request'); };
  try {
    saveDraft(first, { rating: 4, text: 'Hello', userId: 123, roles: ['ADMIN'] });
    assert.deepEqual(JSON.parse(data.get(draftKey(first))), { rating: 4, text: 'Hello' });
    assert.equal(loadDraft(first, null).text, 'Hello');
  } finally { globalThis.fetch = originalFetch; }
});
test('auth cleanup removes only review drafts, leaving unrelated session state intact', () => {
  const data = storage(); saveDraft(first, { rating: 4, text: 'A' }); saveDraft(second, { rating: 2, text: 'B' });
  sessionStorage.setItem('unrelated', 'keep'); clearReviewDrafts();
  assert.equal(data.size, 1); assert.equal(data.get('unrelated'), 'keep');
  assert.deepEqual(loadDraft(first, null), { rating: null, text: '' });
});
test('blocked/full storage does not crash and reports unsuccessful save', () => {
  globalThis.sessionStorage = { getItem() { throw Error('blocked'); }, setItem() { throw Error('quota'); } };
  assert.deepEqual(loadDraft(first, 4), { rating: 4, text: '' });
  assert.equal(saveDraft(first, { rating: 4, text: 'A' }), false); assert.doesNotThrow(clearReviewDrafts);
});
