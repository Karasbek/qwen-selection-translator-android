# Limited Explain/Translate memory cache

## Scope and identity

`TranslationMemoryCache` is owned by the application-lifetime AI provider. It holds only complete,
successfully parsed Explain/Translate results in RAM. It never persists text/results, reads or stores
Authorization headers/API keys, logs content, or adds telemetry. The existing key/configuration checks
still run before a lookup; a removed key does not permit serving an old result.

`TranslationCacheKey` includes exact source text, operation, resolved endpoint and complete serialized
wire body. That body already contains the actual unchanged prompt, model, temperature, token budget,
stream flag and any Groq reasoning extensions. A change affecting the sent request causes a miss;
settings that do not affect this operation's body do not. Switching back can reuse the earlier entry
if it has not expired/been evicted. Credentials are deliberately excluded from cache identity.

The AI provider caches compatible endpoints too; the existing “Расход Groq” diagnostics count only
`api.groq.com`. Google fallback and Anki Draft remain uncached. CardGenerator, prompts, model,
ClozeCardBuilder and Anki saving API are unchanged.

## Bounds and lifecycle

- At most 100 completed entries, shared between Explain and Translate.
- Fixed 30-minute TTL measured from successful insertion by a monotonic clock. Hits update LRU order
  but do not extend TTL. Expired entries are pruned on the next lookup, without background work.
- Additional combined 2 Mi-character budget for key text/body/endpoint and result text (approximately
  4 MiB of UTF-16 character payload, plus object/index overhead). Oversized results are returned but
  not retained. LRU eviction enforces both limits.
- Everything resets when the application process exits; Activity recreation does not reset it.

Per-key coroutine mutexes serialize concurrent lookups. After one successful load, queued identical
requests hit the same entry without another HTTP call. Different keys proceed independently. Cancellation
releases the gate and does not store the cancelled result, including late noncancellable callbacks.
If the first caller fails/cancels, another explicit pending caller can load; there is no background retry.

Only nonempty parsed results with `finish_reason=stop` are retained. HTTP failures, malformed JSON,
truncated/other incomplete responses and cancelled work are not cached. Existing generation/request
IDs in ProcessTextViewModel still reject stale UI updates after selection/settings changes.

`TranslationRequest.bypassCache` propagates explicit Retry to the provider; it bypasses stored and
in-flight results, serializes with an in-flight same-key call, and performs a fresh load. A successful
complete fresh response replaces the entry. A failed/incomplete refresh does not replace a previously
complete result. Anki Regenerate already calls its uncached generator directly.

## Diagnostics

Settings → Диагностика → Расход Groq now includes cache hits and misses separately per Explain/Translate,
explicit bypasses, and prevented logical Groq calls (= hits). A queued caller reusing the first success
counts as a hit. Bypasses have a separate counter and are not reported as lookup misses.

Existing HTTP counters count actual OkHttp request-header send events, including protocol followups.
Only loader calls enter usage accounting; hits do not increment started/success/HTTP counts or replay
previous tokens. Groq's `cached_tokens` means server-side prompt caching, not this local result cache.
Prevented-call counts do not imply a measured token saving; only returned usage is known.

## Examples (valid complete results, unchanged inputs, within TTL)

| Scenario | Before | After |
| --- | ---: | ---: |
| Two identical selections, Explain + Translate | 4 Groq calls | 2 calls + 2 hits |
| Ten identical selections | 20 calls | 2 calls + 18 hits |
| New selection/settings/model/endpoint affecting both operations | 2 calls | 2 calls |
| Explicit Retry on one operation | 1 call | 1 call, cache bypass |
| Repeated selection followed by a fresh Anki Draft | 3 calls | 1 Draft call, 2 hits |

These counts exclude dictionary lookup and Google fallback and assume no transport followups.

## Verification

Targeted JVM tests use fake HTTP providers or localhost MockWebServer, deterministic usage and clocks:
exact text/operation, languages/options/model/endpoint, body parameters, TTL, LRU/default 100-entry limit,
memory budget, bypass, malformed/error/incomplete responses, concurrency, cancellation, stale UI state,
shared popup results, and token/HTTP/prevented-call counters.

Full JDK 21 command (single Gradle invocation, `--max-workers=2 --no-parallel`):
`testDebugUnitTest assembleDebug lintDebug compileDebugAndroidTestKotlin assembleDebugAndroidTest`.
182 JVM tests passed; build passed; lint has 0 errors and 14 warnings.
Honor KRJ-W09 / Android 10: all 40 Android tests passed. A new fake-HTTP Activity test closes and
reopens the popup with the same selection, verifies 2 total provider calls instead of 4, 1 hit per
operation, unchanged token totals, successful Replace eligibility, recreation and valid Anki Preview.
Existing Copy/Replace, PROCESS_TEXT, prompts/settings order, preview editing/cancellation, fake saving
and read-only Anki metadata checks remain green.

An additional physical ReadEra → «ПЕРЕВОД» → Qwen → Draft smoke test passed with a public synthetic
`abandon` fixture and fake providers (2 translation calls, 1 card call, 0 writes). The initial manual
smoke attempt timed out because ReadEra rendered a blank page; after restarting ReadEra and confirming
the fixture was visible, the repeat passed. This smoke checks the real entry flow; repeated-popup cache
behavior is verified by the separate fake-HTTP Activity test above.
No paid Groq request or real Anki write was made. User keys/settings and the collection were untouched.
