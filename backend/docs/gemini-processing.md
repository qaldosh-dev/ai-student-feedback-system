# Gemini processing — OPT-01 / OPT-02

The four existing providers keep their own prompts, response schemas, parsers and
business services. They share one Spring `GeminiTransport` and one long-lived Java
HTTP client. Requests are independent; the client closes only at application shutdown.
No queues, scheduled work, cache, automatic retries, schema or public DTO changes.

## Small and large datasets

The existing single-request prompt/schema is used whenever the complete input fits
the request and expected output budgets. Nothing is sampled, cut off or rewritten.
Reviews retain the existing 2000-Java-character product limit.

For larger inputs, whole reviews are partitioned deterministically in the existing
database ID order, measuring the **final serialized UTF-8 request**, including policy,
schema and both levels of JSON escaping. Every review participates exactly once in
the map stage. Calls are sequential within one analysis; independent features can
still run concurrently. Failure anywhere discards the partial result.

- **Sentiment:** classify each chunk, validate one label per input, concatenate in
  input order. The existing service calculates counts, percentages and ties unchanged.
- **Topics:** map each review to compact labels, retaining singleton themes too.
  Their source-item positions are held only in memory. Exact labels are consolidated;
  Gemini groups semantic equivalents using candidate numbers, kinds and support counts.
  Candidate numbers are not review/user IDs. The backend unions supporting-item sets,
  rather than summing overlapping counts, and requires two distinct items per final
  theme. Intermediate reductions must cover every candidate; only the final step
  selects up to five topics and five concerns. This detects recurrence across chunks.
- **Summary:** summarize each chunk with the original neutral format rules, then
  combine all partial summaries with their represented review counts. If needed,
  reduction continues in bounded levels, carrying summed counts. Mixed/minority views
  and untrusted-data rules are explicit in the reduction prompt. Partials stay private.

Topic merging and summaries remain model interpretations: membership/count checks do
not prove semantic accuracy or eliminate compression loss. A live quality evaluation
with synthetic multilingual feedback is still useful before deployment. No real
student feedback is needed for that evaluation.

## Technical constants

| Guard | Value | Reason |
|---|---:|---|
| Final request | 65,536 UTF-8 bytes | Bounds each call; fits even worst-escaped valid moderation input |
| Received response | 100,000 bytes | Enforced while receiving, before full accumulation/parsing |
| Reviews per analysis | 10,000 | Extreme per-scope memory/work guard, not a normal sampling limit |
| Calls per analysis | 128 | Includes map and reduction calls; prevents runaway work/cost |
| Analysis scheduling window | 2 minutes | No new call after this; an already-started call has its normal timeout |
| Topic map items | At most 16 | Ten possible 80-character labels per review plus escaped envelope fit the response budget |
| Sentiment labels per call | `(100000 - 4096) / 16` | Reserve response envelope space and escaped per-label output |

Request byte size can make a chunk smaller than its output-derived item ceiling.
The database text query fetches at most 10,001 items: the extra item signals the
extreme review ceiling, and the provider rejects the **entire** analysis before
sending anything. This prefix is never returned as successful complete analytics.
If semantic consolidation cannot shrink an oversized candidate set, processing also
stops safely instead of looping or dropping candidates. These guards can produce
`UNAVAILABLE`; ordinary oversized requests use chunking instead.

The existing `GEMINI_API_KEY`, `GEMINI_MODEL` and request timeout configuration remain.
Connect timeout is 3 seconds; the configured request/read deadline defaults to 8
seconds and retains the existing 100–10,000 ms bounds. A response subscriber counts
actual bytes for success and error responses regardless of Content-Length/chunking,
cancels on overflow, and a deadline cancels an unfinished asynchronous HTTP exchange.
This HTTP implementation detail does not introduce background business processing.

## Privacy, failures and testing

Services still require at least **3 PUBLISHED reviews in the exact owned Teacher +
Course + academicTerm scope** before any provider/chunking call. For 0/1/2, existing
`PRIVACY_PROTECTED` responses and hidden small counts are unchanged. Only text and
anonymous derived data go to Gemini, never added identity/group/moderation metadata.

Moderation remains one complete review/one request. Provider failure retains
`SUBMITTED` and the existing retry/cooldown flow. Analytics failures retain the existing
`UNAVAILABLE` DTOs. No partial lists or intermediate summaries reach the frontend.
Safe provider logs use machine categories/reasons, status and sanitized model metadata;
they never include keys, headers, raw bodies, exception messages or stack traces.

Tests use mocked Gemini results plus an ephemeral loopback HTTP server for real body
limits/cancellation/deadline checks. No live Gemini, SMTP or production database is
needed. The test server/client/executor close after each test. Semantic quality and
live-model acceptance of the new private schemas were not established by mocks.
