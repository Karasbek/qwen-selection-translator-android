# Qwen Selection Translator — Android MVP

Android translator optimized for the flow: **select a word/sentence in another app -> tap `Перевести` -> get a compact popup without leaving the task**.

## What is implemented

- `ACTION_PROCESS_TEXT` integration (API 23+ mechanism; app minSdk is 26).
- `ACTION_SEND text/plain` fallback through Android share sheet.
- translucent Compose popup over the current app;
- automatic translation on open;
- Translate / Explain tabs;
- Copy;
- Replace for writable PROCESS_TEXT selections;
- Qwen 3.8 27B through Groq/OpenAI-compatible chat completions;
- isolated Google web translation fallback when Qwen is unavailable;
- configurable target language, model and OpenAI-compatible base URL;
- API key encryption with Android Keystore + AES/GCM;
- Preferences DataStore for non-secret configuration.

## Default Qwen configuration

- base URL: `https://api.groq.com/openai/v1`
- model: `qwen/qwen3.8-27b`
- `reasoning_effort=none`
- `reasoning_format=hidden`

The API key is entered inside the app. It is never stored in source code.

## Build

Requirements: JDK 17+, Android SDK with API 37, Android Build Tools compatible with AGP 9.2, and internet access for Gradle dependencies.

```bash
cd qwen-selection-translator-android
./gradlew testDebugUnitTest assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected phone:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## First run

1. Open **Qwen Перевод**.
2. Enter a Groq API key if you want Qwen translation and Explain.
3. Leave the defaults (`qwen/qwen3.8-27b`, Groq base URL) unless needed.
4. Choose target language code, e.g. `ru`.
5. Save.
6. In another app select text -> `Перевести` (may be under the three-dot overflow in the selection toolbar).
7. If an app does not expose PROCESS_TEXT, use Share -> **Qwen Перевод**.

## Why there is a Kotlin engine in the MVP

The long-term architecture still targets a shared Rust/UniFFI core for Android + Linux. This MVP intentionally keeps the runnable Android build independent of a Rust/NDK pipeline. The UI depends only on `TranslatorEngine`; a future `UniffiTranslatorEngine` can replace `DefaultTranslatorEngine` without rewriting ProcessTextActivity or Compose UI.

See `ARCHITECTURE.md` and `CODEX_HANDOFF.md`.

## Known MVP limitations

- Google web fallback is unofficial and can break independently of the app.
- Explain requires a configured Qwen/OpenAI-compatible API key.
- No history/offline dictionaries yet.
- Not every Android app exposes third-party PROCESS_TEXT actions in its custom selection menu.
- No streaming; popup waits for the complete provider response.
