import { test, after, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)), server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
const originalFetch = globalThis.fetch;
after(() => server.close());
afterEach(() => { globalThis.fetch=originalFetch; delete globalThis.sessionStorage; });
const { createReviewSubmission, completeDraft } = await server.ssrLoadModule('/src/pages/reviewSubmission.ts');
const { saveDraft, loadDraft, draftKey } = await server.ssrLoadModule('/src/pages/reviewDraft.ts');
const { ReviewSubmitButton, ReviewSubmissionSuccess } = await server.ssrLoadModule('/src/components/ReviewDraftEditor.tsx');
const { ReviewTargetSelection, reviewTargetQuery } = await server.ssrLoadModule('/src/pages/ReviewTargetPage.tsx');
const target={ courseOfferingId:1, academicTerm:'2026-2027 1 term', groupSections:['01-N','02-P'] };
const other={ ...target, courseOfferingId:2 };
const draft={ rating:4, text:'Useful explanations.' };
function setup() {
  const data=new Map();
  globalThis.sessionStorage={ getItem:key=>data.get(key)??null, setItem:(key,value)=>data.set(key,value), removeItem:key=>data.delete(key) };
  saveDraft(target,draft); saveDraft(other,{rating:2,text:'Other target'}); return data;
}
const button=(value=draft,pending=false)=>ReviewSubmitButton({draft:value,pending,onSubmit(){}});
function response(status=201, overrides={}) { return {ok:status===201,status,json:async()=>({reviewId:7,status:'SUBMITTED',submitted:true,courseOfferingId:1,academicTerm:target.academicTerm,rating:4,...overrides})}; }
function network(submit) {
  globalThis.fetch=async(path,options)=>path==='/api/auth/csrf'
    ? {ok:true,json:async()=>({headerName:'X-CSRF-TOKEN',token:'test-only'})} : submit(path,options);
}
test('complete draft enables Submit without identity visibility choice',()=>{
  assert.equal(button().props.disabled,false);
  const html=renderToStaticMarkup(button()); assert.match(html,/Submit Review/); assert.doesNotMatch(html,/checkbox|anonymous|author|email/i);
});
for(const [label,value] of [['no rating',{...draft,rating:null}],['invalid rating',{...draft,rating:6}],['decimal',{...draft,rating:4.5}],['empty',{...draft,text:''}],['whitespace',{...draft,text:' \n '}],['too long',{...draft,text:'x'.repeat(2001)}]])
  test(`${label} disables Submit and rejects without a request`,async()=>{
    assert.equal(completeDraft(value),false); assert.equal(button(value).props.disabled,true);
    globalThis.fetch=()=>{throw Error('must not call server');};
    await assert.rejects(createReviewSubmission(target).submit(value));
  });
test('submission sends only exact target/rating/text with CSRF and session credentials; clears current draft only',async()=>{
  const data=setup(); let sent;
  network(async(path,options)=>{sent={path,...options};return response();});
  const result=await createReviewSubmission(target).submit({...draft,userId:99});
  assert.equal(sent.path,'/api/student/reviews'); assert.equal(sent.method,'POST'); assert.equal(sent.credentials,'same-origin');
  assert.equal(sent.headers['X-CSRF-TOKEN'],'test-only');
  assert.deepEqual(JSON.parse(sent.body),{courseOfferingId:1,academicTerm:target.academicTerm,...draft});
  assert.equal(result.draftCleared,true); assert.equal(data.has(draftKey(target)),false);
  assert.equal(result.reviewId,7); assert.equal(result.status,'SUBMITTED');
  assert.deepEqual(loadDraft(other,null),{rating:2,text:'Other target'});
});
test('one in-flight action cannot double-submit and button is disabled while pending',async()=>{
  setup(); let resolveResponse,calls=0;
  const gate=new Promise(resolve=>{resolveResponse=resolve;});
  network(async()=>{calls++;return gate;});
  const action=createReviewSubmission(target); const first=action.submit(draft);
  assert.equal(action.pending,true); assert.equal(button(draft,action.pending).props.disabled,true);
  assert.equal(await action.submit(draft),null);
  resolveResponse(response()); await first; assert.equal(calls,1); assert.equal(action.pending,false);
});
for(const status of [400,401,403,500]) test(`HTTP ${status} keeps draft and exposes a safe error`,async()=>{
  setup(); network(async()=>response(status));
  await assert.rejects(createReviewSubmission(target).submit(draft),error=>!error.message.includes('SQL'));
  assert.deepEqual(loadDraft(target,null),draft); assert.equal(loadDraft(other,null).text,'Other target');
});
test('duplicate conflict shows friendly feedback, never succeeds, and keeps both drafts', async () => {
  setup(); let calls = 0;
  network(async () => { calls++; return response(409); });
  const action = createReviewSubmission(target);
  await assert.rejects(action.submit(draft), /You already submitted feedback for this course offering and term/);
  assert.deepEqual(loadDraft(target,null), draft);
  assert.deepEqual(loadDraft(other,null), {rating:2,text:'Other target'});
  assert.equal(action.pending, false); assert.equal(calls, 1);
});
test('network failure or unexpected success target preserves draft',async()=>{
  setup(); network(async()=>{throw new TypeError('network');});
  await assert.rejects(createReviewSubmission(target).submit(draft)); assert.deepEqual(loadDraft(target,null),draft);
  network(async()=>response(201,{courseOfferingId:999}));
  await assert.rejects(createReviewSubmission(target).submit(draft)); assert.deepEqual(loadDraft(target,null),draft);
});

for (const overrides of [{reviewId:0},{reviewId:null},{status:null},{status:'PUBLISHED'}])
  test(`unconfirmed submission identity/status preserves drafts: ${JSON.stringify(overrides)}`,async()=>{
    setup(); network(async()=>response(201,overrides));
    await assert.rejects(createReviewSubmission(target).submit(draft));
    assert.deepEqual(loadDraft(target,null),draft);
    assert.equal(loadDraft(other,null).text,'Other target');
  });
test('tampered target cannot expose Submit despite stored draft',()=>{
  setup(); const html=renderToStaticMarkup(createElement(ReviewTargetSelection,{rows:[other],search:reviewTargetQuery(target).toString(),onSelect(){}}));
  assert.match(html,/not available for review/); assert.doesNotMatch(html,/Submit Review|textarea/);
});
test('success shows confirmation only, without author identity or an editable/submittable draft',()=>{
  const html=renderToStaticMarkup(createElement(ReviewSubmissionSuccess,{storageFailed:false}));
  assert.match(html,/role="status"/); assert.match(html,/Your review was submitted successfully/);
  assert.doesNotMatch(html,/author|userId|email|checkbox|textarea|button/);
});
