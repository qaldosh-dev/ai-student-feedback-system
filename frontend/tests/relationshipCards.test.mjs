import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { renderToStaticMarkup as renderMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router';
import { relationshipHighlights } from '../src/pages/relationshipHighlights.ts';
const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)), server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(() => server.close());
const Teachers = (await server.ssrLoadModule('/src/components/TeachersSection.tsx')).default;
const Courses = (await server.ssrLoadModule('/src/components/CoursesSection.tsx')).default;
const { resolveReviewTarget, reviewTargetQuery } = await server.ssrLoadModule('/src/pages/ReviewTargetPage.tsx');
const renderToStaticMarkup = element => renderMarkup(createElement(MemoryRouter, null, element));
const rows = [
  { courseOfferingId: 1, courseId: 10, teacherId: 20, courseCode: 'ABC 100', courseName: 'Alpha Subject', teacherName: 'Alex Person', academicTerm: '2026-2027 1 term', groupSections: ['01-N', '02-P'] },
  { courseOfferingId: 2, courseId: 10, teacherId: 21, courseCode: 'ABC 100', courseName: 'Alpha Subject', teacherName: 'Morgan Person', academicTerm: '2026-2027 1 term', groupSections: ['04-N'] },
];
const props = { loading: false, error: false, query: '', hasActiveSearch: false, onQueryChange() {}, onRetry() {} };
test('actual teacher cards render contextual indicator only on eligible identity', () => {
  const html = renderToStaticMarkup(createElement(Teachers, { ...props, relationships: relationshipHighlights(rows, ['STUDENT']).teachers,
    teachers: [{ id: 20, name: 'Alex Person', department: '' }, { id: 99, name: 'Other Person', department: '' }] }));
  assert.equal((html.match(/has-verified-relationship/g) ?? []).length, 1);
  assert.match(html, /Verified with your schedule/); assert.match(html, /ABC 100 — Alpha Subject/);
  assert.match(html, /2026-2027 1 term/); assert.equal((html.match(/>Leave Review<\/a>/g) ?? []).length, 1);
});
test('actual course card renders both eligible teachers but no global verified-course label', () => {
  const html = renderToStaticMarkup(createElement(Courses, { ...props, relationships: relationshipHighlights(rows, ['STUDENT']).courses,
    courses: [{ id: 10, code: 'ABC 100', name: 'Alpha Subject' }, { id: 11, code: 'ABC 200', name: 'Other Subject' }] }));
  assert.equal((html.match(/has-verified-relationship/g) ?? []).length, 1);
  assert.match(html, /Alex Person/); assert.match(html, /Morgan Person/); assert.doesNotMatch(html, /Verified Course/);
  assert.equal((html.match(/>Leave Review<\/a>/g) ?? []).length, 2);
});
test('actual non-student card rendering remains public without personal context', () => {
  const html = renderToStaticMarkup(createElement(Courses, { ...props, relationships: relationshipHighlights(rows, ['TEACHER']).courses,
    courses: [{ id: 10, code: 'ABC 100', name: 'Alpha Subject' }] }));
  assert.match(html, /Alpha Subject/); assert.doesNotMatch(html, /has-verified-relationship|Verified with your schedule|Alex Person|Leave Review/);
});

const teacherCards = (data, roles = ['STUDENT']) => renderToStaticMarkup(createElement(Teachers, {
  ...props, relationships: relationshipHighlights(data, roles).teachers,
  teachers: [{ id: 20, name: 'Alex Person', department: '' }],
}));
const courseCards = (data, roles = ['STUDENT']) => renderToStaticMarkup(createElement(Courses, {
  ...props, relationships: relationshipHighlights(data, roles).courses,
  courses: [{ id: 10, code: 'ABC 100', name: 'Alpha Subject' }],
}));
const reviewLinks = html => [...html.matchAll(/href="([^"]*\/student\/reviews\/new\?[^"]+)"/g)].map(match => match[1].replaceAll('&amp;', '&'));

test('teacher with several verified courses has an action for every relationship, including fourth', () => {
  const data = [1,2,3,4].map(id => ({...rows[0], courseOfferingId:id, courseId:10+id, courseCode:`ABC ${100+id}`}));
  const links = reviewLinks(teacherCards(data)); assert.equal(links.length,4);
  assert.deepEqual(links, data.map(row => '/student/reviews/new?' + reviewTargetQuery(row)));
});
test('separate teacher actions resolve through the existing target mechanism without cross-selection', () => {
  const data = rows.map((row,index) => ({...row, courseCode:'INF 451', teacherName: index ? 'Lyazzat Atymtayeva' : 'Aliya Zhunis'}));
  const html = courseCards(data); const links = reviewLinks(html); assert.equal(links.length,2);
  for (const [index,href] of links.entries()) {
    const url = new URL(href,'http://localhost'); assert.equal(url.pathname,'/student/reviews/new');
    assert.deepEqual([...url.searchParams.keys()],['courseOfferingId','academicTerm']);
    assert.equal(resolveReviewTarget(data,url.search).target.teacherName,data[index].teacherName);
    assert.match(html,new RegExp(`aria-label="Leave review for INF 451 with ${data[index].teacherName}, ${data[index].academicTerm}"`));
  }
  assert.notEqual(links[0],links[1]);
});
test('same offering with different terms has distinct links; groups and repeated uploads do not multiply actions', () => {
  const otherTerm={...rows[0],academicTerm:'2025-2026 2 term'};
  const links=reviewLinks(teacherCards([rows[0],rows[0],{...rows[0],groupSections:['03-P']},otherTerm]));
  assert.equal(links.length,2); assert.notEqual(links[0],links[1]);
});
for(const roles of [[],['TEACHER'],['ADMIN']]) test(`both catalog sections hide review actions for ${roles.join() || 'logged out'}`,()=>{
  for(const render of [teacherCards,courseCards]) assert.doesNotMatch(render(rows,roles),/Leave Review|\/student\/reviews\/new/);
});
test('student plus teacher role retains shortcuts',()=>assert.equal(reviewLinks(courseCards(rows,['STUDENT','TEACHER'])).length,2));
test('without server eligibility, pending/unchecked/unrelated catalog relationships have no action',()=>{
  assert.equal(reviewLinks(teacherCards([])).length,0); assert.equal(reviewLinks(courseCards([])).length,0);
});
test('links are keyboard-native independent actions, with no nested interactive card or extra review form',()=>{
  const html=teacherCards(rows);
  assert.match(html,/class="catalog-card-main"/); assert.match(html,/<a class="leave-review-link"/);
  assert.doesNotMatch(html,/<button[^>]*>[\s\S]*Leave Review|<a\b[^>]*>(?:(?!<\/a>)[\s\S])*<a\b|<form|textarea|\/courses\//);
  assert.match(html,/href="\/teachers\/20"/);
});

