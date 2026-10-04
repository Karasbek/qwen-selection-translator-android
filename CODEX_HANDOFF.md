# Codex handoff — 2026-10-04

The existing Android MVP has been built successfully. Continue from this repository; do not recreate the project or rewrite the engine/UI. Git was initialized with the original project preserved in commit `e88c4b9`.

## Implemented fixes

- Official Gradle 9.4.1 wrapper JAR/scripts and verified distribution SHA-256, Android gitignore and local SDK configuration (ignored).
- AGP 9.2 external Kotlin compatibility (`android.newDsl=false` alongside `android.builtInKotlin=false`). Existing runtime dependency versions retained.
- Fixed invalid window dim resource (`backgroundDimAmount`).
- Removed popup `noHistory`, retained standard activity launch/result semantics, added explicit `onNewIntent` handling.
- Shared intent validation handles MIME, null/blank/malformed input and readonly; selection whitespace is preserved, including in Replace results.
- Cancel active coroutine and HTTP socket on new operation/invocation/retry; prevent duplicated loading-tab requests and stale replies. Engine never falls back on cancellation.
- Safe network/config/JSON errors, bounded bodies/input, finite provider chain, no redirects or automatic connection retry, reject truncated AI response.
- Centralized defaults, validate HTTPS URL and language/model, condition Groq extensions on Groq/Qwen configuration. Actual model/host shown in popup.
- Explicit Close/Retry, language/provider info, constrained scrollable popup and wrapping buttons, dark settings background and safe insets.
- Settings load/save errors handled; loading/saving disables edits; Keystore and synchronous persistence run off main. State diagnostics redact credentials; FLAG_SECURE on settings.
- Reviewed AES-GCM fresh IV/key generation, authentication and missing-key restore behavior; unusable ciphertext cleared. Existing backup exclusions are correct and retained.
- Added network/core/settings/Invocation/ViewModel regression tests with fakes and local HTTP only.

## Verified result

See `VERIFICATION.md` for commands, report paths and exact counts. Both required Gradle tasks and lint succeed. Debug APK:

`/data/qwen-selection-translator-android/app/build/outputs/apk/debug/app-debug.apk`

Debug signature and packaged intent-filter/backup resources checked. Follow-up USB verification on HONOR KRJ-W09 (Android 10 / API 29, Magic 3.1.1 / EMUI property 10.1.1) passed native selection, live Groq Translate/Explain with the existing encrypted user key, Copy/paste, actual editable Replace delivery, readonly/Share restrictions, tile and rotation. Final suite: 40 tests, 0 failures; assemble/lint pass (0 lint errors, 12 warnings). Google live success remains unverified after an earlier HTTP 429 probe.

## HONOR follow-up

Read [HONOR_VERIFICATION.md](HONOR_VERIFICATION.md) before diagnosing menu absence again. There is no general firmware ban: stock widgets with View IDs expose a scrollable overflow; a separate temporary package launched the main Qwen handler across UIDs. The temporary package was removed; applicationId remains com.karas.qwentranslator.

Huawei Browser and ReadEra use their own limited menus. ReadEra Premium 19.12.27+1120's Translate explicitly starts ru.yandex.translate/.ui.activities.QuickTrActivity from ReadEra's UID; changing defaults/our Manifest cannot redirect it. Its Share works: second Huawei Share page → Qwen перевод. The system's native Petal Translate action is distinct from ReadEra's Yandex action. Do not claim a package blacklist based on missing logcat PackageManager queries.

Changes: distinct Qwen перевод action label, overflow/share instructions, standard ACTION_TRANSLATE adapter (EXTRA_TEXT, readonly), debug-only stock SelectionProbeActivity, optional ClipboardTileService → nonexported foreground ClipboardInvocationActivity → existing SEND/popup flow. Service never reads clipboard; focused adapter reads once, no listener. Input limit shared via TranslationRequest.MAX_TEXT_LENGTH. Old TileService launch is only used below API 34 with a narrow compatibility lint suppression. Main PROCESS_TEXT launchMode/result semantics, engine/provider abstraction and Keystore remain intact.

The Qwen tile was added and left on the tablet for use. Debug fixture: adb shell am start -n com.karas.qwentranslator/.SelectionProbeActivity. It adds no custom menu actions. A harmless test TXT and Notes fixture were used; no personal documents/messages were translated or sent. The local HTTP server and USB reverse used for browser testing were stopped/removed.

Remaining checks are in VERIFICATION.md: Samsung repeat run, Telegram/other custom menus, Notes selection, API 34+ and lockscreen tile behavior, complete key save/IV/backup/restore, broader offline/Unicode/long-layout tests. Do not dump password fields/preferences or raw HTTP credentials. Existing user key survived reinstall -r and worked; it was never printed or modified.

## Boundaries to preserve

`Invocation` → `ProcessTextViewModel` → `TranslatorEngine` → `TranslatorProvider`. Core contracts are Android-free. `SettingsStore` and `ApiKeyStore` are small test seams over the existing DataStore/Keystore implementations. Compose/Activity must not contain provider HTTP/parsing.

Rust/UniFFI, history, streaming, offline dictionaries and extra providers remain future work. No Accessibility/overlay permissions. Do not touch Linux sway-translator or other repositories.

## Known limits

- Host-specific Browser/old ReadEra menu limitations; stock HONOR selection, Share and tile have runtime evidence.
- Google unofficial endpoint/URL length and service quotas may reject requests; no unbounded fallback/retries.
- 12,000 UTF-16 input-character cap; AI output token cap and provider limits also apply.
- Groq source language is `авто`; Google may report its detected code.
- Intent menu visibility depends on the caller's Android selection implementation.
- Build emits AGP legacy Kotlin/DSL deprecations. Future AGP 10 migration will need built-in Kotlin; not required for this working AGP 9.2 MVP.
- Debug lint succeeds with nonblocking warnings recorded in `VERIFICATION.md`. Release build/signing was outside the requested milestone.
