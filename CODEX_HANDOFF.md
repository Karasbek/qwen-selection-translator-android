# Codex handoff

Goal: debug and harden this MVP; do not redesign it unless a concrete defect requires it.

## Definition of MVP

1. App installs and opens settings.
2. User may enter Groq API key; secret is encrypted with Android Keystore.
3. In an app that exposes standard text selection, selecting text shows `Перевести` via `ACTION_PROCESS_TEXT`.
4. Tapping it opens the translucent bottom popup over the previous app and automatically translates.
5. Translate can use Qwen/Groq, then the isolated Google web fallback.
6. Explain uses Qwen/Groq.
7. Copy works.
8. Replace is shown only for writable PROCESS_TEXT invocations and returns the processed text.
9. Share -> Qwen Перевод works as a fallback entry point.

## First debugging order

- Run `./gradlew testDebugUnitTest assembleDebug`.
- Fix build/API incompatibilities without changing the architecture seam (`TranslatorEngine`, `TranslatorProvider`, `Invocation`).
- Install on a real Android device and test PROCESS_TEXT in Chrome/Telegram/a plain EditText app.
- Verify `EXTRA_PROCESS_TEXT_READONLY` Replace behavior on an editable field.
- Verify activity window sizing/theme on phone and tablet.
- Verify Groq request against current `qwen/qwen3.8-27b` and current API response schema.
- Verify fallback parsing against a live response; if Google web endpoint changed, replace only `GoogleWebTranslateProvider`.
- Add instrumentation tests after the basic real-device path is green.

## Important constraints

- Do not add Accessibility Service or overlay permission for the normal selection path.
- Do not hard-code any API key.
- Do not log selected text or credentials.
- Keep Android-specific classes out of the core contracts.
- Treat Google web translation as replaceable/best-effort, not a stable API contract.
- Rust/UniFFI is the intended later shared-core direction, but do not block this Android MVP on native toolchains.
