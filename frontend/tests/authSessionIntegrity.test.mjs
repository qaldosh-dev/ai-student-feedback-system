import { test, after, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router';

const root = fileURLToPath(new URL('../', import.meta.url));
const instrument = /\/(AuthContext|ReviewDraftEditor|AccountArea|ProtectedRoute|RegisterPage)\.tsx$/;
const runtime = await createServer({ root, server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent', plugins: [{
  name: 'auth-session-handlers', enforce: 'pre',
  transform(code, id) {
    if (!instrument.test(id.replaceAll('\\', '/'))) return;
    return code.replaceAll("from 'react';", "from 'virtual:session-hooks';")
      .replace("from './ToastProvider';", "from 'virtual:session-toast';")
      .replace("from './ThemeProvider';", "from 'virtual:session-theme';")
      .replace("from 'react-router';", "from 'virtual:session-router';");
  },
  resolveId(id) { if (id.startsWith('virtual:session-')) return '\0' + id; },
  load(id) {
    if (id === '\0virtual:session-toast') return 'export const useToast = () => () => {};';
    if (id === '\0virtual:session-theme') return 'export const ThemeToggle = () => null;';
    if (id === '\0virtual:session-router') return 'export const Link = "a"; export const useLocation = () => ({ key: "1" }); export const useNavigate = () => () => {};';
    if (id !== '\0virtual:session-hooks') return;
    return `
      export { createContext } from 'react';
      export const useContext = () => globalThis.__qaContext;
      export function useState(initial) {
        const h = globalThis.__qaHooks; const i = h.cursor++;
        if (!(i in h.values)) h.values[i] = typeof initial === 'function' ? initial() : initial;
        return [h.values[i], next => { h.values[i] = typeof next === 'function' ? next(h.values[i]) : next; }];
      }
      export function useRef(initial) { const h = globalThis.__qaHooks; const i = h.cursor++; return h.values[i] ??= { current: initial }; }
      export function useCallback(fn) { const h = globalThis.__qaHooks; const i = h.cursor++; return h.values[i] ??= fn; }
      export function useEffect(fn, deps) {
        const h = globalThis.__qaHooks; const i = h.cursor++; const old = h.effects[i];
        if (!old || !deps || deps.some((value, n) => !Object.is(value, old.deps[n]))) h.pending.push(() => {
          old?.cleanup?.(); h.effects[i] = { deps, cleanup: fn() };
        });
      }
    `;
  },
}] });
after(() => runtime.close());
const { AuthProvider } = await runtime.ssrLoadModule('/src/auth/AuthContext.tsx');
const { default: Editor } = await runtime.ssrLoadModule('/src/components/ReviewDraftEditor.tsx');
const { default: AccountArea } = await runtime.ssrLoadModule('/src/components/AccountArea.tsx');
const { default: Guard } = await runtime.ssrLoadModule('/src/components/ProtectedRoute.tsx');
const { default: Register } = await runtime.ssrLoadModule('/src/pages/RegisterPage.tsx');
const { getCurrentUser, reviewSessionHeaders, AccountSessionExpired } = await runtime.ssrLoadModule('/src/api/auth.ts');
const { createReviewSubmission } = await runtime.ssrLoadModule('/src/pages/reviewSubmission.ts');
const { saveDraft, loadDraft } = await runtime.ssrLoadModule('/src/pages/reviewDraft.ts');
const a = { id: 101, email: 'a@feedback.test', roles: ['STUDENT'], emailVerified: true, linkedTeacher: null };
const b = { ...a, id: 202, email: 'b@feedback.test' };
const target = { courseOfferingId: 7, academicTerm: '2026-2027 1 term' };
const draft = { rating: 4, text: 'Account A private draft' };
const original = { fetch: globalThis.fetch, window: globalThis.window, document: globalThis.document, localStorage: globalThis.localStorage, sessionStorage: globalThis.sessionStorage, FormData: globalThis.FormData };
let instances, storage, broadcasts, sessionAccount, sessionToken, calls, forcedStatus;
function memory() {
  const data = new Map();
  return { get length() { return data.size; }, key: i => [...data.keys()][i], getItem: key => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) };
}
function response(status, body, headers = {}) { return { status, ok: status >= 200 && status < 300, json: async () => body, headers: new Headers(headers) }; }
beforeEach(() => {
  instances = []; storage = memory(); broadcasts = []; calls = []; forcedStatus = null;
  sessionAccount = a; sessionToken = 'synthetic-session-a';
  globalThis.sessionStorage = storage;
  globalThis.localStorage = { setItem: (key, value) => broadcasts.push({ key, value }) };
  globalThis.window = new EventTarget();
  globalThis.document = Object.assign(new EventTarget(), { visibilityState: 'visible' });
  globalThis.fetch = async (url, options = {}) => {
    calls.push({ url, options });
    if (url === '/api/auth/me') return sessionAccount ? response(200, { ...sessionAccount }, { 'X-CSRF-TOKEN': sessionToken }) : response(401);
    if (url === '/api/auth/csrf') return response(200, { headerName: 'X-CSRF-TOKEN', token: sessionToken });
    if (url === '/api/auth/login') return response(200, sessionAccount);
    if (url === '/api/auth/logout') { sessionAccount = null; return response(204); }
    if (url === '/api/auth/register') return response(202, { message: 'Check email' });
    if (url === '/api/student/reviews') {
      if (forcedStatus === 401 || !sessionAccount) return response(401);
      if (options.headers['X-CSRF-TOKEN'] !== sessionToken) return response(403, { message: 'Request could not be verified. Please try again.' });
      return response(201, { reviewId: 1, submitted: true, status: 'SUBMITTED', ...target });
    }
    throw Error('Unexpected test request');
  };
});
afterEach(() => { instances.forEach(h => h.close()); Object.assign(globalThis, original); delete globalThis.__qaHooks; delete globalThis.__qaContext; });
function mount(Component, props = {}) {
  const h = { cursor: 0, values: [], effects: [], pending: [] };
  const instance = { run() { globalThis.__qaHooks = h; h.cursor = 0; const node = Component(props); h.pending.splice(0).forEach(fn => fn()); return node; },
    close() { h.effects.forEach(effect => effect?.cleanup?.()); } };
  instances.push(instance); return instance;
}
const tick = () => new Promise(resolve => setImmediate(resolve));
async function account() {
  const provider = mount(AuthProvider); provider.run(); await tick();
  const state = () => globalThis.__qaContext = provider.run().props.value;
  state(); return { provider, state };
}
function changed() { const event = new Event('storage'); event.key = 'feedback-auth-change'; window.dispatchEvent(event); }
function nodes(node) { return !node || typeof node !== 'object' ? [] : [node, ...[node.props?.children].flat(Infinity).flatMap(nodes)]; }
const markup = node => renderToStaticMarkup(createElement(MemoryRouter, {}, node));

