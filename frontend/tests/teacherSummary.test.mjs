import { test, after, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)), server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(() => server.close());
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
const { SummaryContent } = await server.ssrLoadModule('/src/components/SummaryAnalysis.tsx');
const { getTeacherSummary } = await server.ssrLoadModule('/src/api/teacherSummary.ts');
const data = { courseId: 10, courseCode: 'ABC 100', courseName: 'Subject', academicTerm: 'term A',
  status: 'AVAILABLE', privacyThresholdMet: true, analysisAvailable: true, enoughFeedback: true, analyzedReviewCount: 3,
  summary: 'Feedback highlights useful explanations. Some responses mention short deadlines.' };
const props = { data, loading: false, onRetry() {} };
const render = (overrides = {}) => renderToStaticMarkup(createElement(SummaryContent, { ...props, ...overrides }));
function elements(node) {
  if (!node || typeof node !== 'object') return [];
  if (Array.isArray(node)) return node.flatMap(elements);
  return [node, ...elements(node.props?.children)];
}

test('AI Summary renders one prose paragraph, not JSON, topics or sentiment', () => {
  const html = render();
  assert.match(html, /<h2 id="summary-heading">AI Summary<\/h2>/);
  assert.ok(html.includes(`<p>${data.summary}</p>`));
  assert.doesNotMatch(html, /<li|<ul|<pre|<code|<canvas|<svg|Sentiment|Common Topics|Common Concerns|%|\{"summary"/);
});

test('loading hides even a supplied stale summary', () => {
  const html = render({ loading: true });
  assert.match(html, /Generating AI summary/); assert.match(html, /role="status"/);
  assert.ok(!html.includes(data.summary));
});

test('zero feedback does not generate fake summary', () => {
  const html = render({ data: { ...data, status: 'PRIVACY_PROTECTED', privacyThresholdMet: false, analysisAvailable: false } });
  assert.match(html, /Not enough published feedback to display analytics while protecting student privacy/);
  assert.ok(!html.includes(data.summary)); assert.doesNotMatch(html, /Retry/);
});

test('one feedback is not paraphrased as an aggregate', () => {
  const html = render({ data: { ...data, status: 'PRIVACY_PROTECTED', privacyThresholdMet: false, enoughFeedback: false } });
  assert.match(html, /Not enough published feedback to display analytics while protecting student privacy/);
  assert.ok(!html.includes(data.summary)); assert.doesNotMatch(html, /Retry/);
});

test('failure or missing result offers Retry without stale summary or diagnostics', () => {
  for (const result of [null, { ...data, status: 'UNAVAILABLE', analysisAvailable: false, error: 'SECRET_KEY' }]) {
    const html = render({ data: result });
    assert.match(html, /AI summary is temporarily unavailable/); assert.match(html, /Retry/);
    assert.ok(!html.includes(data.summary)); assert.doesNotMatch(html, /SECRET_KEY/);
  }
});

test('Retry invokes only the supplied summary action', () => {
  let calls = 0;
  const tree = SummaryContent({ ...props, data: null, onRetry: () => calls++ });
  elements(tree).find(node => node.type === 'button').props.onClick(); assert.equal(calls, 1);
});

test('extra private fields are not rendered and provider prose is HTML escaped', () => {
  const html = render({ data: { ...data, summary: '<script>unsafe()</script>', reviewText: 'PRIVATE_TEXT',
    authorEmail: 'private@example.test', reviewId: 987654, groupSection: '15-P', topics: ['SECRET_TOPIC'],
    positivePercentage: 99, overallSentiment: 'MOSTLY_POSITIVE', recommendation: 'Teacher should reveal secrets' } });
  assert.doesNotMatch(html, /PRIVATE_TEXT|private@example|987654|15-P|99%|MOSTLY_POSITIVE|SECRET_TOPIC|Teacher should|<script>/);
  assert.match(html, /&lt;script&gt;/);
});

test('existing sentiment/topics sections stay independently mounted exactly once', async () => {
  const page = await readFile(new URL('../src/pages/TeacherAnalyticsPage.tsx', import.meta.url), 'utf8');
  for (const component of ['SentimentAnalysis', 'TopicsAnalysis', 'SummaryAnalysis'])
    assert.equal((page.match(new RegExp(`<${component} key=`, 'g')) ?? []).length, 1);
  const section = await readFile(new URL('../src/components/SummaryAnalysis.tsx', import.meta.url), 'utf8');
  assert.doesNotMatch(section, /getTeacherSentiment|getTeacherTopics|retryReviewModeration|recommendation|dangerouslySetInnerHTML/);
});

test('actual summary effect ignores late responses after course/term change; Retry requests summary only', async () => {
  // Execute the production effect with controlled hook state and deferred HTTP responses.
  // No browser, dependency installation or external provider is needed.
  const effectServer = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)),
    server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent', plugins: [{
      name: 'summary-effect-test', enforce: 'pre',
      transform(code, id) {
        if (!id.replaceAll('\\', '/').endsWith('/components/SummaryAnalysis.tsx')) return;
        return code.replace("from 'react';", "from 'virtual:summary-hooks';")
          .replace("from '../auth/AuthContext';", "from 'virtual:summary-auth';");
      },
      resolveId(id) { if (id.startsWith('virtual:summary-')) return '\0' + id; },
      load(id) {
        if (id === '\0virtual:summary-auth') return 'export const useAuth = () => ({ expireSession() {} });';
        if (id === '\0virtual:summary-hooks') return `
          export function useState(initial) {
            const state = globalThis.__us35Hooks; const index = state.cursor++;
            if (!(index in state.values)) state.values[index] = initial;
            return [state.values[index], value => {
              state.updates++; state.values[index] = typeof value === 'function' ? value(state.values[index]) : value;
            }];
          }
          export function useEffect(effect) { globalThis.__us35Hooks.effects.push(effect); }
        `;
      },
    }] });
  let cleanup;
  try {
    const { default: SummaryAnalysis } = await effectServer.ssrLoadModule('/src/components/SummaryAnalysis.tsx');
    const calls = [];
    globalThis.fetch = (url, options) => new Promise(resolve => calls.push({ url, options, resolve }));
    const start = (scope, state = { cursor: 0, values: [], effects: [], updates: 0 }) => {
      state.cursor = 0; state.effects = []; globalThis.__us35Hooks = state;
      const tree = SummaryAnalysis(scope);
      const stop = state.effects[0]();
      return { state, tree, stop };
    };
    const finish = async (call, result) => {
      call.resolve({ ok: true, status: 200, json: async () => result });
      await new Promise(resolve => setImmediate(resolve));
    };
    for (const scope of [{ courseId: 20, term: 'term A' }, { courseId: 10, term: 'term B' }]) {
      const old = start({ courseId: 10, term: 'term A' });
      const oldCall = calls.at(-1); old.stop(); const oldUpdates = old.state.updates;
      const current = start(scope); cleanup = current.stop;
      assert.equal(current.state.values[0], null); assert.equal(current.state.values[1], true);
      const currentCall = calls.at(-1);
      const fresh = { ...data, ...scope, academicTerm: scope.term, summary: 'Fresh scope summary.' };
      await finish(currentCall, fresh);
      await finish(oldCall, { ...data, summary: 'STALE SUMMARY' });
      assert.ok(oldCall.options.signal.aborted);
      assert.equal(old.state.updates, oldUpdates);
      assert.equal(current.state.values[0].summary, 'Fresh scope summary.');
      assert.equal(current.state.values[1], false);
      current.tree.props.onRetry();
      assert.equal(current.state.values[0], null); assert.equal(current.state.values[1], true);
      assert.equal(current.state.values[2], 1);
      current.stop();
      const retry = start(scope, current.state); cleanup = retry.stop;
      assert.equal(calls.at(-1).url, currentCall.url);
      await finish(calls.at(-1), fresh); retry.stop();
    }
    assert.equal(calls.length, 6);
    assert.ok(calls.every(call => call.url.startsWith('/api/teacher/analytics/summary?')));
  } finally {
    cleanup?.(); delete globalThis.__us35Hooks; await effectServer.close();
  }
});
test('course/term changes and refresh request exact new scope without teacher identity', async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return { ok: true, status: 200, json: async () => data }; };
  const controller = new AbortController();
  for (const [id, term] of [[10,'term A'],[20,'term B'],[20,' term C '],[20,' term C ']])
    assert.deepEqual(await getTeacherSummary(id, term, controller.signal), data);
  assert.equal(calls.length, 4);
  assert.equal(calls[0].url, '/api/teacher/analytics/summary?courseId=10&term=term+A');
  assert.equal(calls[1].url, '/api/teacher/analytics/summary?courseId=20&term=term+B');
  assert.equal(new URL(calls[2].url, 'http://localhost').searchParams.get('term'), ' term C ');
  assert.equal(calls[2].url, calls[3].url);
  for (const call of calls) { assert.equal(call.options.credentials, 'same-origin'); assert.equal(call.options.body, undefined); assert.doesNotMatch(call.url, /teacherId/); }
  controller.abort(); assert.ok(calls.every(call => call.options.signal.aborted));
});
test('401 expires session', async () => {
  globalThis.fetch = async () => ({ ok: false, status: 401 });
  await assert.rejects(getTeacherSummary(10, 'term', new AbortController().signal), /session has expired/);
});
for (const status of [400,403,404,429,503]) test(`HTTP ${status} never exposes provider or backend body`, async () => {
  globalThis.fetch = async () => ({ ok: false, status, json: async () => ({ message: 'SECRET_KEY PRIVATE_TEXT' }) });
  await assert.rejects(getTeacherSummary(10, 'term', new AbortController().signal), { message: 'AI summary is temporarily unavailable.' });
});
test('network error rejects instead of inventing a summary', async () => {
  globalThis.fetch = async () => { throw new Error('Network error'); };
  await assert.rejects(getTeacherSummary(10, 'term', new AbortController().signal));
});
test('valid US31 selection mounts keyed summary; effects reload, cancel stale requests and retry', async () => {
  const page = await readFile(new URL('../src/pages/TeacherAnalyticsPage.tsx', import.meta.url), 'utf8');
  const section = await readFile(new URL('../src/components/SummaryAnalysis.tsx', import.meta.url), 'utf8');
  assert.match(page, /if \(loading \|\| error \|\| data\?\.selectedCourseId == null \|\| data.selectedAcademicTerm == null\) return null/);
  assert.match(page, /<SummaryAnalysis key=\{`\$\{data.selectedCourseId\}:\$\{data.selectedAcademicTerm\}`\}/);
  assert.match(page, /courseId=\{data.selectedCourseId\} term=\{data.selectedAcademicTerm\}/);
  assert.match(section, /getTeacherSummary\(courseId, term, controller.signal\)/);
  assert.match(section, /\[courseId, term, attempt, expireSession, onPrivacyProtected\]/);
  assert.match(section, /return \(\) => controller.abort\(\)/);
  assert.match(section, /if \(controller.signal.aborted\) return;/);
  assert.match(section, /setLoading\(true\); setData\(null\); setAttempt/);
  assert.match(section, /AccountSessionExpired\) expireSession\(\)/);
});




