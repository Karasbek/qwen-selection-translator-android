# Full Explain → local Anki preview + offline IPA

This supersedes the earlier structured Groq card generation and conservative
semantic-extraction proposal. Explain/Translate prompts and their RAM cache
are unchanged. Anki preparation has no CardGenerator dependency.

`ProcessTextActivity.preparationContext()` supplies the current successful
Explain as a whole. `AnkiDraftViewModel.prepare()` creates a draft locally.
Missing, disabled, failed or loading Explain leaves a manual editor and a
clear explanation of how to obtain the text; it never triggers a model call.
Explicit reprepare uses current Explain, and replacing manual edits requires
confirmation. Reopening preserves edits. Changing the studied word does not
reuse an old Explain on reprepare.

`ClozeCardBuilder.buildExplanation()` puts a standalone `{{c1::word}}` first,
then the complete Explain including its existing example. All safe exact
case-insensitive word/phrase repetitions use the same c1. Unicode word
boundaries, apostrophes and hyphens protect against partial replacements.
HTML and existing cloze-like input are escaped before insertion. Newlines are
preserved as `<br>`. No definition/example separation or invented examples.
Other word forms are left unchanged; preview warns users to review and edit
possible answer leaks. This is not semantic correctness or morphology analysis.
Multiple c1 occurrences generate one card. The editor updates Front locally.

`OfflinePronunciationRepository` implements `PronunciationRepository`. The
application owns one lazy dictionary buffer plus packed line offsets (about
3.7 MB together); lookup uses binary search on sorted rows off the UI thread.
Exact case-insensitive English forms only; no stemming or guesses. `auto`
permits looking in the explicitly labelled English (US) dictionary; other
known source languages are rejected. One IPA auto-fills Pinyin; multiple
variants leave it blank and expose explicit choice buttons; missing entries
leave it blank. Manual IPA editing is retained. No network IPA fallback is
wired. Historical network generator/provider classes remain for their isolated
tests, but cannot be reached from the production Anki UI/container.

Bundled `app/src/main/assets/ipa/en_US.txt`: 125,927 lines, 3,180,267 bytes.
Pinned ipa-dict revision: `43c3570eb3553bdd19fccd2bd0091534889af023`.
The rows are sorted without changing words or IPA. MIT licenses, the underlying
CMU notice, credits and hashes are bundled in the same asset directory.
See `NOTICE.md` and `provenance.json` there for provenance.

Cold Explain + Translate + Anki now uses two Groq calls, rather than three.
With both translation cache hits it uses zero; Anki always adds zero. Token
accounting never treats local preparation as a Groq call. Saving still uses
AnkiSaveCoordinator and all fresh metadata/field/type validation, duplicate
warnings, write-once state, OutcomeUnknown handling and explicit confirmation.
No collection writes occur during preparation or tests using fake gateways.

## Validation

JDK 21, `--max-workers=2 --no-parallel`: targeted builder, repository, draft
lifecycle and save-coordinator tests; full regression: 201 JVM tests,
assembleDebug, lintDebug and compileDebugAndroidTestKotlin all passed.
Lint: zero errors, 14 existing warnings. Debug APK and test APK installed on
Honor KRJ-W09 / Android 10 (`JQFBB20817903050`); all 42 Android tests passed.
A separate opt-in physical ReadEra smoke passed after pressing the actual
ReadEra translation action and Qwen Anki button on the public fixture.
It verified the complete fake Explain, real bundled `/əˈbændən/`, two counted
fake Explain/Translate calls, zero extra Groq calls and zero fake Anki writes.
No paid Groq requests or live collection writes were performed.

The dictionary contributes 1,091,875 compressed bytes to the debug APK;
uncompressed asset size is 3,180,267 bytes. Pronunciation variants reflect
upstream English US data, not a pronunciation-quality guarantee for all words.