test('cross-tab account switch clears stale identity/draft and publishes no credentials', async () => {
  const app = await account(); assert.equal(app.state().currentUser.id, a.id);
  saveDraft({ ...target, ownerId: a.id }, draft);
  sessionAccount = b; sessionToken = 'synthetic-session-b'; changed();
  assert.equal(app.state().currentUser, null); assert.equal(app.state().checking, true);
  assert.doesNotMatch(markup(mount(AccountArea).run()), /a@feedback|Sign Out/);
  await tick(); assert.equal(app.state().currentUser.id, b.id);
  assert.equal(loadDraft({ ...target, ownerId: a.id }, null).text, '');
  assert.equal(loadDraft({ ...target, ownerId: b.id }, null).text, '');
  await app.state().login('b@feedback.test', 'synthetic-only');
  assert.ok(broadcasts.length > 0);
  for (const event of broadcasts) assert.doesNotMatch(event.value, /feedback.test|synthetic|password/);
});

test('missed tab notification cannot refresh stale editor CSRF into account B token', async () => {
  const user = await getCurrentUser(new AbortController().signal);
  const action = createReviewSubmission({ ...target, ownerId: a.id }, reviewSessionHeaders(user));
  sessionAccount = b; sessionToken = 'synthetic-session-b';
  await assert.rejects(action.submit(draft), AccountSessionExpired);
  const sent = calls.find(call => call.url === '/api/student/reviews');
  assert.equal(sent.options.headers['X-CSRF-TOKEN'], 'synthetic-session-a');
  assert.deepEqual(JSON.parse(sent.options.body), { ...target, ...draft });
  assert.equal(calls.filter(call => call.url === '/api/auth/csrf').length, 0);
});

test('same-account tab notification preserves scoped draft and normal submission', async () => {
  const app = await account(); saveDraft({ ...target, ownerId: a.id }, draft);
  changed(); app.state(); await tick();
  assert.equal(app.state().currentUser.id, a.id);
  assert.deepEqual(loadDraft({ ...target, ownerId: a.id }, null), draft);
  const action = createReviewSubmission({ ...target, ownerId: a.id }, reviewSessionHeaders(app.state().currentUser));
  assert.equal((await action.submit(draft)).reviewId, 1);
});

