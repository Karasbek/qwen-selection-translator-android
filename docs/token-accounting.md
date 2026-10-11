# Groq usage and request lifecycle (Optimization 1)

## Scope

Explain, Translate and Anki Draft still use independent, unchanged prompts and the existing model.
A cold successful journey costs three logical Groq calls (two translation blocks, then one explicit
card preparation). Preview editing, reopening, saving and unchanged Settings returns add no Groq call.
IPA dictionary lookup and Google translation fallback are separate services, not Groq usage.
A later change adds the bounded process-memory AI result cache described in `translation-cache.md`.
Explain-to-card conversion, telemetry and collection changes remain outside scope.

## Local diagnostics

Settings → Диагностика → Расход Groq displays application-session counters separately for
Explain, Translate and Anki Draft. They survive Activity recreation but reset on process restart.
Only requests to `api.groq.com` are included; arbitrary OpenAI-compatible endpoints are excluded.

- **Начато**: logical calls reaching the enqueue boundary, after local key/configuration validation.
- **HTTP попытки**: OkHttp `requestHeadersStart` events (wire attempts, including protocol followups).
  A call failing before headers can have zero attempts; one logical call can have multiple attempts.
- **Успешно / Ошибки / Отменено / В работе**: mutually exclusive terminal outcomes or pending calls.
  Successful HTTP responses whose application content cannot be parsed are errors.
- **Input / Output / Всего / Cached**: sums of reported nonnegative integer
  `usage.prompt_tokens`, `completion_tokens`, `total_tokens`,
  `prompt_tokens_details.cached_tokens`. Each sum includes its number of reporting responses.
  Missing/invalid fields remain unknown; zero is displayed only when explicitly returned.
  Totals are not inferred from other fields. Partial sums are not complete billing totals.

Usage is captured before content parsing, so a malformed 2xx response can still contribute tokens.
Existing error-body handling remains unchanged: HTTP error bodies are discarded and their usage is
unknown. Cancellation/socket failure may still cost server tokens; no usage is invented or recovered
by another request. Diagnostics neither store nor log keys, selected text, outputs or note content.

Implementation: `core/ApiUsageTracker.kt`, `network/ApiUsage.kt`, both Groq providers,
`di/AppContainer.kt`, `MainActivity.kt` and `ui/settings/SettingsScreen.kt`.

## Request invalidation

`ProcessTextViewModel.refreshSettings()` compares each block's exact existing `PromptBuilder` output,
resolved endpoint and model. Explain-only preferences invalidate Explain; translation languages
invalidate Translate. Reordering, Anki IDs and unchanged returns retain existing results/errors and
in-flight calls. Newly enabled blocks run; disabled blocks cancel and reject stale callbacks.
Unchanged errors (including after a key-only save) require explicit Retry; Retry runs one selected block.
A settings refresh racing initial configuration still starts each enabled operation once.
ViewModels retain work across Activity recreation; Compose does not launch requests.
The bounded memory cache now reuses identical AI requests across popups; a new input remains a miss.
There is no cross-process persistence.

## Draft lifecycle

`AnkiDraftViewModel.generate()` checks Anki availability and compatible selected deck/model metadata
before calling the generator or IPA lookup. Invalid destinations retain the editable draft and show
existing validation messages; no fallback selection is made. Saving still performs its own fresh checks.

Successful CardContent is committed before optional IPA lookup. Closing/cancelling during IPA keeps
that content with empty IPA; reopening refreshes metadata without repeating generation or IPA lookup.
The user can edit IPA manually or explicitly regenerate. Late/cancelled callbacks cannot overwrite it.

Reopening captures current popup context for the next explicit regeneration. If the edited word differs
from the original context selection, old Explain/Translate and detected language are discarded;
explicit configured source language remains respected. Dirty edits still require confirmation before
replacement; errors/preflight failures retain them. Cloze building and Anki writes are unchanged.

## Optimization 1 verification (before the memory-cache change)

Targeted JVM tests passed before full regression. Full JDK 21 Gradle run with `--max-workers=2 --no-parallel`:
`testDebugUnitTest assembleDebug lintDebug compileDebugAndroidTestKotlin assembleDebugAndroidTest`.
163 JVM tests passed. Lint has zero errors and 14 warnings; debug/test APKs built.
Honor KRJ-W09 / Android 10: 39 Android tests passed, including numeric diagnostics, unchanged settings,
preview reopening, orientation/recreation, PROCESS_TEXT, Copy/Replace and fake-save regression.
An additional physical ReadEra smoke test passed: a public synthetic `abandon` fixture was selected,
ReadEra's «ПЕРЕВОД» menu invoked Qwen, counted fake providers received exactly two translation calls
and one card call, the draft validated, and zero fake writes occurred. No paid Groq request or real
Anki write was made. Real Anki metadata tests remain read-only.

For the physical smoke test, run only
`ProcessTextActivityTest#readEraTranslateActionUsesOnlyCountedFakes` with instrumentation argument
`-e readeraSmoke true`, then select the public fixture in ReadEra while the test keeps fakes installed
(up to 55 seconds). Never invoke translation outside this fake window for a no-cost verification.
The default test path supplies the same ACTION_TRANSLATE entry intent deterministically.

## Next optimization, outside this change

Use session diagnostics to measure normal and repeat journeys. The limited keyed memory cache is now
implemented in the follow-up change; persistent caching and structured Explain reuse are not.
Local Explain-to-card conversion would require quality/word-form validation.
Do not infer token savings from cancelled calls whose server usage is unknown.
