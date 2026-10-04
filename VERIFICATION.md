# Verification — reading-popup redesign, 2026-10-04

Baseline: `8eb615e` (working Android integration) preserved in Git. JDK 21, Gradle 9.4.1, AGP 9.2.0, existing SDK Platform 37 / Build Tools 36.0.0 and dependency versions retained.

## Build and JVM tests

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew testDebugUnitTest assembleDebug lintDebug
```

| Check | Result |
| --- | --- |
| JVM tests | 67 passed, 0 failures/errors/skipped |
| assembleDebug | BUILD SUCCESSFUL; APK created |
| lintDebug | 0 errors / 14 warnings |
| compileDebugAndroidTestKotlin | BUILD SUCCESSFUL |
| Honor instrumentation | 16 passed, 0 failures |
| git diff --check | Clean |

The new tests cover default order/options, enabled blocks and rejection of all-disabled state, actual on-disk Preferences DataStore restart and old-key migration, Settings moves/options/key preservation, concurrent in-flight requests, partial success, local Retry, cancelled stale replies/new invocation, cancellation on ViewModel clearance, changed/unchanged Settings return, incremental enabling, request option mapping, typed failures, and readonly/editable/translation-link replacement behavior. Existing prompt/provider/HTTP/engine/Invocation/link parsing coverage is retained/adapted to the automatic-result model. No real API calls or credentials are required.

A combined JVM/emulator run encountered a host/environment failure: emulator process exited with SIGSEGV and a Gradle test worker connection timed out. A bounded `--max-workers=2` repeat succeeded. This failure was not counted as a passing run or hidden by changing assertions.

APK:

`/data/qwen-selection-translator-android/app/build/outputs/apk/debug/app-debug.apk`

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/test-results/testDebugUnitTest/TEST-*.xml`, `app/build/reports/lint-results-debug.html` / `.txt`.

Nonblocking lint warnings are existing Keystore synchronous-commit/KTX suggestions, target/dependency/Gradle suggestions, release resource shrinking, and the existing translation-link App Link/unique-data-attribute suggestions. No baseline, blanket suppression, version upgrades or changes to that regression-critical filter were made.

## Historical pre-crash Compose UI checks

An existing API 35 x86_64 AVD was available; no image downloaded and no physical device connected. It was started read-only, with temporary disk writes and snapshot saving disabled.

```bash
ANDROID_SERIAL=emulator-5580 JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew connectedDebugAndroidTest
```

Tests cover default card order, independent loading/partial success, block Copy payloads, failed-block Retry, successful editable Translation Replace, readonly legacy links, long/narrow landscape content in dark theme with accessible Close, disabled/reordered blocks, both errors and overflow Settings, last-enabled toggle protection, Move controls, explanation-language and example callbacks, and inside/outside taps.

Debug Compose previews also compile: 320/360dp portrait, short text, partial success, two errors, one block, long dark landscape. These previews are fixtures, not live Groq/physical-device evidence.

## Integration/security preservation

Source diff against `8eb615e` confirms unchanged Manifest, all intent parsers (including `#auto/en/<text>`), `SecretStore`, clipboard/tile adapters and backup rules. `stateAlwaysHidden|adjustResize` remains set. Standard activity launch mode, `onNewIntent`/`setIntent`, default RESULT_CANCELED and RESULT_OK/EXTRA_PROCESS_TEXT delivery remain intact; Activity only adapts the new state for UI/current replacement.

No Accessibility Service, overlay, background clipboard watcher or new permission. New normal preferences contain no credentials. A real DataStore restart test preserves the old provider settings and new reading options. Full Keystore/device restore behavior was not newly tested.

## Current redesign physical-device regression — 2026-10-04

Resumed the existing uncommitted worktree after the crash; no reset/restore/clean or implementation rewrite. Recovery backup `/data/qwen-translator-recovery-20261004-171306` retained. Audit covered tracked diff and every new source/test/preview file. All requested redesign behaviors were already implemented; no production device defect was reproduced in this pass.

USB device identified with `adb devices -l` and getprop: HUAWEI / HONOR, **KRJ-W09 / HWKRJ**, Android **10 / API 29**, build **KRJ-W09 3.1.1.267(C10E1R1P1)**, MagicUI 3.1.1. Installed the verified APK using `adb install -r`; the Huawei installer was confirmed on its identified Qwen screen. No app data cleared, no API key removed or printed. Existing encrypted key continued to produce live Groq/Qwen results after installs and settings saves/restarts.

