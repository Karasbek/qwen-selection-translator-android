# Qwen Selection Translator

Select foreign text → **Qwen перевод** → a compact popup with **Explanation first, Translation second**. Both enabled results start automatically and independently. Android 8.0+ (minSdk 26).

One Kotlin/Compose module. PROCESS_TEXT, SEND, TRANSLATE and `translate.google.com` translation links all retain the same Invocation → popup ViewModel → TranslatorEngine → provider flow. No Accessibility, overlay permission or clipboard monitoring.

## v0.2.0

AnkiDroid integration uses your existing Cloze note type and deck. The Anki
preview reuses the complete Explain without another Groq request; tap words or
long-press/drag a phrase to select c1 clozes. English US IPA comes from the
bundled offline dictionary. Saving requires explicit confirmation and preserves
duplicate/outcome protection; synchronization is performed in AnkiDroid.
Successful Explain/Translate results have a bounded process-memory cache;
Settings includes local Groq usage diagnostics.

This release is debug-signed with the same certificate as v0.1.0 and supports
installation as an update. See [release notes](docs/releases/v0.2.0.md) for
validation and limitations, and [interactive Cloze](docs/anki-interactive-cloze.md)
for editor details.

## Reading popup

- Selected text and a compact source → target language line at the top; Settings and provider information under ⋮.
- Separate explanation/translation cards, each with loading, success or safe error and local Retry. One failed request does not hide the other result.
- Copy belongs to its card. Replace uses only a successful Translation for editable PROCESS_TEXT, preserving selection-edge whitespace and Android result delivery. Share, TRANSLATE and translation links never allow replacement.
- Source text and results scroll; Close stays accessible. Popup is bounded by the available viewport and 620dp width / 640dp height caps. Existing keyboard-hiding window configuration is retained.

## Settings

Save changes, then return to the popup: changed settings refresh its enabled results. Ordinary resumes and recompositions do not repeat requests.

| Setting | Default |
| --- | --- |
| Result order | Explanation → Translation |
| Enabled results | Both; the last enabled block cannot be switched off |
| Explanation language | English |
| Detail / examples | Simple / at most one short example |
| Translation languages | Auto → Russian (`ru`) |
| Model | `qwen/qwen3.8-27b` |
| Base URL | `https://api.groq.com/openai/v1` |

Explanation language (English / same as source / Russian), detail (Simple / Normal / Detailed) and examples affect Android-free engine request options and the actual prompt. Translation remains a separate operation. Move ↑/↓ controls change the saved order without additional libraries.

Enter your own API key in Settings. It remains encrypted with Android Keystore AES-GCM separately from Preferences DataStore; credentials stay excluded from cloud/device backup. Settings mask the key and block screenshots/recents capture. An empty key field removes the credential. Configure only a trusted HTTPS service: selected text and key are sent to that host.

Translate tries configured AI then one best-effort Google web fallback. Explanation requires the AI key. Errors distinguish authentication, rate limit, timeout, network, access restriction, unavailable service and malformed responses, without rendering raw server bodies/exceptions.

## Android integration

In supporting apps: select → **Qwen перевод**. On HONOR, open ⋮ and scroll below other handlers if needed. The previous verified native selection path is preserved.

The old ReadEra 19.12.27 direct **ПЕРЕВОД → Qwen popup** path uses the legacy translation-link handler: `http://translate.google.com/#auto/en/<text>`. Its Manifest filter and parser are unchanged in this iteration. This is regression-critical; do not remove it or substitute Share/Tile as its acceptance criterion. The existing ReadEra/Android chooser setup must be retained.

Share and the optional Quick Settings tile remain secondary entry points. The tile reads only explicitly copied plain text once from a foreground adapter.

## Build

JDK 17+ (verified OpenJDK 21), existing Android SDK Platform 37 / Build Tools 36.0.0. Keep the current wrapper/dependency versions; set `ANDROID_HOME` or ignored `local.properties`.

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew --max-workers=2 --no-parallel testDebugUnitTest assembleDebug lintDebug compileDebugAndroidTestKotlin
```

APK: `/data/qwen-selection-translator-android/app/build/outputs/apk/debug/app-debug.apk`.

See [VERIFICATION.md](VERIFICATION.md) for exact JVM/Compose test results and device checks. No external API calls or credentials are required by tests. Debug-only Compose previews cover short/long results, partial success, errors, one disabled block and dark landscape.

Current redesign verified on USB HONOR KRJ-W09 / HWKRJ, Android 10 / API 29: live automatic results, real EnglishWords selection/Replace/Copy, ReadEra direct **ПЕРЕВОД**, settings restart/order/enabled blocks, offline Retry and landscape. All 16 physical-device instrumentation tests passed. Earlier Honor findings and screenshots are kept separately as historical evidence. See [VERIFICATION.md](VERIFICATION.md).

## Limits

- Google web fallback is unofficial and may reject requests; live fallback reliability remains unverified.
- Input above 12,000 UTF-16 characters is rejected before requests. Provider/token limits still apply.
- AI source-language detection is not returned by the existing API contract; the header uses configured Auto unless a provider reports a detected language.
- Custom host menus may omit PROCESS_TEXT. Honor and old ReadEra were verified; Samsung and newer Android remain separate physical-device checks.
- History, dictionary, offline providers, streaming and Rust/UniFFI remain future work behind the existing engine seam.

[ARCHITECTURE.md](ARCHITECTURE.md) · [CODEX_HANDOFF.md](CODEX_HANDOFF.md) · [HONOR_VERIFICATION.md](HONOR_VERIFICATION.md).
