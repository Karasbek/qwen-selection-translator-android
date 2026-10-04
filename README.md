# Qwen Selection Translator — Android MVP

Select text in an Android app → **Перевести** → a compact translucent popup that starts translation automatically. Android 8.0+ (minSdk 26).

The existing single-module Kotlin/Compose project is preserved. Both `ACTION_PROCESS_TEXT` and Share (`ACTION_SEND text/plain`) use the same Invocation → ViewModel → TranslatorEngine → provider flow. No Accessibility Service, overlay permission, clipboard monitoring, or runtime permissions are required.

## Current functionality

- Translate through configured Qwen/Groq, then one best-effort Google web fallback attempt.
- Explain in the selected text's language; English text is explained in simple English. Requires an AI API key.
- Loading, readable error/Retry, source/target language and actual AI model/host or fallback provider information.
- Copy, Close/Back, and Replace only for editable PROCESS_TEXT translation results. Replace preserves surrounding selection whitespace and returns `RESULT_OK` / `EXTRA_PROCESS_TEXT`.
- Scrollable popup and source text; input above 12,000 UTF-16 characters is rejected with a readable error, not sent/truncated for translation.
- Target language, model and HTTPS base URL in Preferences DataStore; API key encrypted separately with Android Keystore AES-256-GCM, fresh IV for every save.
- Credentials excluded from cloud backup and device transfer. Settings screen blocks screenshots/recents capture and masks the API key.

## Configuration

Defaults are centralized in `AppSettings`:

- target language: `ru`;
- base URL: `https://api.groq.com/openai/v1`;
- model: `qwen/qwen3.8-27b`.

Groq/Qwen requests disable reasoning with `reasoning_effort=none`, `reasoning_format=hidden`. These extensions are omitted for other models/hosts. Model and Chat Completions contract were checked against [Groq model documentation](https://console.groq.com/docs/model/qwen/qwen3.8-27b) and [API reference](https://console.groq.com/docs/api-reference) on 2026-10-04. No authenticated live Groq call was performed.

Open **Qwen Перевод**, enter your API key and save. Empty key removes the stored credential. Base URL must use HTTPS without embedded credentials, query or fragment. Configure only a service you trust: selected text and the key are sent to that host. Translate falls back to Google when AI fails; Explain does not.

In a supporting app, select text → **Перевести** (possibly in selection-menu overflow). Otherwise use Share → **Перевести**. After changing settings from the popup, return with Back and tap Retry, or switch the operation, to use the new settings.

## Build and install

Requirements: JDK 17+ (verified with OpenJDK 21), Android SDK Platform 37.0, Build Tools 36.0.0, internet for first dependency download. Set `ANDROID_HOME` or ignored `local.properties` (`sdk.dir=...`).

The official Gradle 9.4.1 wrapper, including its JAR and distribution SHA-256 verification, is included. AGP 9.2.0, Kotlin/Compose plugin 2.2.10 and the existing dependency versions are retained. `android.newDsl=false` accompanies the existing `android.builtInKotlin=false`, as required by the external Kotlin plugin; AGP reports deprecation warnings for this compatible configuration.

```bash
./gradlew --version
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On this Arch machine, checks used `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` before each Gradle command. The generated APK is:

```text
/data/qwen-selection-translator-android/app/build/outputs/apk/debug/app-debug.apk
```

See [VERIFICATION.md](VERIFICATION.md) for actual results and the phone checklist. Unit tests use fakes/interceptors and a local MockWebServer, with no mandatory external API calls.

## Remaining limitations

- No connected device during this session: installation, selection toolbar visibility, visual layout, clipboard, result delivery and Keystore persistence need phone verification.
- Google web fallback is unofficial. A live probe returned HTTP 429 in this environment; successful live fallback translation remains unverified. Failures are bounded and shown to the user.
- AI source language is displayed as `авто`; Google may report a detected code.
- Long text can exceed fallback URL or provider quotas even below the app's input limit. Truncated AI responses are rejected rather than offered as complete translations.
- No history, offline dictionary, streaming or Rust implementation. `TranslatorEngine` remains Android-free for future Rust/UniFFI integration.
- Custom selection menus may omit third-party PROCESS_TEXT actions.

See [ARCHITECTURE.md](ARCHITECTURE.md) and [CODEX_HANDOFF.md](CODEX_HANDOFF.md).
