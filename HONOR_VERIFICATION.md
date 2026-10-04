# HONOR tablet selection integration — 2026-10-04

## Device identified over USB

| Property | Value |
| --- | --- |
| Manufacturer / brand | HUAWEI / HONOR |
| Model / device | KRJ-W09 / HWKRJ |
| Android / API | 10 / 29 |
| Build | KRJ-W09 3.1.1.267(C10E1R1P1) |
| EMUI property | EmotionUI_10.1.1 |
| Magic property | 3.1.1 |

The device was identified from getprop, rather than assumed to be a particular Pad generation.

## Findings

There is **no general ban on third-party PROCESS_TEXT handlers on this firmware**. In stock Android TextView and EditText, **⋮ → scroll down → Qwen перевод** appears alongside Petal Search, Petal Translate, DeepSeek, 2GIS and Yandex. Clicking it opens the popup and translates automatically.

This was also checked from a **different package**: a temporary debug build with applicationId `com.karas.qwentranslator.selectionprobe` ran the plain widget fixture. Both Qwen handlers appeared in the native overflow; selecting the main app produced:

```text
ActivityTaskManager: START u0 {act=android.intent.action.PROCESS_TEXT typ=text/plain hwFlg=0x10 cmp=com.karas.qwentranslator/.ProcessTextActivity (has extras)} from uid 10493
```

The main app's UID was 10492. Thus this was a real cross-package selection launch, not merely same-package eligibility or an adb direct intent. The temporary package had no copied settings/key and was removed after testing; the normal applicationId was restored and all checks rerun.

An initial stock-widget probe omitted View IDs and consequently had no PROCESS_TEXT actions. Adding IDs restored the native overflow. This was a defect in the diagnostic fixture, not a production Manifest fix. The debug fixture now assigns IDs and does not alter ActionMode menus.

| Host | Observed result |
| --- | --- |
| Stock TextView, debug fixture | Qwen in scrollable overflow; readonly popup, live translation |
| Stock EditText, debug fixture | Qwen in overflow; Replace returns translated selection to the actual field |
| Same stock fixture in separate package | Main Qwen visible and launched by the other package |
| Huawei Browser, local harmless HTML page | Copy / Select all / Search / Share; no third-party PROCESS_TEXT list in the tested read-only selection |
| ReadEra Premium 19.12.27+1120, harmless TXT | Custom Translate / Share / Web search actions; Translate explicitly starts Yandex; Share opens Qwen successfully |
| Huawei Notes | A test note was opened; selection-menu behavior was inconclusive, not counted as a pass |
| Telegram / Chrome / Samsung | No message/draft content tested; Chrome not installed; Samsung not connected for this session |

ReadEra's observed “ПЕРЕВОД” is **an explicit PROCESS_TEXT launch from ReadEra (UID 10334)**, not a TextClassifier default, Huawei Search action, or an implicit translation resolver:

```text
ActivityTaskManager: START u0 {act=android.intent.action.PROCESS_TEXT typ=text/plain hwFlg=0x10 cmp=ru.yandex.translate/.ui.activities.QuickTrActivity (has extras)} from uid 10334
```

No translator chooser/preference was found in that version's inspected general settings. Adding ACTION_TRANSLATE, updating/relaunching ReadEra, and changing our label did not change its explicit Yandex launch. Another application's manifest/default app cannot redirect an Intent with an explicit foreign component. This does not prove a hardcoded OEM blacklist or cover every ReadEra version. No Yandex/Huawei app was disabled, replaced or cleared.

PackageManager can enumerate PROCESS_TEXT handlers normally. Logcat does not log every PackageManager query; absence of a query log was not used as proof of filtering. A public Huawei framework mirror for another firmware was consulted as a diagnostic hint, not treated as the installed tablet's source code.

## Integration comparison

Yandex's APK was pulled solely to inspect its Manifest/resources. Its business logic was not decompiled or copied.

| Metadata | Qwen | Yandex QuickTrActivity |
| --- | --- | --- |
| exported / enabled | true / enabled | true / enabled |
| minSdk / targetSdk | 26 / 36 | 26 / 36 |
| PROCESS_TEXT | DEFAULT + text/plain | DEFAULT + text/plain |
| Additional actions | SEND; now TRANSLATE | DEFINE, TRANSLATE |
| launchMode | standard | singleTask (2) |
| taskAffinity | normal app affinity | `.copydrop` |
| excludeFromRecents | true | true |
| noHistory | absent (false) | true |
| Theme | Theme.QwenTranslator.Popup, translucent | APK theme resource 0x7f1405bd; runtime compact popup |
| Window layout | absent | top/end gravity 0x800035, min/default height 400dp |
| configChanges / soft input | no override | 0xd20 / 0x2 |
| Activity permission / special metadata / directBootAware / resize override | absent | absent on QuickTrActivity |
| Package state | installed, enabled, not hidden/suspended/stopped | same |
| Distribution | sideloaded debug, v2 signature | AppGallery release, v3 signature |

