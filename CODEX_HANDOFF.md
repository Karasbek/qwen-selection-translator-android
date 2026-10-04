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

Debug signature and packaged intent-filter/backup resources checked. No Android device was connected, so no installation or runtime phone verification is claimed. No real API key supplied or live authenticated Groq request made. Groq official docs confirm the configured model and contract. Google web live probe returned HTTP 429; this endpoint is best-effort and its successful live response remains unverified.

## Next work: phone verification

Install with `adb install -r` (do not clear/uninstall existing data). Complete the phone checklist in `VERIFICATION.md`: selection toolbar, translucent popup, key save/restart, real Groq translation/Explain, Copy, editable Replace result, readonly/Share restrictions, settings return/Retry, rapid switching/new invocations, Back, rotation, dark theme, big font and long/Unicode text.

Use a user-provided key through settings only. Do not print preferences, credentials, raw request headers or selected text to diagnostics. After changing popup settings, return and tap Repeat or switch operation to reload configuration. Network/Keystore operations were validated by source/tests where possible; real Android Keystore is a phone-only check in this session.

## Boundaries to preserve

`Invocation` → `ProcessTextViewModel` → `TranslatorEngine` → `TranslatorProvider`. Core contracts are Android-free. `SettingsStore` and `ApiKeyStore` are small test seams over the existing DataStore/Keystore implementations. Compose/Activity must not contain provider HTTP/parsing.

Rust/UniFFI, history, streaming, offline dictionaries and extra providers remain future work. No Accessibility/overlay permissions. Do not touch Linux sway-translator or other repositories.

## Known limits

- No phone runtime evidence yet.
- Google unofficial endpoint/URL length and service quotas may reject requests; no unbounded fallback/retries.
- 12,000 UTF-16 input-character cap; AI output token cap and provider limits also apply.
- Groq source language is `авто`; Google may report its detected code.
- Intent menu visibility depends on the caller's Android selection implementation.
- Build emits AGP legacy Kotlin/DSL deprecations. Future AGP 10 migration will need built-in Kotlin; not required for this working AGP 9.2 MVP.
- Debug lint succeeds with nonblocking warnings recorded in `VERIFICATION.md`. Release build/signing was outside the requested milestone.
