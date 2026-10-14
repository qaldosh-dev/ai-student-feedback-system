import { test, after, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

const root = fileURLToPath(new URL('../', import.meta.url));
const server = await createServer({ root, server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(() => server.close());
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
const { TeacherAnalysisSections, TeacherAnalyticsContent } = await server.ssrLoadModule('/src/pages/TeacherAnalyticsPage.tsx');
const { SentimentContent } = await server.ssrLoadModule('/src/components/SentimentAnalysis.tsx');
const { TopicsContent } = await server.ssrLoadModule('/src/components/TopicsAnalysis.tsx');
const { SummaryContent } = await server.ssrLoadModule('/src/components/SummaryAnalysis.tsx');
const data = { courses: [{ courseId: 10, courseCode: 'ABC 100', courseName: 'Subject', academicTerms: ['Fall 2026', '2026-2027 1 term'] }],
  selectedCourseId: 10, selectedAcademicTerm: 'Fall 2026', hasPublishedFeedback: true, privacyThresholdMet: false };
const props = { data, loading: false, error: '', privacyBlocked: false, onPrivacyProtected() {} };
const render = overrides => renderToStaticMarkup(createElement(TeacherAnalysisSections, { ...props, ...overrides }));
function elements(node) {
  if (!node || typeof node !== 'object') return [];
  if (Array.isArray(node)) return node.flatMap(elements);
  return [node, ...elements(node.props?.children)];
}
let generic;
for (const count of [0, 1, 2]) test(`${count} fixture reviews: same single privacy notice, no count, AI section or Retry`, () => {
  // Actual count is test-only and deliberately not part of the response contract.
  let calls = 0; globalThis.fetch = () => { calls++; throw new Error('Unexpected request'); };
  const html = render({ data: { ...data, hasPublishedFeedback: count > 0 } });
  generic ??= html; assert.equal(html, generic);
  assert.equal((html.match(/<h2/g) ?? []).length, 1); assert.match(html, /Analytics Privacy/);
  assert.match(html, /Not enough published feedback to display analytics while protecting student privacy/);
  assert.doesNotMatch(html, /Sentiment Analysis|Common Topics|Common Concerns|AI Summary|Retry|\b[0123]\b/);
  assert.equal(calls, 0);
});

test('exactly three or more: server eligibility mounts each existing analysis once', () => {
  const tree = TeacherAnalysisSections({ ...props, data: { ...data, privacyThresholdMet: true } });
  assert.deepEqual(tree.props.children.map(child => child.type.name), ['SentimentAnalysis', 'TopicsAnalysis', 'SummaryAnalysis']);
  for (const child of tree.props.children) {
    assert.equal(child.props.courseId, 10); assert.equal(child.props.term, 'Fall 2026');
    assert.equal(child.props.onPrivacyProtected, props.onPrivacyProtected);
  }
});

test('loading/new scope or error hides all old eligible analytics immediately', () => {
  assert.equal(render({ data: { ...data, privacyThresholdMet: true }, loading: true }), '');
  assert.equal(render({ data: { ...data, privacyThresholdMet: true }, error: 'Unavailable' }), '');
});

test('late endpoint privacy decision closes all analyses with one message', () => {
  const html = render({ data: { ...data, privacyThresholdMet: true }, privacyBlocked: true });
  assert.match(html, /Analytics Privacy/); assert.doesNotMatch(html, /Retry|Sentiment|Topics|Summary/);
});

test('missing eligibility fails closed and private extras are not displayed', () => {
  const html = render({ data: { ...data, privacyThresholdMet: undefined, actualPublishedReviewCount: 2,
    email: 'private@example.test', authorId: 12345, reviewText: 'SECRET_TEXT', groupSection: '15-P' } });
  assert.match(html, /Analytics Privacy/);
  assert.doesNotMatch(html, /private@example|12345|SECRET_TEXT|15-P|\b2\b/);
});

for (const Content of [SentimentContent, TopicsContent, SummaryContent]) test(`${Content.name}: direct privacy response has no Retry or stale output`, () => {
  const html = renderToStaticMarkup(createElement(Content, { data: { status: 'PRIVACY_PROTECTED', privacyThresholdMet: false,
    analysisAvailable: false, analyzedReviewCount: null, summary: 'STALE', topics: ['STALE'], concerns: ['STALE'] },
    loading: false, onRetry() { throw new Error('Privacy must not offer retry'); } }));
  assert.match(html, /Analytics Privacy/); assert.doesNotMatch(html, /Retry|STALE|temporarily unavailable/);
});

test('filters stay usable without revealing small-sample availability', () => {
  const html = renderToStaticMarkup(createElement(TeacherAnalyticsContent, { data, loading: false, error: '', onSelection() {}, onRetry() {} }));
  assert.equal((html.match(/<select/g) ?? []).length, 2);
  assert.match(html, /Fall 2026/); assert.match(html, /2026-2027 1 term/);
  assert.doesNotMatch(html, /No published feedback|Published feedback is available|Retry/);
});

test('production scope effect: eligible/ineligible switches, stale response, refresh and endpoint revocation', async () => {
  const runtime = await createServer({ root, server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent', plugins: [{
    name: 'privacy-effects', enforce: 'pre',
    transform(code, id) {
      const path = id.replaceAll('\\', '/');
      if (!path.endsWith('/pages/TeacherAnalyticsPage.tsx') && !/\/components\/(Sentiment|Topics|Summary)Analysis\.tsx$/.test(path)) return;
      return code.replace("from 'react';", "from 'virtual:privacy-hooks';")
        .replace("from '../auth/AuthContext';", "from 'virtual:privacy-auth';")
        .replace("from 'react-router';", "from 'virtual:privacy-router';");
    },
    resolveId(id) { if (id.startsWith('virtual:privacy-')) return '\0' + id; },
    load(id) {
      if (id === '\0virtual:privacy-auth') return 'export const useAuth = () => ({ currentUser: { id: 1, linkedTeacher: { id: 2 } }, expireSession() {} });';
      if (id === '\0virtual:privacy-router') return 'export const Link = () => null; export const useSearchParams = () => [new URLSearchParams(globalThis.__us36Query), () => {}];';
      if (id === '\0virtual:privacy-hooks') return `
        export function useState(initial) {
          const state = globalThis.__us36Hooks; const i = state.cursor++;
          if (!(i in state.values)) state.values[i] = initial;
          return [state.values[i], value => { state.updates++; state.values[i] = typeof value === 'function' ? value(state.values[i]) : value; }];
        }
        export const useCallback = fn => fn;
        export function useEffect(effect) { globalThis.__us36Hooks.effects.push(effect); }
      `;
    },
  }] });
  const stops = [];
  try {
    const { default: Page, TeacherAnalysisSections: Gate } = await runtime.ssrLoadModule('/src/pages/TeacherAnalyticsPage.tsx');
    const calls = [];
    globalThis.fetch = (url, options) => new Promise(resolve => calls.push({ url, options, resolve }));
    const state = () => ({ cursor: 0, values: [], effects: [], updates: 0 });
    const run = (Component, props, store) => { store.cursor = 0; store.effects = []; globalThis.__us36Hooks = store; return Component(props); };
    const start = query => {
      globalThis.__us36Query = query;
      const node = Page(); const store = state();
      run(node.type, node.props, store); const stop = store.effects[0](); stops.push(stop);
      return { node, store, stop, call: calls.at(-1) };
    };
    const gate = session => {
      const tree = run(session.node.type, session.node.props, session.store);
      const node = elements(tree).find(child => child.type === Gate);
      return Gate(node.props);
    };
    const finish = async (call, result) => {
      call.resolve({ ok: true, status: 200, json: async () => result });
      await new Promise(resolve => setImmediate(resolve));
    };
    const old = start('courseId=10&term=Fall+2026');
    const eligible = { ...data, privacyThresholdMet: true };
    await finish(old.call, eligible); assert.equal(gate(old).props.children.length, 3);
    old.stop();
    const pending = start('courseId=20&term=Fall+2026');
    assert.equal(gate(pending), null); pending.stop(); const updates = pending.store.updates;
    const current = start('courseId=10&term=2026-2027+1+term');
    assert.notEqual(current.node.key, old.node.key);
    await finish(current.call, { ...data, selectedAcademicTerm: '2026-2027 1 term' });
    await finish(pending.call, { ...eligible, selectedCourseId: 20 });
    assert.equal(pending.store.updates, updates); assert.ok(pending.call.options.signal.aborted);
    assert.match(renderToStaticMarkup(gate(current)), /Analytics Privacy/);
    assert.ok(calls.every(call => call.url.startsWith('/api/teacher/analytics/scope')));
    current.stop();
    const refreshed = start('courseId=10&term=2026-2027+1+term');
    assert.equal(refreshed.call.url, current.call.url);
    await finish(refreshed.call, eligible);
    const analysisNodes = gate(refreshed).props.children;
    assert.equal(analysisNodes.length, 3);
    for (const child of analysisNodes) {
      const childState = state(); run(child.type, child.props, childState);
      const stop = childState.effects[0](); stops.push(stop);
      await finish(calls.at(-1), { privacyThresholdMet: false, status: 'PRIVACY_PROTECTED' });
      assert.match(renderToStaticMarkup(gate(refreshed)), /Analytics Privacy/); stop();
    }
    assert.deepEqual(calls.slice(-3).map(call => new URL(call.url, 'http://localhost').pathname),
      ['/api/teacher/analytics/sentiment', '/api/teacher/analytics/topics', '/api/teacher/analytics/summary']);
  } finally {
    stops.forEach(stop => stop()); delete globalThis.__us36Hooks; delete globalThis.__us36Query;
    await runtime.close();
  }
});
