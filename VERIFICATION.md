# Verification — 2026-10-04

## Build environment and final result

- Project: `/data/qwen-selection-translator-android`; baseline working commit `16b3f97` preserved.
- Builds: OpenJDK 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk`), official Gradle wrapper 9.4.1, AGP 9.2.0, Kotlin/Compose plugin 2.2.10.
- SDK: `/home/karasik/Android/Sdk`, Platform 37.0 / Build Tools 36.0.0; local SDK path ignored by Git.
- Existing tool/dependency versions retained. No other repository or system package was changed.

Final commands, including a rerun after restoring the temporary diagnostic applicationId:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew testDebugUnitTest assembleDebug lintDebug
```

| Check | Result |
| --- | --- |
| testDebugUnitTest | 40 passed, 0 failures/errors/skipped |
| assembleDebug | BUILD SUCCESSFUL, APK created |
| lintDebug | BUILD SUCCESSFUL, 0 errors / 12 warnings |
| git diff --check | Clean |

The 39 baseline tests are retained, plus ACTION_TRANSLATE input/readonly/blank/MIME coverage. Existing tests cover prompts, Translate/Explain, JSON/malformed/empty/truncated responses, fallback/cancellation, bounded HTTP/timeouts, Invocation/Replace and stale ViewModel/settings errors. They use fakes and local MockWebServer; no external calls or keys are required.

Lint warnings remain the baseline nonblocking warnings (synchronous preferences commit on IO, KTX suggestions, target/dependency version suggestions and release shrinking). The old TileService Intent launch API is narrowly suppressed **only for compatibility below API 34**, where the replacement PendingIntent overload does not exist; API 34+ takes the new branch. No lint baseline or blanket suppression was added.

APK:

```text
/data/qwen-selection-translator-android/app/build/outputs/apk/debug/app-debug.apk
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/test-results/testDebugUnitTest/TEST-*.xml`, `app/build/reports/lint-results-debug.html` and `lint-results-debug.txt`.

## USB tablet runtime

Device: HUAWEI / HONOR **KRJ-W09**, HWKRJ, Android **10 / API 29**, build **KRJ-W09 3.1.1.267(C10E1R1P1)**, EMUI property **EmotionUI_10.1.1**, Magic property **3.1.1**.

[HONOR_VERIFICATION.md](HONOR_VERIFICATION.md) contains the metadata comparison, experiments, intent logs, host-specific limits and reproduction steps.

Passed on the real device:

- Repeated `adb install -r` without clearing main app data; PackageManager resolves launcher and enumerates PROCESS_TEXT, SEND and TRANSLATE to ProcessTextActivity.
- Native TextView/EditText selection **⋮ → scroll down → Qwen перевод**. A separate temporary package also selected and launched the main Qwen app, proving cross-package eligibility.
- Live Groq Translate and Explain with the already configured user key; actual qwen/qwen3.8-27b / api.groq.com shown. Hello translated to Здравствуйте; Explain returned a simple English explanation.
- Real host result delivery: editable Hello selection became Здравствуйте, surrounding sentence unchanged. Readonly selection omits Replace.
- Copy followed by Paste inserts the translation in a test EditText.
- ReadEra Share → second Huawei Share page → Qwen перевод opens the same popup, translates automatically, omits Replace.
- Quick Settings tile added and physically tapped through adb input; foreground focus allows its one-shot clipboard read, then the same popup translates copied text, omitting Replace.
- MainActivity launch, Back/Close returning to caller, portrait → landscape → portrait popup/result without a crash; rotation settings restored.
- Missing PROCESS_TEXT extra closes safely. Share input of 12,001 characters displays the size error. Untyped ACTION_TRANSLATE reads EXTRA_TEXT and stays readonly.
- Existing key/settings remain usable across reinstalls/app recreation. No API key was read from preferences, printed, changed or inserted into logs/screenshots.

No FATAL EXCEPTION from the tested app was observed. UI/runtime checks were performed with harmless fixtures; no messages were sent. The temporary diagnostic package was removed, and the normal package is installed. The optional Qwen tile remains available.

## Integration and security checks

Baseline verified wrapper distribution and JAR SHA-256 checks remain unchanged. Manifest/APK inspection confirms exported standard PROCESS_TEXT/SEND filters with DEFAULT + text/plain, plus TRANSLATE without MIME. Standard launch/result behavior and excludeFromRecents remain unchanged.

The tile service requires the system signature BIND_QUICK_SETTINGS_TILE permission; its clipboard activity is **not exported**. This is not an application runtime permission. There is no Accessibility Service, SYSTEM_ALERT_WINDOW, foreground/background clipboard listener or screen reader. TileService itself does not access clipboard data. Image/URI clipboard content is not coerced or read; blank/nontext/oversized input is guarded.

Keystore AES-GCM storage and backup exclusions remain intact. Settings use FLAG_SECURE and password masking. Full save-twice IV comparison, backup/restore, lockscreen and newer Android behavior were not experimentally validated in this follow-up.

## Known limits and remaining checks

- **Not a general HONOR PROCESS_TEXT ban:** stock selection menus show and launch Qwen from another package. Their overflow is scrollable, so first-row visibility is insufficient.
- Huawei Browser's tested read-only menu does not offer third-party handlers. Installed ReadEra Premium 19.12.27+1120 explicitly sends its Translate action to Yandex. Adding TRANSLATE or changing app defaults cannot redirect an explicit foreign component. Qwen cannot force other apps to rebuild private menus through public Android APIs.
- ReadEra Share and Copy → Qwen tile are verified alternatives. The old ReadEra version's general settings did not expose a translator choice; newer versions were not installed/tested.
- Samsung repeat run, Telegram/other app menus, complete Huawei Notes selection tests, API 34+ tile/lockscreen and empty/nontext tile behavior remain phone checks.
- Longer Unicode/multiline layouts, rapid user interactions, offline/timeout/rate-limit and backup/restore remain broader manual checks; relevant domain/network/lifecycle paths have unit coverage.
- Unofficial Google fallback live success remains unverified. The earlier unauthenticated probe returned HTTP 429. AI live success does not establish Google reliability.

Groq contract references: [model](https://console.groq.com/docs/model/qwen/qwen3.8-27b), [API](https://console.groq.com/docs/api-reference), [reasoning](https://console.groq.com/docs/reasoning). Authenticated calls in this follow-up used only the tablet's existing app configuration, with no credential exposure.
