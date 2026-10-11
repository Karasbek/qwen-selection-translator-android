# Anki saving — phase 3

The existing preview now offers **Сохранить в Anki**. Only an explicit tap starts saving.
Explain/Translate prompts, generation, dictionary lookup, routing, result order, Copy/Replace,
Groq settings and Keystore storage are unchanged. No collection management or sync is added.

## API and field mapping

Implementation uses the official ContentProvider contract, verified against **v2.25.1**:

- [API overview](https://github.com/ankidroid/Anki-Android/wiki/AnkiDroid-API)
- [AddContentApi](https://github.com/ankidroid/Anki-Android/blob/v2.25.1/api/src/main/java/com/ichi2/anki/api/AddContentApi.kt)
- [provider implementation](https://github.com/ankidroid/Anki-Android/blob/v2.25.1/AnkiDroid/src/main/java/com/ichi2/anki/provider/CardContentProvider.kt)
- [contract](https://github.com/ankidroid/Anki-Android/blob/v2.25.1/api/src/main/java/com/ichi2/anki/FlashCardsContract.kt)

`AnkiSaveCoordinator` reads current settings and fresh installation/permission/deck/model metadata,
rebuilds the edited draft locally, checks ordered fields and requires positive target IDs. The
note type must be Cloze with exactly `Front`, `Pinyin`, `Дополнение оборота`, `Image`, `Sound`.
Filtered decks are rejected before writing. It checks again after duplicate lookup; missing targets
never fall back. Changed settings produce a dialog naming the new destination and require another
explicit confirmation. Invalid, stale or forged Front (including c2) cannot pass builder equality.

`AndroidAnkiGateway.addNote` inserts exactly five builder strings, joined with U+001F, into the
provider's notes URI. It sends no tags. The optional IPA stays in Pinyin and the last three fields
are empty. It records the positive returned ID immediately through an insertion callback. As in
AnkiDroid's wrapper, it then queries cards of **only this new note** and puts its one ordinal-0 card
into the chosen ordinary deck through the official card URI. Single-note insert ignores `deckId`
query parameters in 2.25.1, so using that parameter would silently target the model's default deck.
There is no direct database access and no edits to pre-existing notes/cards, templates or models.

Verification reads only the new note's model/fields and its card ordinals/deck IDs. Success requires
matching all five fields/model and exactly one ordinal-0 card in the chosen deck. A mismatch yields
a diagnostic without a second move, delete or insert. All provider operations run on Dispatchers.IO.

## Operation ownership and durable protection

Each prepared/edited draft has an operation UUID. `Ready → Checking → Saving → Saved(noteId)` is
owned by the application coordinator, independent of Activity/ViewModel cancellation. A synchronized
claim blocks double taps/concurrent callbacks and prevents simultaneous saving operations. Compose
only observes state; it does not initiate requests during recomposition. Saved/unknown operations
are locked; errors preserve editor text. Reopening the same popup retains its operation, and a new
popup sees a pending receipt for the same word. Rotation keeps the activity-scoped editor.

`FileSaveReceiptStore` durably writes an intent **before** calling the provider, then records an
inserted ID before subsequent card calls. It uses Android AtomicFile in `noBackupFilesDir` and
stores UUIDs, SHA-256 content/word fingerprints, returned IDs and confirmation flags. No selection,
Front, definition, example, IPA, API key or collection dump is persisted/logged. Unreadable journals
fail closed. Receipts are device-local and not cloud-backed or transferred. They are protection
metadata, not a user-facing note history. Draft text remains in memory, as in phase 2.

A preflight failure is `FailedBeforeWrite`. Once addNote has been invoked, exceptions, null/invalid
IDs and verification failures are conservatively `OutcomeUnknown`. The 2.25.1 wrapper can insert
successfully and fail later querying/moving cards; a failed call never proves absence. Interrupted
intents survive process death and block another attempt for the same word. There is **no automatic
retry**, including after process recreation or UI dismissal. A known ID permits **Проверить
сохранение снова**, which performs read-only confirmation, never insertion/movement. Without an ID,
or with a missing/mismatched note, the operation remains unresolved and requires checking AnkiDroid.
There is deliberately no in-app override of an unresolved insertion.

## Duplicates

The official `notes_v2` query uses model ID plus the API's SHA-1 first-field checksum (strip generated
HTML tags/decode entities). Only checksum candidates are read and their complete Front compared;
checksum collisions cannot trigger exact equality. Bound parameters are used; no full collection
scan or browser search for the word is performed. A failed lookup stops before writing.

Local receipts identify completed operations and warn if the word was already successfully saved
through Qwen. A new operation may deliberately add another definition or even an exact duplicate
**after** the warning and explicit **Добавить ещё одну** confirmation. Passing a confirmation flag
without a preceding warning cannot bypass checks. Current Saved operations cannot be re-tapped.
No absolute duplicate detection is promised: other apps' different definitions, cleared application
storage, another device/collection and concurrent external writes are outside this local protection.
Metadata checks and insertion are separate provider calls, not an atomic server transaction.

## Validation

See the final verification record below. All device writes in the automated suite use a fake gateway
and isolated in-memory receipts. The filesystem journal tests use temporary app-cache directories.
Device API probes only read metadata and search a unique synthetic Front; they do not insert notes.

### Verification record

Required command passed with JDK 21, two workers and no parallel Gradle builds:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew --max-workers=2 --no-parallel testDebugUnitTest assembleDebug lintDebug compileDebugAndroidTestKotlin
```

`assembleDebugAndroidTest` was also built in that same single Gradle invocation.
145 JVM tests passed (29 new saving tests); 36 Android tests passed on Honor KRJ-W09 Android 10
(serial JQFBB20817903050; 10 new device tests). Lint: zero errors, the same 14 existing warnings.
Fake tests cover explicit-only insertion, double/concurrent taps, lifecycle recreation/dismissal,
IDs, failures before/after the insertion boundary, null IDs, verification failures, missing/changed
targets, filtered decks, strict field order/c1, duplicates/override, durable journal corruption,
no automatic retry, edited drafts and read-only rechecking of known IDs. Existing suites remain green.

Honor has AnkiDroid 2.25.1. Its metadata and constrained official duplicate API queries passed.
Manual ReadEra selection → ПЕРЕВОД → Qwen → В Anki generated a valid draft with two c1 insertions,
showed the existing custom Cloze model and enabled Save only after validation. Manual IPA editing,
keyboard-visible Save/Return controls and return to the preserved translation were checked.
The optional dictionary lookup was unavailable and correctly left IPA blank before manual editing.
Rotation/recreation and insertion during popup dismissal were tested with the fake gateway.
The temporary synthetic ReadEra text file was removed after the smoke check.

**Live-write verification is pending.** The user answered “давай” to permission for exactly one
synthetic note, but has not yet specified the existing test deck required by the requested scope.
No real save was triggered, no note ID was created/claimed and no collection or sync was changed.
Real insertion, Anki front/back rendering and post-sync visibility on other devices remain unverified.
