# Verification — 2026-10-04

## Environment

- Working directory: `/data/qwen-selection-translator-android`.
- System Java/Android Studio JBR: OpenJDK 25.0.2. Builds verified using OpenJDK 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk`).
- Official Gradle wrapper 9.4.1, AGP 9.2.0, Kotlin/Compose plugin 2.2.10.
- Android SDK: `/home/karasik/Android/Sdk`, Platform 37.0 / Build Tools 36.0.0. Local SDK path is ignored by Git.
- `ANDROID_HOME` was set; `ANDROID_SDK_ROOT` unset. `adb` is installed.
- Network/cache access and adb were run outside restricted sandbox. No system package installation or changes to another project were needed.

## Passed

Commands run with the JDK above:

```bash
java -version
./gradlew --version
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug
```

- Unit tests: **39 passed, 0 failed, 0 skipped**. Coverage includes Translate/Explain prompts, configured request JSON, Unicode/multiline parsing, missing/empty/null/malformed/truncated AI output, Google segment parsing, engine fallback/cancellation/blank and huge input, HTTP status/timeouts/body limits/socket cancellation, stale ViewModel responses and new invocation, settings failures/validation/redaction, readonly/MIME/selection whitespace and replacement boundaries.
- Tests use fakes/interceptors/local MockWebServer; no Groq credentials or external network calls required.
- `assembleDebug`: **BUILD SUCCESSFUL**, debug APK created.
- `lintDebug`: **BUILD SUCCESSFUL**, **0 errors / 12 warnings**. Warnings: deliberate synchronous SharedPreferences commits on IO and KTX style suggestions, unchanged targetSdk 36, newer dependency/Gradle versions available, release resource shrinking suggestion. No lint baseline or broad disabling of checks.
- Official Gradle distribution checksum verified: `2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb`.
- Wrapper JAR checksum matches Gradle's published value: `55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c`.
- `aapt2 dump xmltree`: actual APK contains exported PROCESS_TEXT and SEND filters, DEFAULT category and text/plain MIME; backup references are present. No Accessibility/overlay service/permissions. INTERNET is present (AndroidX also contributes its internal signature permission).
- Backup resources exclude `translator_secrets.xml` for legacy backup, cloud backup and device transfer.
- `apksigner verify --verbose`: verifies, APK Signature Scheme v2, one signer.
- `git diff --check`: clean. Generated caches/APK/local.properties are ignored.
- Source audit: no HTTP in UI/Activity, main-thread secret IO, selected-text/key logging, hard-coded credentials or uncancelled blocking HTTP execution.

APK:

```text
/data/qwen-selection-translator-android/app/build/outputs/apk/debug/app-debug.apk
```

Reports:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/test-results/testDebugUnitTest/TEST-*.xml`
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/lint-results-debug.txt`

## External API verification limits

Groq official [model documentation](https://console.groq.com/docs/model/qwen/qwen3.8-27b), [API reference](https://console.groq.com/docs/api-reference) and [reasoning documentation](https://console.groq.com/docs/reasoning) were checked. They document qwen/qwen3.8-27b, POST /openai/v1/chat/completions, message content, max_completion_tokens, reasoning_effort=none and reasoning_format=hidden.

No authenticated live Groq request was made: no user key was supplied. Provider contract/flow passed mocked tests. An unauthenticated, nonsecret Google web translation probe returned HTTP 429; live translation through this unofficial fallback was not verified successfully. The app reports HTTP failures and stops after its finite provider chain.

## Phone verification still required

`adb devices -l` returned an empty device list. No installation, actual selection menu, screenshot/UI automation or instrumented tests were performed. Source/unit/APK checks do not establish those runtime results.

Install without clearing existing data:

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then check:

1. Launcher opens settings in light/dark theme; keyboard and Save remain accessible. Set language/model/base URL, enter a user key, save, restart and verify settings/key persistence. Empty key deletes it. Invalid URL shows an error without a crash.
2. In Chrome or another app with standard Android selection, select text and find **Перевести**, possibly in overflow. Confirm popup opens over the source app and translates automatically using Groq.
3. Verify source/result, language and actual model/host; Copy then paste into a test field.
4. English selection → Explain gives simple English; switching tabs quickly cannot show the previous tab's late result. Retry refreshes after changing settings.
5. From a plain editable field, select text → Translate → Replace. Only the selected text changes; surrounding whitespace remains. This must be started by the host for a result, not merely with adb am start.
6. Readonly selection hides Replace. Share → Перевести uses the same popup and also hides Replace.
7. Back, Close and outside tap return to the caller without replacing text. Open Settings from popup and Back to the retained popup.
8. Rotate while loading/success/error; no crash or duplicate request from recomposition. Open a different selection; no stale original text/result.
9. Multiline, emoji/non-Latin text, long text, large font and landscape: scrolling/wrapping work, Close is reachable. Above 12,000 UTF-16 characters gives a readable limit error.
10. Offline, invalid key/model, timeout and rate limit produce readable errors; Translate makes at most one AI plus one fallback attempt, Explain stays AI-only. Google success must be checked on the device/network separately.
11. Keystore/device-specific: save twice with the same test key, confirm IV/ciphertext change without printing the key; ciphertext is not plaintext. Backup/transfer should restore ordinary settings but require re-entering the API key. Do not wipe the user's data to test this.

Optional adb diagnostics with harmless sample text (these do not prove host menu or Replace result delivery):

```bash
adb shell cmd package query-activities --brief -a android.intent.action.PROCESS_TEXT -t text/plain
adb shell am start -a android.intent.action.PROCESS_TEXT -t text/plain -n com.karas.qwentranslator/.ProcessTextActivity --es android.intent.extra.PROCESS_TEXT 'Hello world' --ez android.intent.extra.PROCESS_TEXT_READONLY true
adb shell am start -a android.intent.action.SEND -t text/plain -n com.karas.qwentranslator/.ProcessTextActivity --es android.intent.extra.TEXT 'Hello world'
```
