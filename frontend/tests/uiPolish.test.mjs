import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter, Routes, Route } from 'react-router';

const root = fileURLToPath(new URL('../', import.meta.url));
const server = await createServer({ root, server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(() => server.close());
const load = path => server.ssrLoadModule(`/src/${path}.tsx`);
const { default: Badge } = await load('components/StatusBadge');
const { default: Empty } = await load('components/EmptyState');
const { default: Loading } = await load('components/LoadingState');
const { ToastNotice, toastMessages } = await load('components/ToastProvider');
const { default: ErrorPage, AccessDenied } = await load('pages/ErrorPage');
const render = (Component, props = {}) => renderToStaticMarkup(createElement(MemoryRouter, {}, createElement(Component, props)));

for (const [status, label] of Object.entries({ VERIFIED: 'Verified', PENDING: 'Pending', VALID: 'Valid', INVALID: 'Invalid',
  PUBLISHED: 'Published', SUBMITTED: 'Submitted', NEEDS_CORRECTION: 'Needs Correction', PRIVACY_PROTECTED: 'Privacy Protected',
  AVAILABLE: 'Available', UNAVAILABLE: 'Unavailable', VALIDATING: 'Validating' })) {
  test(`badge ${status} has readable text, not color alone`, () => {
    const html = render(Badge, { status });
    assert.ok(html.includes(`>${label}</span>`)); assert.match(html, /status-badge/);
  });
}
test('empty state has useful text and an optional semantic action', () => {
  const html = render(Empty, { title: 'No verified courses yet.', description: 'Upload a schedule.', action: { to: '/student/schedules', label: 'Upload Schedule' } });
  assert.match(html, /<h2>No verified/); assert.match(html, /href="\/student\/schedules"/);
  assert.doesNotMatch(render(Empty, { title: 'No published feedback yet.', description: 'Check again later.' }), /<a /);
});
test('skeleton announces one loading label and hides decorative placeholders', () => {
  const html = render(Loading, { label: 'Loading courses…' });
  assert.match(html, /role="status"/); assert.match(html, /aria-hidden="true"/);
  assert.equal((html.match(/class="skeleton-card"/g) ?? []).length, 3);
});
test('representative empty lists use helpful, role-appropriate actions', async () => {
  const { VerifiedCourseCards } = await load('pages/VerifiedCoursesPage');
  const { MyReviewsContent } = await load('pages/MyReviewsPage');
  const { TeacherFeedbackContent } = await load('pages/TeacherFeedbackPage');
  const { TeacherAnalyticsContent } = await load('pages/TeacherAnalyticsPage');
  const { default: Teachers } = await load('components/TeachersSection');
  assert.match(render(VerifiedCourseCards, { rows: [] }), /Upload Schedule/);
  assert.match(render(MyReviewsContent, { reviews: [], loading: false, error: '', editing: null }), /Review a Course/);
  for (const [Component, props] of [[TeacherFeedbackContent, { groups: [], loading: false, error: '' }],
    [TeacherAnalyticsContent, { data: null, loading: false, error: '' }]]) {
    const html = render(Component, props); assert.match(html, /empty-state/); assert.doesNotMatch(html, /href="\/student/);
  }
  const html = render(Teachers, { teachers: [], loading: false, error: false, hasActiveSearch: true, query: 'unknown', sort: 'NAME_ASC' });
  assert.match(html, /Try another search or filter/); assert.doesNotMatch(html, /href="\/student/);
});
test('list, detail and analytics loading disappear on success or failure; submitting stays disabled', async () => {
  const { CourseDetailsContent } = await load('pages/CourseDetailsPage');
  const { TeacherFeedbackContent } = await load('pages/TeacherFeedbackPage');
  const { SentimentContent } = await load('components/SentimentAnalysis');
  for (const [Component, props] of [[CourseDetailsContent, { course: null }],
    [TeacherFeedbackContent, { groups: [] }], [SentimentContent, { data: null }]]) {
    assert.match(render(Component, { ...props, loading: true, error: '' }), /skeleton-card/);
    assert.doesNotMatch(render(Component, { ...props, loading: false, error: '' }), /skeleton-card/);
    assert.doesNotMatch(render(Component, { ...props, loading: false, error: 'Could not load data.' }), /skeleton-card/);
  }
  const { ReviewSubmitButton } = await load('components/ReviewDraftEditor');
  assert.match(render(ReviewSubmitButton, { draft: { rating: 4, text: 'Useful' }, pending: true }), /disabled=""/);
});
for (const [message, role] of [['reviewSubmitted', 'status'], ['reviewError', 'alert'], ['retryCompleted', 'status']]) {
  test(`${message} toast uses fixed safe copy and can be dismissed`, () => {
    let closed = false;
    const node = ToastNotice({ message, onClose: () => { closed = true; } });
    const html = render(ToastNotice, { message, onClose() {} });
    assert.ok(html.includes(toastMessages[message].text)); assert.ok(html.includes(`role="${role}"`));
    node.props.children[1].props.onClick(); assert.equal(closed, true);
    assert.doesNotMatch(html, /provider|API.key|stack|password|session.id/i);
  });
}
test('unknown route renders a proper 404 and home link', async () => {
  const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/not-a-real-page'] },
    createElement(Routes, {}, createElement(Route, { path: '*', element: createElement(ErrorPage) }))));
  assert.match(html, /404/); assert.match(html, /Page not found/); assert.match(html, /href="\/"/);
  const code = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  assert.match(code, /path="\*" element={<ErrorPage/);
});
test('Access Denied provides a safe home destination without security internals', () => {
  const html = render(AccessDenied); assert.match(html, /403/); assert.match(html, /Access Denied/);
  assert.match(html, /href="\/"/); assert.doesNotMatch(html, /ADMIN|TEACHER|stack|password/);
});
test('major surfaces share both theme palettes and mobile wrapping rules', async () => {
  const css = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8');
  for (const token of ['bg', 'surface', 'surface-muted', 'text', 'text-muted', 'border', 'primary', 'danger', 'success', 'warning'])
    assert.equal((css.match(new RegExp(`--${token}:`, 'g')) ?? []).length, 2);
  const componentCss = css.slice(css.indexOf('font-family:'));
  assert.doesNotMatch(componentCss, /#[0-9a-f]{3,8}\b|color:\s*white|background:\s*white/i);
  for (const rule of ['input, select, textarea', 'overflow-wrap: anywhere', 'minmax(0, 1fr)', '.main-navigation.is-open', '.profile-panel', '.toast-region']) assert.ok(css.includes(rule));
  assert.match(css, /max-width: 768px/); assert.match(css, /min-width: 320px/);
});

// Execute production handlers/effects with a small deterministic hook harness. No live API or email.
const instrument = new Set(['ThemeProvider', 'ToastProvider', 'AccountArea', 'SiteLayout', 'ProtectedRoute',
  'ScheduleUploadForm', 'ReviewDraftEditor', 'PublishedReviewEditor', 'ReviewCorrectionEditor', 'ReviewModerationRetry']);
const runtime = await createServer({ root, server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent', plugins: [{
  name: 'ui-effects', enforce: 'pre',
  transform(code, id) {
    const name = id.replaceAll('\\', '/').split('/').at(-1)?.replace('.tsx', '');
    if (!instrument.has(name)) return;
    return code.replaceAll("from 'react';", "from 'virtual:ui-hooks';")
      .replace("from '../auth/AuthContext';", "from 'virtual:ui-auth';")
      .replace("from 'react-router';", "from 'virtual:ui-router';")
      .replace("from './ToastProvider';", "from 'virtual:ui-toast';")
      .replace("from '../api/scheduleUploads';", "from 'virtual:ui-upload';")
      .replace("from '../api/reviews';", "from 'virtual:ui-reviews';")
      .replace("from '../pages/reviewSubmission';", "from 'virtual:ui-submission';")
      .replace("from '../pages/reviewDraft';", "from 'virtual:ui-draft';");
  },
  resolveId(id) { if (id.startsWith('virtual:ui-')) return '\0' + id; },
  load(id) {
    const prefix = '\0virtual:ui-'; if (!id.startsWith(prefix)) return;
    switch (id.slice(prefix.length)) {
      case 'auth': return 'export const useAuth = () => globalThis.__ui.auth;';
      case 'router': return 'export const Link = "a"; export const Outlet = () => null; export const useLocation = () => globalThis.__ui.location;';
      case 'toast': return 'export const useToast = () => message => globalThis.__ui.notifications.push(message);';
      case 'upload': return 'export const uploadSchedule = (...args) => globalThis.__ui.action(...args);';
      case 'reviews': return 'export const correctReview = (...args) => globalThis.__ui.action(...args); export const updatePublishedReview = correctReview; export const retryReviewModeration = correctReview; export class ModerationRetryCooldown extends Error {}';
      case 'submission': return 'export const completeDraft = () => true; export const createReviewSubmission = () => ({ pending: false, submit: (...args) => globalThis.__ui.action(...args) });';
      case 'draft': return 'export const FEEDBACK_MAX_LENGTH = 2000; export const loadDraft = () => ({ rating: 4, text: "Useful feedback" }); export const saveDraft = () => true; export const validDraft = x => x;';
      case 'hooks': return `
        export { createContext } from 'react';
        export const useContext = () => globalThis.__ui.context;
        export function useState(initial) {
          const h = globalThis.__ui; const i = h.cursor++;
          if (!(i in h.values)) h.values[i] = typeof initial === 'function' ? initial() : initial;
          return [h.values[i], value => { h.values[i] = typeof value === 'function' ? value(h.values[i]) : value; }];
        }
        export function useRef(value) { const h = globalThis.__ui; const i = h.cursor++; return h.values[i] ??= { current: value }; }
        export function useEffect(effect, deps) {
          const h = globalThis.__ui; const i = h.cursor++;
          const old = h.effects[i];
          if (!old || !deps || deps.some((d, n) => !Object.is(d, old.deps[n]))) h.pending.push(() => {
            old?.cleanup?.(); h.effects[i] = { deps, cleanup: effect() };
          });
        }
        export function useCallback(fn, deps) { const h = globalThis.__ui; const i = h.cursor++; return h.values[i] ??= fn; }
      `;
    }
  },
}] });
after(() => runtime.close());
const runLoad = name => runtime.ssrLoadModule(`/src/components/${name}.tsx`);
function elements(node) {
  if (!node || typeof node !== 'object') return [];
  return [node, ...[node.props?.children].flat(Infinity).flatMap(elements)];
}
const find = (node, predicate) => elements(node).find(predicate);
function harness(Component, props = {}, roles = ['STUDENT']) {
  const h = { cursor: 0, values: [], effects: [], pending: [], notifications: [], location: { key: '1', pathname: '/student' },
    auth: { currentUser: { email: 'test@feedback.test', roles, emailVerified: true }, checking: false, sessionError: false, logout: async () => { h.loggedOut = true; } },
    action: async () => ({ reviewId: 1, draftCleared: true, status: 'VALID' }) };
  const run = () => { globalThis.__ui = h; h.cursor = 0; const node = Component(props); h.pending.splice(0).forEach(fn => fn()); return node; };
  return { h, run, close: () => { h.effects.forEach(effect => effect?.cleanup?.()); delete globalThis.__ui; } };
}

test('theme defaults to light, toggles DOM/storage, restores both preferences and tolerates blocked storage', async () => {
  const { ThemeProvider, ThemeToggle, readTheme } = await runLoad('ThemeProvider');
  const savedDocument = globalThis.document, savedStorage = globalThis.localStorage;
  const stored = new Map(); globalThis.document = { documentElement: { dataset: {} } };
  globalThis.localStorage = { getItem: key => stored.get(key), setItem: (key, value) => stored.set(key, value) };
  const instances = [];
  try {
    const app = harness(ThemeProvider); instances.push(app); let node = app.run();
    assert.equal(node.props.value.theme, 'light'); assert.equal(document.documentElement.dataset.theme, 'light');
    app.h.context = node.props.value; ThemeToggle().props.onClick(); node = app.run();
    assert.equal(node.props.value.theme, 'dark'); assert.equal(document.documentElement.dataset.theme, 'dark');
    assert.equal(stored.get('feedback-theme'), 'dark');
    const reload = harness(ThemeProvider); instances.push(reload); node = reload.run(); assert.equal(node.props.value.theme, 'dark');
    reload.h.context = node.props.value; ThemeToggle().props.onClick(); reload.run();
    const lightReload = harness(ThemeProvider); instances.push(lightReload); assert.equal(lightReload.run().props.value.theme, 'light');
    globalThis.localStorage = { getItem() { throw Error('blocked'); }, setItem() { throw Error('blocked'); } };
    assert.equal(readTheme(), 'light'); lightReload.run().props.value.toggleTheme(); lightReload.run();
    assert.equal(document.documentElement.dataset.theme, 'dark');
  } finally { instances.forEach(app => app.close()); globalThis.document = savedDocument; globalThis.localStorage = savedStorage; }
});

test('toast provider replaces duplicates, auto-closes, and cancels timers on dismissal/unmount', async () => {
  const { ToastProvider } = await runLoad('ToastProvider');
  const savedWindow = globalThis.window; let next = 0; const timers = new Map();
  globalThis.window = { setTimeout: fn => { timers.set(++next, fn); return next; }, clearTimeout: id => timers.delete(id) };
  const app = harness(ToastProvider);
  try {
    let node = app.run(); node.props.value('reviewSubmitted'); node = app.run();
    assert.equal(timers.size, 1); assert.ok(find(node, n => n.props?.message === 'reviewSubmitted'));
    node.props.value('reviewSubmitted'); node = app.run(); assert.equal(timers.size, 1);
    find(node, n => n.props?.message).props.onClose(); node = app.run(); assert.equal(timers.size, 0);
    node.props.value('reviewError'); app.run(); [...timers.values()][0](); node = app.run();
    assert.equal(find(node, n => n.props?.message), undefined);
    node.props.value('retryCompleted'); app.run(); app.close(); assert.equal(timers.size, 0);
  } finally { app.close(); globalThis.window = savedWindow; }
});

for (const roles of [['STUDENT'], ['TEACHER'], ['ADMIN'], ['STUDENT', 'TEACHER'], []]) {
  test(`profile disclosure respects roles ${roles.join('+') || 'none'}, theme, logout, Escape/outside/navigation`, async () => {
    const { default: Account } = await runLoad('AccountArea');
    const savedDocument = globalThis.document; const listeners = new Map();
    globalThis.document = { addEventListener: (name, fn) => listeners.set(name, fn), removeEventListener: name => listeners.delete(name) };
    const app = harness(Account, {}, roles);
    try {
      let node = app.run(); const open = () => { find(node, n => n.props?.['aria-controls'] === 'profile-panel').props.onClick(); node = app.run(); };
      assert.ok(find(node, n => n.props?.children === 'My account')); assert.equal(find(node, n => n.props?.id === 'profile-panel'), undefined);
      open(); const links = elements(node).filter(n => n.props?.to).map(n => n.props.to);
      for (const role of ['STUDENT', 'TEACHER', 'ADMIN']) assert.equal(links.includes('/' + role.toLowerCase()), roles.includes(role));
      assert.equal(links.includes('/teacher/analytics'), roles.includes('TEACHER'));
      assert.ok(find(node, n => n.type?.name === 'ThemeToggle'));
      let focused = false; find(node, n => n.props?.['aria-controls'] === 'profile-panel').props.ref.current = { focus: () => { focused = true; } };
      listeners.get('keydown')({ key: 'Escape' }); node = app.run(); assert.equal(focused, true); assert.equal(find(node, n => n.props?.id === 'profile-panel'), undefined);
      open(); listeners.get('pointerdown')({ target: {} }); node = app.run(); assert.equal(find(node, n => n.props?.id === 'profile-panel'), undefined);
      open(); await find(node, n => n.type === 'button' && n.props?.children === 'Sign Out').props.onClick(); assert.equal(app.h.loggedOut, true);
      app.h.location.key = '2'; app.run(); node = app.run(); assert.equal(find(node, n => n.props?.id === 'profile-panel'), undefined);
    } finally { app.close(); globalThis.document = savedDocument; }
  });
}
test('mobile navigation toggles, closes on link/Escape/route and restores focus', async () => {
  const { default: Layout } = await runLoad('SiteLayout'); const app = harness(Layout);
  try {
    let node = app.run(); const button = () => find(node, n => n.props?.['aria-controls'] === 'main-navigation');
    assert.equal(button().props['aria-expanded'], false); button().props.onClick(); node = app.run();
    assert.equal(button().props['aria-expanded'], true);
    find(node, n => n.props?.id === 'main-navigation').props.onClick(); node = app.run(); assert.equal(button().props['aria-expanded'], false);
    button().props.onClick(); node = app.run(); let focused = false; button().props.ref.current = { focus: () => { focused = true; } };
    find(node, n => n.props?.onKeyDown).props.onKeyDown({ key: 'Escape' }); node = app.run(); assert.equal(focused, true);
    button().props.onClick(); app.run(); app.h.location.pathname = '/courses/2'; app.run(); node = app.run(); assert.equal(button().props['aria-expanded'], false);
  } finally { app.close(); }
});
test('guard distinguishes loading, failed session, signed out, forbidden and allowed multi-role access', async () => {
  const { default: Guard } = await runLoad('ProtectedRoute'); const app = harness(Guard, { requiredRole: 'ADMIN', children: 'Private content' });
  try {
    assert.ok(find(app.run(), n => n.type?.name === 'AccessDenied'));
    app.h.auth.currentUser = null; assert.ok(find(app.run(), n => n.props?.children === 'Sign In required'));
    app.h.auth.sessionError = true; assert.ok(find(app.run(), n => n.props?.children === 'Could not check your session.'));
    app.h.auth.checking = true; assert.ok(find(app.run(), n => n.props?.label === 'Checking session…'));
    app.h.auth.checking = false; app.h.auth.sessionError = false; app.h.auth.currentUser = { roles: ['STUDENT', 'ADMIN'], emailVerified: true };
    assert.equal(app.run(), 'Private content');
  } finally { app.close(); }
});

test('upload handler emits a single safe success/invalid/error toast and preserves validation', async () => {
  const { default: Upload } = await runLoad('ScheduleUploadForm'); const app = harness(Upload);
  try {
    let node = app.run(); find(node, n => n.props?.type === 'file').props.onChange({ target: { files: [{ name: 'schedule.pdf', size: 100 }] } });
    node = app.run(); await find(node, n => n.type === 'form').props.onSubmit({ preventDefault() {} });
    assert.deepEqual(app.h.notifications, ['uploadSuccess']);
    for (const outcome of ['INVALID', 'error']) {
      app.h.action = async () => { if (outcome === 'error') throw Error('secret-provider-raw'); return { status: outcome }; };
      node = app.run(); find(node, n => n.props?.type === 'file').props.onChange({ target: { files: [{ name: 'schedule.pdf', size: 100 }] } });
      node = app.run(); await find(node, n => n.type === 'form').props.onSubmit({ preventDefault() {} });
      assert.equal(app.h.notifications.at(-1), outcome === 'INVALID' ? 'uploadInvalid' : 'uploadError');
    }
  } finally { app.close(); }
});
for (const [component, message, actionProp] of [
  ['ReviewDraftEditor', 'reviewSubmitted', 'onSubmit'], ['PublishedReviewEditor', 'reviewUpdated', 'onSubmit'],
  ['ReviewCorrectionEditor', 'correctionSubmitted', 'onSubmit'], ['ReviewModerationRetry', 'retryCompleted', 'onRetry'],
]) {
  test(`${component} only notifies after action success; errors never become provider text in toasts`, async () => {
    const { default: Component } = await runLoad(component);
    const savedWindow = globalThis.window; globalThis.window = { setInterval: () => 1, clearInterval() {} };
    const app = harness(Component, { target: {}, initialRating: 4, initialText: 'Useful feedback', reviewId: 1, review: { reviewId: 1, status: 'SUBMITTED', retryAfterSeconds: 0 }, onSaved() {}, onUpdated() {} });
    try {
      let node = app.run(); if (component === 'ReviewCorrectionEditor') { node.props.onClick(); node = app.run(); }
      assert.deepEqual(app.h.notifications, []);
      const submit = find(node, n => typeof n.props?.[actionProp] === 'function'); assert.ok(submit);
      await submit.props[actionProp](); await new Promise(resolve => setImmediate(resolve));
      assert.deepEqual(app.h.notifications, [message]);
    } finally { app.close(); }
    const failing = harness(Component, { target: {}, initialRating: 4, initialText: 'Useful feedback', reviewId: 1,
      review: { reviewId: 1, status: 'SUBMITTED', retryAfterSeconds: 0 }, onSaved() {}, onUpdated() {} });
    try {
      failing.h.action = async () => { throw new Error('raw-sensitive-provider-error'); };
      let node = failing.run(); if (component === 'ReviewCorrectionEditor') { node.props.onClick(); node = failing.run(); }
      await find(node, n => typeof n.props?.[actionProp] === 'function').props[actionProp]();
      await new Promise(resolve => setImmediate(resolve));
      assert.deepEqual(failing.h.notifications, ['reviewError']);
    } finally { failing.close(); globalThis.window = savedWindow; }
  });
}