| Runtime scenario | Current result |
| --- | --- |
| MainActivity / Settings | Results, Explanation, Translation and AI provider sections accessible; source Auto, target ru, English/Simple/examples ON, both blocks ON, Explanation first |
| Direct readonly PROCESS_TEXT | Both start automatically; explanation first; live independent results; no Replace; keyboard hidden; Close/Back work; dark theme usable |
| Partial success | Real Translation success observed while Explanation was still loading; a successful Explanation also remained alongside a separate Translation timeout |
| Copy per block | Both payloads pasted into the real EnglishWords search field; explanation copies explanation only, translation copies output only |
| Editable PROCESS_TEXT | EnglishWords 4.3.4 native selection → ⋮ → scroll → Qwen; auto-start, Replace changes `Hello world` to `Здравствуйте world` |
| Selection-edge whitespace | Select all of `  Hello  ` in EnglishWords → Qwen → Replace returned `  Здравствуйте  `; Copy returned `Здравствуйте` without selection padding |
| ReadEra direct regression | Premium 19.12.27+1120: harmless TXT selection → ⋮ → **ПЕРЕВОД** → ACTION_VIEW → Qwen, both results automatic; readonly, no Replace; no Share/Tile substitute |
| Result order / restart | Save Translation → Explanation, force-stop/reopen, invoke popup: Translation first; default order restored |
| Enable/disable | Explanation-only and Translation-only popups verified, including restart; tapping last enabled switch leaves it ON; both restored ON |
| Explanation options / restart | Russian/Detailed/examples OFF saved through UI, force-stop/reopen produced a detailed Russian explanation without examples; English/Simple/examples ON restored |
| Network error / local Retry | Wi-Fi temporarily disabled with restoration in `finally`; both blocks showed curated timeout messages. After network restoration, Translation Retry succeeded while the successful Explanation remained unchanged |
| Long text / rotation | Long source and multiline Translation scroll in actual dark landscape; Close stays visible; answers survive return to portrait; original rotation settings restored |
| New Intent / stale replies | Two rapid adb PROCESS_TEXT calls, second with SINGLE_TOP, confirmed delivery to existing Activity; only the new source/results survive. Counted non-cooperative late replies also rejected by the activity test |
| Close cancellation / duplicate requests | Device activity tests count exactly two requests across portrait → landscape → portrait and recreation; four across two invocations; closing an in-flight popup cancels both |
| Logcat | Cleared before regression; AndroidRuntime / ActivityTaskManager / Qwen tags inspected; no Qwen FATAL EXCEPTION, no app credential logging |

For offline testing, the initial 45-second polling harness expired before the finite AI + translation-fallback chain finished; Wi-Fi was restored by `finally`. Both timeout states were then observed, followed by partial success and a successful block-local retry. This harness timeout is not counted as a passed assertion or an app crash. Original Wi-Fi ON, rotation lock/portrait and system dark mode were restored/preserved.

### Physical-device instrumentation

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew compileDebugAndroidTestKotlin assembleDebugAndroidTest --max-workers=2
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.karas.qwentranslator.test/androidx.test.runner.AndroidJUnitRunner
```

**16 tests passed on Honor API 29**: 9 popup Compose tests, 4 Settings Compose tests and 3 new activity lifecycle tests. The activity tests temporarily inject a counted fake engine and an isolated cache DataStore through test-only reflection, then restore the container; user preferences and credentials are untouched. They verify real orientation changes/recreation without repeat calls, `onNewIntent`/late non-cooperative replies/latest intent after recreation, and cancellation on Activity close. Fake instrumentation results are distinct from the live Groq/ReadEra/EnglishWords checks above.

Current screenshots: [ReadEra direct popup](docs/runtime/honor-redesign-readera-popup.png), [EnglishWords editable popup](docs/runtime/honor-redesign-englishwords-popup.png). Old screenshots and pre-redesign findings remain in [HONOR_VERIFICATION.md](HONOR_VERIFICATION.md) as historical evidence.

Final commands repeated after the device audit. The final combined run additionally used `--rerun-tasks` (61 tasks executed), producing fresh unit/lint/build/compile reports after crash recovery:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew testDebugUnitTest assembleDebug lintDebug --max-workers=2
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew compileDebugAndroidTestKotlin --max-workers=2
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Final smoke passed after reinstalling the APK from the fresh run: MainActivity, readonly PROCESS_TEXT, ReadEra's direct ПЕРЕВОД and EnglishWords native editable Replace (`Hello world` → `Привет world` in this last response). Final filtered logcat had 0 FATAL EXCEPTION entries. APK SHA-256: `f023ca8ee34215cb971c67544a79f44827a8723dfb383dedecc3d45474c6c712`. Build outputs remain ignored. No push.

Remaining limits: Samsung/newer Android device coverage, hosts with custom action menus, full Keystore backup/restore, live authentication/rate-limit rejection, unofficial Google reliability and provider input/token limits. Auto remains displayed when the AI response does not report source-language detection. History/dictionary/offline/streaming remain unimplemented.
