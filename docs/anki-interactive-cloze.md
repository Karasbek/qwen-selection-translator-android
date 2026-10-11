# Interactive local Cloze editor

`CardContent.selections` stores explicit `ClozeSelections` with non-overlapping
UTF-16 ranges (end exclusive) in the original Explain and an optional standalone
header cloze. `ClozeRanges.initial` selects case-insensitive exact occurrences
at Unicode word boundaries, including complete multiword phrases. If there
are no occurrences it selects a standalone header; otherwise no header is added.
An explicit empty selection stays empty and prevents saving, rather than
silently restoring automatic clozes.

`InteractiveClozeText` renders source-position-preserving masked characters for
hidden ranges. Themed primary styling/underline means hidden; secondary styling
means a suggestion. Tapping any word toggles hiding, and tapping inside a hidden
phrase reveals the whole phrase. Long-press then drag selects whole words as a
phrase; intersecting ranges are replaced by the phrase without overlaps.
Normal swipes remain available for Preview scrolling. The plain Explain/Front
editor remains the fallback for typing corrections. Text or studied-word edits
reset selections and show a warning; IPA edits preserve selections.

Suggestions are lightweight English suffix-stem comparisons (including a
limited decide/decision rule). They are neither a linguistic assertion nor a
source of automatic hidden ranges. Unsupported/irregular forms may not be
suggested, but any word or phrase can still be selected manually. There is no
NLP dependency, model call or network operation in this editor.

`ClozeCardBuilder` validates ranges and escapes original text before inserting
only owned c1 delimiters. It preserves case, punctuation, words, examples and
line breaks. All selected ranges use the same c1 ordinal, yielding one card.
Invalid/overlapping/out-of-bounds ranges and an entirely hidden meaning are
rejected. Saving rebuilds Front through the existing fresh validation in
AnkiSaveCoordinator. IPA, field order, duplicates, write-once state and
OutcomeUnknown are unchanged.

Selections live in the existing AnkiDraftViewModel, survive rotation/recreation
and reopening within a popup, and never depend on Compose recomposition.
Whole Explain, offline IPA and translation prompts/cache are unchanged.

## Verification

Targeted deterministic JVM tests followed by full JDK 21 Gradle regression with
`--max-workers=2 --no-parallel`: 214 JVM tests passed; assembleDebug,
lintDebug and compileDebugAndroidTestKotlin passed (0 lint errors, 14 existing
warnings). Honor KRJ-W09 / Android 10: 48 Android tests passed, including
inline taps, arbitrary words, repeated tap, long-press phrase selection,
normal swipe in a scrolling Preview, manual editor/keyboard, dark theme and
selected ranges surviving rotation/recreation. Existing cache, Copy/Replace,
settings, save warnings/OutcomeUnknown and fake write-safety checks passed.

A separate physical ReadEra smoke with the public `optimization` fixture
passed: real ReadEra translation action and Qwen Anki button, counted fake
Explain/Translate, exact Optimization hidden without a header, optimize only
suggested, real bundled offline IPA, 2 fake translation calls and 0 Anki writes.
No paid Groq requests or real collection writes were performed. Gesture checks
use the same optimization/optimize fixture in dedicated device UI tests.

Limitations: suggestions cover only a small English suffix heuristic and may
miss relatives or suggest unrelated words. Users control all such selections.
Phrase selection uses visible text; it does not implement automatic viewport
scrolling during a held drag. Text edits reset selections with a warning.
The existing draft stays in process memory, including configuration recreation,
not after process death or closing the entire popup.
