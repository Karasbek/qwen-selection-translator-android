# Codex handoff — reading-popup redesign, 2026-10-04

Start from this single-module repository. The previous working point `8eb615e` / `649ed23` is preserved in history. No Rust rewrite, module expansion, new runtime permissions or provider dependencies.

## Current behavior

- Popup automatically starts the enabled Explanation and Translation as independent sibling jobs in one `ProcessTextViewModel`. Defaults: Explanation first, Translation second, both ON.
- Each block has Disabled/Loading/Success/Error; local Retry and Copy. Partial success remains visible. Translation alone may Replace an editable PROCESS_TEXT selection.
- `PopupUiState` contains invocation, language pair, ordered block list and a state map. `replacementText` rechecks current output, operation and invocation; Android result delivery is unchanged.
- Per-invocation generation and per-block request IDs reject stale responses, including non-cooperative cancelled operations. Clearing the ViewModel cancels HTTP sockets. Recomposition/normal resume never start requests.
- On return from Settings, changed operation/provider options refresh results. Order-only changes retain them; disabling cancels just that block; enabling runs only the newly enabled block. An unchanged configuration retries errors on return (also recovers a key-only save) without repeating successful results.
- UI: source text + language pair, overflow Settings/provider info, separate cards, scrollable body, fixed Close. Bounded viewport/620dp width/640dp height. Background gesture handling preserves distinct accessibility headings.

## Settings and core

`AppSettings` persists ordered enum names and enabled set, source/target languages and `ExplanationOptions` alongside existing model/base URL in the SAME DataStore. Old-install keys/defaults and unknown future enum values are handled without resetting user data. Saving zero enabled blocks is rejected; the final switch is disabled and ViewModel guards it.

Explanation defaults: English, Simple, maximum one short example. Language (English/same as source/Russian), detail and examples all affect `PromptBuilder` through Android-free `TranslationRequest.explanation`. Translation stays a separate operation; explicit source code reaches its prompt and Google provider.

HTTP errors and parser/IO failures carry `FailureKind` and optional safe provider label; the engine preserves the primary category across failed fallback. UI uses curated messages, never raw exception messages/bodies. Google fallback remains best-effort, finite and translation-only.

## Regression-critical boundaries

Manifest, `InvocationParser`, `TranslationLinkParser`, clipboard/tile adapters, `SecretStore` and backup XML are unchanged from `8eb615e`. Preserve PROCESS_TEXT, SEND, TRANSLATE, and VIEW for `http(s)://translate.google.com/`. Preserve the legacy `#auto/en/<text>` path used by old ReadEra's direct ПЕРЕВОД button. Do not treat Share/Tile as its acceptance criterion or replace package identities.

API key remains separately encrypted with Keystore AES-GCM, randomized IV. Settings still mask credentials and use FLAG_SECURE. Do not dump settings/key fields or HTTP credentials.

## Verification and continuation

See [VERIFICATION.md](VERIFICATION.md) for final counts/commands and physical-device checklist. JVM tests use fakes and local MockWebServer, including real Preferences DataStore save/restart. Compose instrumentation tests run on the pre-existing API 35 AVD with fake result data; no emulator image downloaded. Debug previews include small/long content, dark landscape, one disabled block, partial success and errors.

The physical Honor was reconnected after crash recovery. Current redesign runtime regression passed on KRJ-W09 / HWKRJ, Android 10 / API 29: ReadEra 19.12.27 direct ПЕРЕВОД → Qwen, EnglishWords native PROCESS_TEXT with actual Replace/edge whitespace/Copy, automatic independent results, settings force-stop/order/enabled blocks/options, offline local Retry, long dark landscape and rapid new Intent. Defaults and original device rotation/network/theme were restored. 16 instrumentation tests passed on Honor, including three counted activity lifecycle checks with isolated test preferences. See VERIFICATION.md for the distinction between live provider checks, fake device tests and historical baseline evidence. No production device bug was reproduced; post-crash work completed the audit, added activity tests and current screenshots/docs. Recovery backup remains at `/data/qwen-translator-recovery-20261004-171306`.

APK: `/data/qwen-selection-translator-android/app/build/outputs/apk/debug/app-debug.apk`.

Remaining product limits: unofficial Google reliability, API input/token limits, host custom menus, source Auto when AI returns no detection, no dictionary/history/offline/streaming yet. Core contracts remain Android-free for future Rust/UniFFI. Existing AGP legacy DSL/Kotlin deprecations and lint warnings remain; do not upgrade toolchains just to silence them.
