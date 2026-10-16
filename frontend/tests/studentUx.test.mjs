import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router';

const root = fileURLToPath(new URL('../', import.meta.url));
const server = await createServer({ root, server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(() => server.close());
const { default: Dashboard } = await server.ssrLoadModule('/src/components/StudentDashboard.tsx');
const { default: BackLink } = await server.ssrLoadModule('/src/components/StudentBackLink.tsx');
const { ScheduleUploadCards } = await server.ssrLoadModule('/src/components/ScheduleUploadList.tsx');
const { ExtractedEntryCards } = await server.ssrLoadModule('/src/pages/ScheduleDetailsPage.tsx');
const { ReviewTargetSelection, reviewTargetQuery } = await server.ssrLoadModule('/src/pages/ReviewTargetPage.tsx');
const { reviewAction, existingReview, requestedReviewEdit } = await server.ssrLoadModule('/src/pages/reviewAction.ts');
const { MyReviewsContent } = await server.ssrLoadModule('/src/pages/MyReviewsPage.tsx');
const render = (Component, props = {}) => renderToStaticMarkup(createElement(MemoryRouter, {}, createElement(Component, props)));
const target = { courseOfferingId: 42, courseId: 4, teacherId: 5, courseCode: 'ABC 100', courseName: 'Alpha Subject', teacherName: 'Alex Person', academicTerm: '2026-2027 1 term', groupSections: ['01-N', '03-P'] };
const review = { ...target, reviewId: 7, status: 'PUBLISHED', rating: 4, text: 'Existing feedback', submittedAt: '2026-10-01T00:00:00Z', moderatedAt: null, moderationReasons: [] };
const selection = (reviews, search = '', rows = [target]) => render(ReviewTargetSelection, { rows, reviews, search, onSelect() {} });

test('dashboard has four semantic action cards and no embedded upload form', async () => {
  const html = render(Dashboard);
  assert.equal((html.match(/<a /g) ?? []).length, 4);
  for (const label of ['Upload Schedule', 'Verified Courses', 'Review a Course', 'My Reviews']) assert.match(html, new RegExp(label));
  assert.match(html, /href="\/student\/schedules"/); assert.match(html, /student-action-card/);
  assert.doesNotMatch(html, /<form|type="file"/);
  const area = await readFile(new URL('../src/pages/RoleAreaPage.tsx', import.meta.url), 'utf8');
  const routes = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  const uploads = await readFile(new URL('../src/pages/ScheduleUploadsPage.tsx', import.meta.url), 'utf8');
  assert.doesNotMatch(area, /ScheduleUploadForm|ScheduleUploadList/);
  assert.match(routes, /path="\/student\/schedules" element={<ProtectedRoute requiredRole="STUDENT"><ScheduleUploadsPage/);
  assert.match(uploads, /Detailed\/Full/); assert.match(uploads, /10 MB/); assert.match(uploads, /<ScheduleUploadForm/); assert.match(uploads, /<ScheduleUploadList/);
});
test('back controls are styled semantic links with correct destinations', () => {
  assert.match(render(BackLink), /class="student-back-link" href="\/student"/);
  const html = render(BackLink, { to: '/student/schedules', children: 'Back to Schedule Uploads' });
  assert.match(html, /href="\/student\/schedules"/); assert.match(html, /Back to Schedule Uploads/); assert.doesNotMatch(html, /<button|tabindex="-1"/);
});
test('all Student detail pages use the common back control', async () => {
  for (const page of ['ScheduleDetailsPage', 'VerifiedCoursesPage', 'ReviewTargetPage', 'MyReviewsPage', 'ReviewStatusPage']) {
    const code = await readFile(new URL(`../src/pages/${page}.tsx`, import.meta.url), 'utf8');
    assert.match(code, /<StudentBackLink/);
  }
});
test('schedule upload cards retain filename, date, term, count, status and details link', () => {
  const html = render(ScheduleUploadCards, { uploads: [{ id: 12, originalFilename: 'Detailed.pdf', uploadedAt: '2026-10-01T00:00:00Z', status: 'VALID', academicTerms: [target.academicTerm], extractedCount: 10, validationMessage: null }] });
  for (const text of ['Detailed.pdf', 'Uploaded:', 'Valid', target.academicTerm, '10 extracted entries', 'View extracted information']) assert.ok(html.includes(text));
  assert.match(html, /student-badge valid/); assert.match(html, /href="\/student\/schedules\/12"/);
});
test('invalid upload keeps safe validation reason without an extraction action', () => {
  const html = render(ScheduleUploadCards, { uploads: [{ id: 12, originalFilename: 'Compact.pdf', uploadedAt: '2026-10-01T00:00:00Z', status: 'INVALID', academicTerms: [], extractedCount: 0, validationMessage: 'Detailed schedule required.' }] });
  assert.match(html, /student-badge invalid/); assert.match(html, /Detailed schedule required/); assert.doesNotMatch(html, /View extracted information/);
});
test('entry badges retain Verified, Pending reason, Not checked and complete section identifiers', () => {
  const entries = ['VERIFIED', 'PENDING', 'NOT_CHECKED'].map((status, i) => ({ ...target, id: i + 1, groupSection: '15-P', verificationStatus: status, verificationReason: status === 'PENDING' ? 'NO_COURSE_OFFERING' : null }));
  const html = render(ExtractedEntryCards, { entries });
  for (const value of ['student-badge verified', 'student-badge pending', 'Not checked', '15-P', 'their relationship was not found']) assert.ok(html.includes(value));
  assert.doesNotMatch(html, /Rejected|practice|lecture/);
});
test('no existing review offers Review and selected target gets the existing create editor', () => {
  assert.match(selection([]), />Review<\/button>/);
  assert.match(selection([], reviewTargetQuery(target).toString()), /<textarea/);
});
for (const [status, label, href] of [
  ['PUBLISHED', 'Edit Review', '/student/reviews?edit=7'],
  ['NEEDS_CORRECTION', 'Fix Feedback', '/student/reviews/7/status'],
  ['SUBMITTED', 'View Status', '/student/reviews/7/status'],
]) {
  test(`${status}: correct action before selection and on direct create URL, never a blank editor`, () => {
    const current = { ...review, status };
    assert.equal(reviewAction(current).label, label); assert.equal(reviewAction(current).href, href);
    for (const query of ['', reviewTargetQuery(target).toString()]) {
      const html = selection([current], query);
      assert.ok(html.includes(`href="${href}"`)); assert.ok(html.includes(label));
      assert.doesNotMatch(html, /<textarea|Submit Review|>Review<\/button>|rating-star/);
    }
  });
  test(`${status}: refreshed history and My Reviews use the same primary action`, () => {
    const reviews = JSON.parse(JSON.stringify([{ ...review, status }]));
    assert.ok(selection(reviews).includes(label));
    const html = render(MyReviewsContent, { reviews, loading: false, error: '', editing: null, onEdit() {}, onCancel() {}, onRefresh() {}, onSessionExpired() {} });
    assert.ok(html.includes(label));
  });
}
test('stable target identity keeps other offerings/teachers and exact terms distinct, ignoring sections and display names', () => {
  assert.equal(existingReview([review], { ...target, groupSections: ['99-X'], teacherName: 'Different display' }), review);
  assert.equal(existingReview([review], { ...target, courseOfferingId: 43 }), undefined);
  assert.equal(existingReview([review], { ...target, academicTerm: 'Fall 2026' }), undefined);
  assert.equal(existingReview([review], { ...target, academicTerm: target.academicTerm + ' ' }), undefined);
});
test('published deep link selects only the owned current published review and prepopulates existing editor', () => {
  assert.equal(requestedReviewEdit([review], 'edit=7'), 7);
  for (const query of ['edit=99', 'edit=7&edit=7', 'edit=07', 'edit=abc']) assert.equal(requestedReviewEdit([review], query), null);
  for (const status of ['SUBMITTED', 'NEEDS_CORRECTION']) assert.equal(requestedReviewEdit([{ ...review, status }], 'edit=7'), null);
  const html = render(MyReviewsContent, { reviews: [review], loading: false, error: '', editing: requestedReviewEdit([review], 'edit=7'), onEdit() {}, onCancel() {}, onRefresh() {}, onSessionExpired() {} });
  assert.match(html, /Existing feedback<\/textarea>/); assert.match(html, /Your rating: 4 \/ 5/); assert.match(html, /Save Changes/); assert.doesNotMatch(html, /Submit Review/);
});

function elements(node) {
  if (!node || typeof node !== 'object') return [];
  return [node, ...[node.props?.children].flat(Infinity).flatMap(elements)];
}
test('production target loader waits for both reads, fails closed, refreshes and ignores late account/target results', async () => {
  const originalFetch = globalThis.fetch;
  const runtime = await createServer({ root, server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent', plugins: [{
    name: 'student-effects', enforce: 'pre',
    transform(code, id) {
      if (!id.replaceAll('\\', '/').endsWith('/pages/ReviewTargetPage.tsx')) return;
      return code.replace("from 'react';", "from 'virtual:ux-hooks';").replace("from '../auth/AuthContext';", "from 'virtual:ux-auth';").replace("from 'react-router';", "from 'virtual:ux-router';");
    },
    resolveId(id) { if (id.startsWith('virtual:ux-')) return '\0' + id; },
    load(id) {
      if (id === '\0virtual:ux-auth') return 'export const useAuth = () => ({ currentUser: { id: globalThis.__uxAccount }, expireSession() { globalThis.__uxExpired = true; } });';
      if (id === '\0virtual:ux-router') return 'export const Link = () => null; export const useSearchParams = () => [new URLSearchParams(globalThis.__uxQuery), () => {}]; export const useNavigate = () => () => {};';
      if (id === '\0virtual:ux-hooks') return `
        export function useState(initial) {
          const state = globalThis.__uxHooks; const i = state.cursor++;
          if (!(i in state.values)) state.values[i] = initial;
          return [state.values[i], value => { state.updates++; state.values[i] = typeof value === 'function' ? value(state.values[i]) : value; }];
        }
        export function useEffect(effect) { globalThis.__uxHooks.effects.push(effect); }
      `;
    },
  }] });
  const stops = [];
  try {
    const { default: Page, ReviewTargetSelection: Selection } = await runtime.ssrLoadModule('/src/pages/ReviewTargetPage.tsx');
    const calls = [];
    globalThis.fetch = (url, options) => new Promise(resolve => calls.push({ url, options, resolve }));
    const run = session => { session.store.cursor = 0; session.store.effects = []; globalThis.__uxHooks = session.store; return session.node.type(session.node.props); };
    const start = (account = 1, query = reviewTargetQuery(target).toString()) => {
      globalThis.__uxAccount = account; globalThis.__uxQuery = query;
      const session = { node: Page(), store: { cursor: 0, values: [], effects: [], updates: 0 } };
      run(session); session.stop = session.store.effects[0](); stops.push(session.stop);
      session.calls = calls.slice(-2); return session;
    };
    const shown = session => elements(run(session)).find(node => node.type === Selection);
    const finish = async (call, data, status = 200) => {
      call.resolve({ ok: status === 200, status, json: async () => data });
      await new Promise(resolve => setImmediate(resolve));
    };
    const first = start(); assert.equal(shown(first), undefined);
    await finish(first.calls[0], [target]); assert.equal(shown(first), undefined);
    await finish(first.calls[1], [review]); assert.equal(shown(first).props.reviews[0].reviewId, 7);
    first.stop();
    const failure = start(); await finish(failure.calls[0], [target]); await finish(failure.calls[1], [], 500);
    assert.equal(shown(failure), undefined); assert.ok(elements(run(failure)).some(n => n.props?.role === 'alert'));
    failure.stop();
    const missingIdentity = start(); await finish(missingIdentity.calls[0], [target]); await finish(missingIdentity.calls[1], [{ ...review, courseOfferingId: undefined }]);
    assert.equal(shown(missingIdentity), undefined); missingIdentity.stop();
    const late = start(); late.stop(); const updates = late.store.updates;
    const other = start(2); assert.notEqual(other.node.key, first.node.key);
    await finish(other.calls[0], [target]); await finish(other.calls[1], []);
    await finish(late.calls[0], [target]); await finish(late.calls[1], [review]);
    assert.equal(late.store.updates, updates); assert.deepEqual(shown(other).props.reviews, []); other.stop();
    const refreshed = start(); await finish(refreshed.calls[0], [target]); await finish(refreshed.calls[1], [{ ...review, status: 'SUBMITTED' }]);
    assert.equal(shown(refreshed).props.reviews[0].status, 'SUBMITTED'); refreshed.stop();
    const expired = start(); await finish(expired.calls[0], [], 401); await finish(expired.calls[1], [], 401);
    assert.equal(globalThis.__uxExpired, true); assert.equal(shown(expired), undefined);
    assert.ok(calls.every(call => call.options.credentials === 'same-origin' && !call.options.body));
  } finally {
    stops.forEach(stop => stop()); globalThis.fetch = originalFetch;
    for (const key of ['__uxHooks', '__uxAccount', '__uxQuery', '__uxExpired']) delete globalThis[key];
    await runtime.close();
  }
});