test('same-account focus recheck does not unmount protected forms', async () => {
  const app = await account(); window.dispatchEvent(new Event('focus'));
  assert.equal(app.state().checking, false); assert.equal(app.state().currentUser.id, a.id);
  assert.equal(mount(Guard, { requiredRole: 'STUDENT', children: 'existing form' }).run(), 'existing form');
  await tick(); assert.equal(app.state().checking, false); assert.equal(app.state().currentUser.id, a.id);
});

test('late me response cannot restore the old account after a tab notification', async () => {
  const app = await account(); const normalFetch = globalThis.fetch; let finish;
  globalThis.fetch = () => new Promise(resolve => { finish = resolve; });
  window.dispatchEvent(new Event('focus')); app.state();
  globalThis.fetch = normalFetch; sessionAccount = b; sessionToken = 'synthetic-session-b';
  changed(); app.state(); await tick(); assert.equal(app.state().currentUser.id, b.id);
  finish(response(200, a, { 'X-CSRF-TOKEN': 'synthetic-session-a' })); await tick();
  assert.equal(app.state().currentUser.id, b.id);
});

test('review 401 clears centralized auth, removes signed-in menu and exposes Sign In', async () => {
  const app = await account();
  saveDraft({ ...target, ownerId: a.id }, draft);
  const editor = mount(Editor, { target, urlRating: null, account: app.state().currentUser, onSessionExpired: app.state().expireSession });
  const view = editor.run(); forcedStatus = 401;
  await nodes(view).find(node => node.props?.onSubmit).props.onSubmit();
  assert.equal(app.state().currentUser, null);
  assert.equal(loadDraft({ ...target, ownerId: a.id }, null).text, '');
  assert.match(markup(mount(Guard, { requiredRole: 'STUDENT', children: 'private editor' }).run()), /Sign In required/);
  const menu = markup(mount(AccountArea).run());
  assert.match(menu, /Sign In/); assert.doesNotMatch(menu, /a@feedback|My account|Sign Out/);
});

test('missed logout/account switch notification still clears editor on expired CSRF', async () => {
  const app = await account(); saveDraft({ ...target, ownerId: a.id }, draft);
  const editor = mount(Editor, { target, urlRating: null, account: app.state().currentUser, onSessionExpired: app.state().expireSession });
  const view = editor.run(); sessionAccount = b; sessionToken = 'synthetic-session-b';
  await nodes(view).find(node => node.props?.onSubmit).props.onSubmit();
  assert.equal(app.state().currentUser, null);
  assert.match(markup(mount(AccountArea).run()), /Sign In/);
});

test('logout notification and focus fallback both clear expired session', async () => {
  for (const event of ['storage', 'focus']) {
    sessionAccount = a; const app = await account(); sessionAccount = null;
    if (event === 'storage') changed(); else window.dispatchEvent(new Event('focus'));
    app.state(); await tick(); assert.equal(app.state().currentUser, null); assert.equal(app.state().checking, false);
    app.provider.close();
  }
});

test('account draft namespaces and legacy keys cannot leak text between users', () => {
  storage.setItem('review-draft:' + JSON.stringify([target.courseOfferingId, target.academicTerm]), JSON.stringify(draft));
  assert.equal(loadDraft({ ...target, ownerId: a.id }, null).text, '');
  saveDraft({ ...target, ownerId: a.id }, draft);
  assert.equal(loadDraft({ ...target, ownerId: b.id }, null).text, '');
  assert.deepEqual(loadDraft({ ...target, ownerId: a.id }, null), draft);
});

test('missing session binding fails closed without posting a review', async () => {
  await assert.rejects(createReviewSubmission(target, undefined).submit(draft), AccountSessionExpired);
  assert.equal(calls.length, 0);
});

test('registration rejects blank password in UI but sends significant spaces unchanged', async () => {
  let password = '        ';
  globalThis.FormData = class { get(key) { return key === 'email' ? 'new@stu.sdu.edu.kz' : password; } };
  const page = mount(Register);
  const event = { preventDefault() {}, currentTarget: { elements: { namedItem: () => ({ value: '' }) } } };
  await nodes(page.run()).find(node => node.type === 'form').props.onSubmit(event);
  assert.equal(calls.length, 0); assert.match(markup(page.run()), /Password must not be blank/);
  password = '  valid password  ';
  await nodes(page.run()).find(node => node.type === 'form').props.onSubmit(event);
  assert.equal(JSON.parse(calls.find(call => call.url === '/api/auth/register').options.body).password, password);
});