The successful cross-package launch disproves that our current targetSdk, sideload/debug state, excludeFromRecents, or standard launch mode inherently prevent selection integration on this device. There was no reason to copy Yandex's singleTask/noHistory behavior or lower targetSdk.

## Changes and usable paths

- Distinct **Qwen перевод** selection/share label and instructions explaining overflow scrolling.
- Standard [ACTION_TRANSLATE](https://developer.android.com/reference/android/content/Intent#ACTION_TRANSLATE) support (EXTRA_TEXT; readonly) in the same popup/engine; verified by direct intent and resolver query. It does not override ReadEra's explicit component.
- Working ReadEra fallback: select → ⋮ → Share → swipe left to the second Huawei Share page → Qwen перевод. No duplicated translation implementation.
- Faster optional fallback: Copy → pull down Quick Settings → **Qwen перевод**. The tile was added via `cmd statusbar add-tile`, tapped in the actual panel, and opened a translated popup. It remains installed for use. It can be removed/reordered through the usual Edit UI.
- Tile uses only a foreground, one-shot clipboard read; nonexported adapter, no clipboard listener, Accessibility or overlay permission. Android 10 focus restriction follows [Android clipboard privacy rules](https://developer.android.com/about/versions/10/privacy/changes#clipboard-data); launch API follows [TileService](https://developer.android.com/reference/android/service/quicksettings/TileService).

Observed tile log (no text/key logged by the app):

```text
ClipboardService: callingPackage:com.karas.qwentranslator, uid =10492, isFocusedWindow =true
ClipboardService: getPrimaryClip pkg=com.karas.qwentranslator, intendingUid=10492, isAccessAlowed=true
ActivityTaskManager: START ... act=android.intent.action.SEND typ=text/plain ... cmp=com.karas.qwentranslator/.ProcessTextActivity
```

## Runtime checks

- MainActivity opens; existing encrypted user key survives repeated install -r and is used successfully. The key was never printed, copied into diagnostics, or changed.
- Live Groq Translate: Hello → Здравствуйте, actual qwen/qwen3.8-27b / api.groq.com shown.
- Live Explain: simple English greeting explanation, source-language target shown.
- Copy followed by stock EditText Paste inserts the translated text.
- Real editable PROCESS_TEXT Replace changes only Hello in `Hello world. This is an editable translation test.`; Android delivers RESULT_OK to the host, which becomes `Здравствуйте world. This is an editable translation test.`.
- Readonly and Share popups omit Replace. Tile popup also omits Replace.
- Close/Back return to the caller; landscape rotation retains a usable popup/result and returns to portrait without a crash. Original rotation settings were restored.
- Missing PROCESS_TEXT extra closes safely; 12,001-character Share input shows the input-limit error without a provider request.
- ACTION_TRANSLATE reads EXTRA_TEXT without MIME and opens a readonly popup.

Remaining device checks: Samsung repeat run, Telegram and other custom menus, newer Android (34+) tile launch/lockscreen behavior, full key-save/backup/restore tests, offline/rate-limit behavior and longer Unicode/multiline layouts. Browser/ReadEra menu limitations remain; no public API was found to force their private action lists or explicit Yandex target to change.

## Reproduction

```bash
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.build.display.id
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.emui
adb shell getprop ro.build.version.magic
adb shell pm path com.karas.qwentranslator
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p com.karas.qwentranslator
adb shell cmd package query-activities --brief -a android.intent.action.PROCESS_TEXT -c android.intent.category.DEFAULT -t text/plain -p com.karas.qwentranslator
adb shell cmd package query-activities --brief -a android.intent.action.SEND -c android.intent.category.DEFAULT -t text/plain -p com.karas.qwentranslator
adb shell cmd package query-activities --brief -a android.intent.action.TRANSLATE -c android.intent.category.DEFAULT
adb shell am start -n com.karas.qwentranslator/.SelectionProbeActivity
```

Long-press Hello in the plain selectable text or editable field, open ⋮, and scroll down. The diagnostic activity is debug-only and adds no menu items. UI hierarchy often omits native floating-toolbar nodes on this firmware; screenshots were needed to observe its scrollable list.

```bash
adb shell cmd statusbar add-tile com.karas.qwentranslator/.ClipboardTileService
adb shell cmd statusbar expand-settings
# Tap Qwen перевод after explicitly copying harmless sample text.
adb logcat -d -s ActivityTaskManager AndroidRuntime ClipboardService
```

Full package dumps and temporary screenshots were kept outside the repository. Only nonsecret integration excerpts are recorded here; never dump settings/password fields or HTTP credentials for verification.
