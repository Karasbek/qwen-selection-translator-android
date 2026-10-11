# Anki integration — phase 1

Read-only integration using the official ContentProvider API. No external API dependency,
collection files, note queries, template queries, inserts, updates, deletes or sync intents.
Android calls live in `AndroidAnkiGateway`; `AnkiConnection`, metadata models and compatibility
checks are JVM-testable through `AnkiGateway`. Settings use existing DataStore preferences.
Selections are applied with the existing Save button. Missing IDs remain stored and never
fall back to another deck or model. Rechecking metadata does not change selections.

## Contract verified against v2.25.1

- [Official API overview](https://github.com/ankidroid/Anki-Android/wiki/AnkiDroid-API)
- [Release contract](https://github.com/ankidroid/Anki-Android/blob/v2.25.1/api/src/main/java/com/ichi2/anki/FlashCardsContract.kt)
- [Release provider](https://github.com/ankidroid/Anki-Android/blob/v2.25.1/AnkiDroid/src/main/java/com/ichi2/anki/provider/CardContentProvider.kt)
- [Release wrapper](https://github.com/ankidroid/Anki-Android/blob/v2.25.1/api/src/main/java/com/ichi2/anki/api/AddContentApi.kt)
- [Release authority configuration](https://github.com/ankidroid/Anki-Android/blob/v2.25.1/api/build.gradle.kts)

Detect package `com.ichi2.anki` and resolve its exported, enabled provider
`com.ichi2.anki.flashcards`. Successful queries verify that the required metadata API works.
Manifest package/provider queries support package visibility on Android 11+.

The dangerous permission `com.ichi2.anki.permission.READ_WRITE_DATABASE` is required
for metadata queries on Android 10. There is no separate read-only permission.
It is requested only after the user presses the explained permission button in settings.
Denial leaves a retryable permission-required state; grant triggers a new metadata read.
Returning to Qwen or pressing Check connection refreshes installation, permission and metadata.

Queries use only these explicit projections:

| URI | Columns |
| --- | --- |
| `content://com.ichi2.anki.flashcards/decks` | `deck_id`, `deck_name` |
| `content://com.ichi2.anki.flashcards/models` | `_id`, `name`, `field_names`, `type` |

`field_names` contains the ordered field names separated by U+001F. `type=1` means Cloze;
`type=0` means ordinary. The model name is not a reliable indicator. Compatibility requires
Cloze and exactly `Front`, `Pinyin`, `Дополнение оборота`, `Image`, `Sound`, in this order.
The screen reports metadata compatibility, not validation of the HTML in existing templates.
It never edits an incompatible model.

## Future writes (phase 3; not implemented)

The official wrapper provides `addNote(modelId, deckId, fields, tags)` and returns a note ID.
In v2.25.1 it inserts the note and moves generated cards to the requested deck. Before any
future write, revalidate the explicitly selected IDs, Cloze kind and field order. Build Front
with definition/example sharing `{{c1::word}}`, IPA in Pinyin, and three empty remaining fields.
Repeated c1 refers to one Cloze card. Phase 2 adds [draft preparation and preview](anki-preview.md).
Explicit saving, duplicate/error handling and a separate write interface remain for phase 3. No model/deck creation or template modifications.

## Phase 1 validation

Resource limits: Gradle heap 1536 MiB, Kotlin daemon heap 768 MiB, two workers, no parallel builds.
Required command passed with Java 21:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew --max-workers=2 --no-parallel testDebugUnitTest assembleDebug lintDebug compileDebugAndroidTestKotlin
```

77 JVM tests passed. Lint: zero errors, 14 existing warnings; Gradle/AGP were not upgraded.
Device checks passed: 7 settings/API tests and 12 existing popup/lifecycle regression tests.
ReadEra routing and invocation parsers remain covered by existing JVM tests; a fresh manual
end-to-end ReadEra interaction was not performed in this phase. No network requests or
collection writes were required for verification. API key storage code is unchanged.
