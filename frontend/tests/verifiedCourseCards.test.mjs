import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router';
import { reviewTargetQuery } from '../src/pages/reviewTargetQuery.ts';

const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)), server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(() => server.close());
const { VerifiedCourseCards } = await server.ssrLoadModule('/src/pages/VerifiedCoursesPage.tsx');
const { resolveReviewTarget } = await server.ssrLoadModule('/src/pages/ReviewTargetPage.tsx');
const source = {
  verifiedCourseId: 1, courseOfferingId: 100, uploadId: 21, sourceEntryId: 10,
  courseCode: 'ABC 123', courseName: 'Example Subject', teacherName: 'Alex Example',
  groupSection: '01-N', academicTerm: '2026-2027 1 term',
};
const render = rows => renderToStaticMarkup(createElement(MemoryRouter, null, createElement(VerifiedCourseCards, { rows })));
const links = html => [...html.matchAll(/href="([^"]*\/student\/reviews\/new\?[^"]+)"/g)].map(match => match[1].replaceAll('&amp;', '&'));

test('every logical card has an accessible direct link using only the existing offering and term query', () => {
  const rows = [source, { ...source, courseOfferingId: 101, teacherName: 'Jamie Example' }, { ...source, academicTerm: '2025-2026 2 term' }];
  const html = render(rows);
  assert.equal(links(html).length, 3);
  assert.match(html, /3 verified courses/);
  for (const [index, href] of links(html).entries()) {
    assert.equal(href, `/student/reviews/new?${reviewTargetQuery(rows[index])}`);
    const url = new URL(href, 'http://localhost');
    assert.deepEqual([...url.searchParams.keys()], ['courseOfferingId', 'academicTerm']);
    const selected = resolveReviewTarget(rows, url.search).target;
    assert.equal(selected.courseOfferingId, rows[index].courseOfferingId);
    assert.equal(selected.academicTerm, rows[index].academicTerm);
    assert.equal(selected.teacherName, rows[index].teacherName);
  }
  assert.match(html, /aria-label="Leave review for ABC 123 with Alex Example, 2026-2027 1 term"/);
});

test('multiple sections and three uploads keep one card, one action and source traceability', () => {
  const rows = [21, 23, 24].flatMap(uploadId => ['01-N', '02-P'].map(groupSection => ({ ...source, uploadId, groupSection })));
  const before = JSON.stringify(rows);
  const html = render(rows);
  assert.equal(links(html).length, 1);
  assert.match(html, /1 verified courses/);
  assert.match(html, /01-N, 02-P/);
  assert.match(html, /Verified from 3 schedule uploads/);
  assert.equal(JSON.stringify(rows), before);
});

test('independent semantic action adds no nested interactive card, profile route or review form', () => {
  const html = render([source]);
  assert.match(html, /class="catalog-card-main"/);
  assert.match(html, /<a class="leave-review-link"/);
  assert.doesNotMatch(html, /<button|<form|<textarea|<a[^>]*>[\s\S]*<a|\/teachers\/|\/courses\//);
});

test('empty verified courses retain empty state without a review shortcut', () => {
  const html = render([]);
  assert.match(html, /No verified courses are available yet/);
  assert.equal(links(html).length, 0);
});
