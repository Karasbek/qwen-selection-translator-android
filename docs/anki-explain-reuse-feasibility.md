> Current Anki preparation: [full Explain + offline IPA](anki-local-preview.md). Anki adds no Groq request; earlier generation counts/semantic-extraction findings below describe the previous implementation.

# Explain → Anki: quality gate and decision

## Status

Research completed against `94b5458` on `feat/anki-integration`, initially clean.
**Automatic reuse is not implemented.** This follows the task's explicit stop condition when safe
extraction requires fragile parsing or offers insufficient practical benefit. Production code,
Explain/Translate/CardGenerator prompts, IPA, saving, UI, settings and cache are unchanged.
Only synthetic fixtures, research tests and this report are added. No user outputs/notes were read.

## Existing contracts

- `core/PromptBuilder.kt:explain()` requests unstructured prose, no headings/Markdown, 1–6 sentences,
  and at most one example in the *explanation* language. It does not require a marker, exact selected
  form in both sentences, a separate definition, or the original word's language when Russian is chosen.
- `network/OpenAiCompatibleProvider.kt:parse()` returns output/provider/operation, but no source-language
  detection for successful Groq translation and no completion provenance. `sourceLanguage` defaults to
  `auto` (`data/AppSettings.kt`). Latin spelling and an English explanation do not establish the source
  language of a selected word (e.g. English/German `gift`). Google fallback sometimes supplies detection;
  that does not help a normal successful two-Groq-call journey.
- `ProcessTextActivity.kt:onAnki` passes the current successful Explain as plain text, and source language
  from Translate detection or settings. `ProcessTextViewModel` protects results from stale requests;
  the translation cache retains complete successful responses but does not make prose structured.
- `anki/ClozeCardBuilder.kt` checks exact whole-form occurrences, escaping, field order and structural
  validation. It cannot decide whether a definition is true, a sentence grammatical or a language English.
- `network/GroqCardGenerator.kt:buildBody()` already receives Explain/Translate as context. Its distinct
  instructions require a definition/example containing the exact form, appropriate languages and JSON.
  `ui/anki/AnkiDraftViewModel.kt:generate()` already performs preflight, one generator call and optional
  IPA lookup; `runRegeneration()` explicitly gets a fresh generated variant. Diagnostics count actual
  calls/tokens; no local-conversion counters are added because there is no local-conversion operation.

## Predetermined corpus

The 24 synthetic cases and LOCAL/FALLBACK expectations were written before any converter implementation
(and no converter has subsequently been written). LOCAL is a human-labelled desirable outcome for a
hypothetical safe converter, **not a measured success of an implemented local path**. The synthetic
8/24 ratio is not an estimate of actual Explain traffic. Language/completeness annotations are oracle
information; current production results do not provide all of it reliably.

Full texts and manual candidate boundaries: `fixtures/explain-card-quality-gate.json`.

| ID | Selection | Expected | Reason |
| --- | --- | --- | --- |
| 01-simple-no-example | `agree` | FALLBACK | Нет отдельного примера. |
| 02-explicit-example | `agree` | LOCAL | Одна английская дефиниция и явно обозначенный независимый пример. |
| 03-verb-with-object | `abandon` | LOCAL | Естественная форма и одно точное вхождение в каждом компоненте. |
| 04-several-meanings | `bank` | FALLBACK | Определение смешивает значения, пример не выбирает единственное определение. |
| 05-several-examples | `agree` | FALLBACK | Несколько примеров. |
| 06-irregular-independent | `went` | LOCAL | Нерегулярная форма сохранена, предложение не переписывается. |
| 07-running-independent | `running` | LOCAL | В обоих компонентах одна исходная форма, корректное употребление. |
| 08-phrasal-verb | `give up` | LOCAL | Фраза не разрывается и не лемматизируется. |
| 09-apostrophe | `don't` | LOCAL | Апостроф сохранён, оба текста самостоятельны. |
| 10-long-discussion | `abandon` | FALLBACK | Несколько контекстных предложений; нельзя молча выбирать одно. |
| 11-foreign-example | `agree` | FALLBACK | Пример не на английском, ASCII не доказывает язык. |
| 12-missing-form | `running` | FALLBACK | В примере другая словоформа. |
| 13-ambiguous-occurrences | `agree` | FALLBACK | Два вхождения в определении. |
| 14-incomplete | `abandon` | FALLBACK | Неполный ответ. |
| 15-unmarked-last-sentence | `agree` | FALLBACK | Нельзя считать последнее предложение примером без признака его роли. |
| 16-anaphoric-definition | `agree` | FALLBACK | В определении нет изучаемой формы, исправление потребует переписывания. |
| 17-anaphoric-example | `agree` | FALLBACK | Пример зависит от контекста this, недостаточно самостоятельный. |
| 18-wrong-grammar | `running` | FALLBACK | Обе строки содержат точную форму, но грамматика неверна. |
| 19-visible-lemma | `agreed` | FALLBACK | Для строгой политики подсказок оставляет родственную изучаемую форму видимой. |
| 20-different-sense | `abandon` | FALLBACK | Структура корректна, значение неверно; синтаксический валидатор этого не обнаружит. |
| 21-noun | `cat` | LOCAL | Однозначное определение и независимый пример. |
| 22-adjective | `bright` | LOCAL | Одно значение, корректная форма в обоих компонентах. |
| 23-unknown-source | `gift` | FALLBACK | Без знания языка выделения gift может быть английским или немецким словом. |
| 24-non-ascii-first-version | `naïve` | FALLBACK | Заранее ограниченная английская версия не должна угадывать язык по латинице/диакритике. |

