# Anki preview — phase 2

The popup's **В Anki** button opens an editable draft and starts a separate card request.
Opening the translator still starts only its existing independent Explain/Translate operations.
Their prompts, languages, order, invocation routing, Copy/Replace and secret storage are unchanged.
Anki-only settings changes preserve both translation results without issuing new translation calls.

## Components

- `CardGenerator` / `GroqCardGenerator`: separate JSON-object request using the configured
  model, endpoint and Keystore-backed API key. Sends selected form, known source language,
  target/explanation language preferences and available existing results as data. No automatic retry.
  [Groq JSON mode documentation](https://console.groq.com/docs/structured-outputs).
- `CardContent`: plain-text word, part of speech, definition and example. Model IPA is ignored.
- `ClozeCardBuilder`: finds exactly one literal, Unicode-aware, case-insensitive whole-form
  occurrence in each text. Does not stem, replace substrings or silently normalize spelling.
  Ambiguous/missing occurrences produce editable validation errors. Encode HTML, quotes, braces,
  ampersands and colons before inserting exactly two `{{c1::...}}` delimiters. Line breaks become
  `<br>`; the paragraph separator is `<br><br>`. No custom template or stylesheet is created.
- `AnkiCardDraft`: original selection, edited content, IPA, target IDs, ordered five fields and
  validation. Compatibility uses the phase-1 metadata gateway. Missing targets never fall back.
- `DictionaryIpaLookup`: [Free Dictionary API](https://dictionaryapi.dev/), exact English entries
  only. A small optional lookup (maximum five seconds), with no audio download. Unsupported
  language/phrase, missing/error response or ambiguous pronunciation leaves Pinyin empty.
  A returned dictionary pronunciation is marked with its source. Manual edits are marked manual;
  changing the word clears old IPA. No model pronunciation or Russian transliteration is used.
- `AnkiDraftViewModel`: separate activity-scoped, cancellable job and stale-response guard.
  Rotation keeps edits/results without new requests. Rapid repeated clicks are ignored.
  Cancellation/failure preserves existing edits; regeneration asks before replacing manual edits.
- `AnkiDraftPreview`: scrollable dialog, editable word/POS/definition/example/IPA, generated Front,
  chosen deck/model and validation. No Save/add-note button. Closing it returns to the translation
  and retains edits for reopening during the same popup. Reopening refreshes target metadata only.

The draft is kept in memory until the translation popup closes, not persisted to disk. A new
selection requires confirmation before replacing an edited previous draft. Existing translation
failures and card failures are independent. Dictionary failures never turn successful generation
into an error. Metadata and provider exceptions are not logged or exposed as raw messages.

## Phase 3 (not implemented)

Add explicit, user-confirmed saving through a separate write capability. Immediately revalidate
selected IDs, model kind/fields and draft content before adding. Handle duplicate notes, failed
writes and successful note IDs. Do not create or modify decks, note types, templates or sync.
`AnkiGateway` still exposes only installation/permission status and deck/model metadata reads.

## Validation

JDK 21, existing Gradle/AGP, maximum two workers, no parallel builds. Required command passed:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew --max-workers=2 --no-parallel testDebugUnitTest assembleDebug lintDebug compileDebugAndroidTestKotlin
```

116 JVM tests passed (39 added); 26 Android tests passed on Honor Android 10 (7 added).
Lint: zero errors and the same 14 existing warnings. Unit tests cover builder/validation, Unicode,
whole-form boundaries, HTML/Cloze injection, structured responses and HTTP failures, dictionary
lookup, explicit requests, cancellation, stale responses and preservation of manual edits/IDs.
Device tests cover preview editing/validation, regeneration confirmation, cancellation, actual
Activity rotation/recreation, and the existing popup/settings/API regression suite.

Manual smoke verification used a temporary synthetic TXT in ReadEra: selected word → ПЕРЕВОД →
Qwen → В Anki → real generation → editable Front → return to translation. The generated card
passed validation. Dictionary access was unavailable in the verification network, so IPA stayed
blank as designed; sourced Unicode IPA and manual editing remain covered by deterministic tests.
The keyboard overlap discovered on Android 10 was fixed with dialog window insets and bounded
height; the return button was manually verified above the visible keyboard. Editing rebuilt Front,
ambiguous repetitions were rejected, and cancelling real regeneration preserved corrections.
No notes, templates, decks, media or sync operations were performed. Debug APK updated in place.

## Changed files

Production files under `app/src/main/java/com/karas/qwentranslator/`:

- `anki/CardGenerator.kt`
- `anki/ClozeCardBuilder.kt`
- `network/GroqCardGenerator.kt`
- `network/DictionaryIpaLookup.kt`
- `ui/anki/AnkiDraftViewModel.kt`
- `ui/anki/AnkiDraftPreview.kt`
- `ProcessTextActivity.kt`
- `di/AppContainer.kt`
- `ui/popup/PopupScreen.kt`
- `ui/popup/ProcessTextViewModel.kt`

JVM tests under `app/src/test/java/com/karas/qwentranslator/`:

- `anki/ClozeCardBuilderTest.kt`
- `anki/AnkiCardDraftTest.kt`
- `network/GroqCardGeneratorTest.kt`
- `network/DictionaryIpaLookupTest.kt`
- `ui/anki/AnkiDraftViewModelTest.kt`
- `ui/popup/ProcessTextViewModelTest.kt`

Android tests under `app/src/androidTest/java/com/karas/qwentranslator/`:

- `ui/anki/AnkiDraftPreviewTest.kt`
- `ui/popup/PopupScreenTest.kt`
- `ui/popup/ProcessTextActivityTest.kt`

Documentation: `docs/anki-preview.md` and `docs/anki-integration.md`.
