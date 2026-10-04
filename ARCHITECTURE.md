# Architecture — verified against source

One Gradle app module, manual dependency container (`TranslatorApplication` → `AppContainer`), Kotlin and Compose. No architecture rewrite or Rust/NDK dependency.

## Actual flow

`ProcessTextActivity` adapts PROCESS_TEXT/SEND/TRANSLATE through `InvocationParser` → `ProcessTextViewModel` → `TranslatorEngine.execute(TranslationRequest)` → `DefaultTranslatorEngine` → ordered `TranslatorProvider`s → HTTP → `PopupUiState` → Compose.

- `Invocation` is Android-free. The parser's `fromValues` validates MIME/nonblank text and preserves selection whitespace. The Android adapter guards malformed extras.
- Activity handles clipboard, Android navigation and result delivery only. Standard launch mode preserves normal start-for-result semantics; a fresh launch creates a fresh invocation. Explicitly reused activity intents are handled by `onNewIntent` / `setIntent` / `acceptInvocation`.
- ViewModel starts once on creation and is retained on configuration changes. One active request job; switching operation/retrying/new invocation cancels the old job. Generation checks and cancellation prevent stale results. Recomposition only collects state.
- Popup is a translucent activity with a bottom Surface bounded to 85% of available height and 680dp width, scrolling, source/result selection, Copy, conditional Replace, Settings, Retry and Close.
- PROCESS_TEXT defaults to readonly unless explicitly false. SEND and ACTION_TRANSLATE (EXTRA_TEXT, no MIME required) are always readonly. Replace requires a current successful TRANSLATE result and returns preserved selection-edge whitespace via RESULT_OK/EXTRA_PROCESS_TEXT. Back/Close retain RESULT_CANCELED.
- Removing `noHistory` allows opening settings and returning to the same popup. Returning does not automatically repeat requests; Retry/switch uses updated settings.

`ClipboardTileService` is protected by the system-only BIND_QUICK_SETTINGS_TILE permission. A click (after unlocking if required) opens nonexported `ClipboardInvocationActivity`. Only once its window has focus does that adapter read the first clipboard item's text, validate blank/size, and open the same ProcessTextActivity with SEND. The service never reads/listens to the clipboard. The adapter uses a separate task with no affinity to return to the previous app on Close. Android 34+ uses the PendingIntent TileService launch API; older Android uses the legacy Intent overload with a narrow compatibility lint suppression.

Debug builds additionally contain `SelectionProbeActivity`: plain platform TextView/EditText with View IDs, no custom ActionMode or added menu actions. It provides reproducible selection/result/clipboard checks without opening personal documents; it is absent from release builds.

## Engine / providers

Core contracts (`Operation`, request, result, engine and provider) contain no Android or Compose types. TRANSLATE and EXPLAIN are explicit operations. Adding DICTIONARY later extends the operation enum, provider capabilities and prompt/UI mapping at these seams.

- Translate: configured AI → Google web (one pass only).
- Explain: configured AI only; prompt uses original language, simple English for English input.
- Input above `TranslationRequest.MAX_TEXT_LENGTH` (12,000 UTF-16 characters) is rejected before provider execution; the clipboard adapter uses the same limit before crossing another Intent boundary. Blank provider output is a failure. Cancellation is rethrown, not treated as fallback failure.
- `OpenAiCompatibleProvider` uses settings/secret interfaces, builds Chat Completions JSON, validates the endpoint and parses only string content. Empty, malformed, null and server-truncated output is rejected. Groq/Qwen extensions are sent only for Groq-hosted Qwen models.
- `GoogleWebTranslateProvider` isolates the unofficial endpoint and segment parsing. Its failures cannot crash the UI.
- Shared `Call.awaitPayload`: OkHttp worker reads/closes the response, rejects bodies above 1 MiB, maps network/timeouts to safe errors, cancels the socket with the coroutine. Server error bodies and parser exceptions are never echoed.
- Connect/read/write/call timeouts: 8/25/15/30 seconds. Automatic connection retries and redirects disabled. A failed AI call may be followed by one fallback call; there is no retry loop.

## Settings / secrets

`MainActivity` → `SettingsViewModel` → `SettingsStore` / `ApiKeyStore`, implemented by `SettingsRepository` / `SecretStore`.

- Defaults live once in `AppSettings`; settings are normalized/validated before saving and base URL is revalidated before sending credentials.
- Non-secret settings use Preferences DataStore; IO read failures give defaults.
- Key encryption uses Android Keystore AES-256-GCM with randomized encryption, fresh cipher-generated IV, 128-bit authentication tag. Ciphertext/IV alone are stored in `translator_secrets.xml`.
- Missing Keystore key after restore, invalid envelope or failed authentication clears unusable ciphertext and requires re-entry; decryption does not generate a replacement key.
- SharedPreferences commits and Keystore work run off the main thread. Save errors become safe UI errors. Fields are disabled while loading/saving to prevent lost edits/concurrent writes.
- Both backup rule formats exclude `translator_secrets.xml` from cloud and device transfer. Ordinary settings may be restored.
- Key is masked in UI, omitted from state `toString`, not saved to SavedStateHandle, not logged. Settings window uses FLAG_SECURE.

## Future core

A future `UniffiTranslatorEngine` may implement the same `TranslatorEngine` boundary. Prompting/routing and providers can move behind it after parity tests exist. Android intent, clipboard, window and settings adapters remain outside the shared core. No history store is implemented yet; one can later decorate or sit behind the engine without introducing HTTP into UI.