## Why the gate prevents shipping a simple extractor

For the marked examples, splitting on “For example” could mechanically isolate two strings. The same
operation works for cases 11 (foreign/mixed-language example), 18 (bad grammar) and 20 (a false
English definition). **All three bad candidates pass the existing ClozeCardBuilder.** The added JVM
research test demonstrates this directly, while the eight manually approved candidates also build
valid fields with exactly two `c1` insertions and no other fields filled.

Choosing the last sentence instead would incorrectly classify case 15; restoring a missing word,
changing run → running, resolving meanings or removing lemma hints introduces linguistic decisions.
Restricting reuse to one punctuation convention loses equivalent valid formats. Adding patterns for
more formats still does not solve semantic quality. A spelling whitelist/dictionary/grammar detector
would expand the task and remain difficult to validate; an LLM classification call is explicitly banned.

Rejecting unknown source language is mandatory for a conservative English-only path. In the default
`auto` configuration, successful Groq Translate leaves it unknown. This means no reliably justified
local reuse for that default journey, even when the English paragraph happens to look usable.
A restricted known-English implementation could potentially be useful, but actual coverage has not
been measured; inventing a format-frequency claim from these hand-written examples would be misleading.

Therefore this change does not ship an automatic classifier or claim three → two calls. It also does
not change the validator to accept/reject English grammar, invent diagnostics or make a partial feature
appear enabled. The existing generator remains the usable, explicit card-preparation path.

## Simpler architecture to consider separately

Keep the current generator and memory cache under today's constraints. A reliable two-call architecture
would need a structured result contract: one Explain response providing the display explanation and an
explicit, validated card candidate (source language, exact form, definition, example), plus the existing
Translate request. The UI could continue displaying only the explanation; the local builder would
consume candidate fields and fall back to the current generator when unavailable/invalid. Explicit
Regenerate would always use the existing generator. No separate classification request would be needed.

This would require changing the Explain response/prompt contract and evaluating explanation quality;
those changes are forbidden in this task and are **not implemented or automatically authorized**.
It cannot promise identical explanation quality without comparative testing. Another explicit choice
is user-selected/manual definition/example reuse, which avoids pretending automatic semantic validation
but changes the UX. Neither alternative is implemented here.

## Calls and verification

Current cold journey remains 3 logical Groq calls (Explain + Translate + Draft). Warm identical
Explain/Translate cache hits leave only 1 Draft call. Research prevents 0 additional calls; a future
validated structured path could yield 2 cold calls, with 3 on fallback. Reopen/recreation and dirty-edit
confirmation remain handled by the existing lifecycle code.

Targeted `ExplainReuseFeasibilityTest`: 3 JVM research tests passed. The complete JDK 21 regression
`--max-workers=2 --no-parallel testDebugUnitTest assembleDebug lintDebug compileDebugAndroidTestKotlin
assembleDebugAndroidTest` passed: 185 JVM tests (182 existing + 3 research), build successful,
lint 0 errors / 14 warnings. Production/Android APK code is unchanged.

Honor KRJ-W09 / Android 10: 40 Android tests passed, including the existing cache, Copy/Replace,
PROCESS_TEXT, dirty edits, cancellation, recreation and fake-save checks. An additional physical
ReadEra → «ПЕРЕВОД» → Qwen → Preview smoke passed on the synthetic `abandon` fixture with counted
fake providers: 2 translation calls, 1 Draft call, 0 writes. This validates the existing flow, not
an implemented local conversion. No paid Groq request, real Anki write, sync or collection modification
was performed. Anki metadata probes were read-only.

Commit scope: this report, the 24-case public synthetic corpus and three research tests. No production
feature, local-conversion counters or changed settings are shipped.
