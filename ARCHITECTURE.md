# Architecture — MVP boundary that survives the next iterations

## Product flow

`ACTION_PROCESS_TEXT` or `ACTION_SEND` -> `Invocation` -> `ProcessTextViewModel` -> `TranslatorEngine` -> ordered `TranslatorProvider`s -> popup state.

The Android UI never calls Groq or Google directly. All provider-specific code is behind `TranslatorProvider`. The intent adapters do not know about networking. This lets Codex replace the Kotlin engine with a Rust/UniFFI implementation later without touching the popup contract.

## Current provider policy

1. Translate: `Qwen/Groq` first; if the API key/config/network call fails, try the isolated unofficial Google web fallback.
2. Explain: `Qwen/Groq` only.
3. Provider failures are accumulated by `DefaultTranslatorEngine`; the popup receives one user-readable error.

The Google endpoint is deliberately isolated in one class because it is not an official stable API. Removing it later must not affect UI.

## State and secrets

- Non-secret settings: Preferences DataStore.
- Groq API key: AES/GCM; AES key lives in Android Keystore; ciphertext lives in a SharedPreferences file excluded from Android cloud/device backup.
- No API key is hard-coded in source.

## Process text semantics

- `PROCESS_TEXT`: incoming `EXTRA_PROCESS_TEXT`; if `EXTRA_PROCESS_TEXT_READONLY=false`, the popup exposes Replace and returns `EXTRA_PROCESS_TEXT` via `RESULT_OK`.
- `SEND text/plain`: same popup and engine, always read-only.
- No Accessibility Service and no `SYSTEM_ALERT_WINDOW` permission.

## Planned Rust seam

The stable seam is the `TranslatorEngine` contract. Do not let future Rust/UniFFI work leak Android types into core. A future adapter should look conceptually like:

```kotlin
class UniffiTranslatorEngine(...) : TranslatorEngine {
    override suspend fun execute(request: TranslationRequest): TranslationResult = ...
}
```

Move prompt construction, routing, provider policy, history, and dictionaries behind that implementation only after parity tests exist. Do not rewrite Compose screens for the migration.

## Deliberately not in this MVP

- history database and sync;
- offline dictionaries/WordNet/FreeDict;
- multi-provider editable ordering;
- language auto-detection UI;
- Rust/UniFFI binary build pipeline;
- streaming tokens;
- Android widgets/quick tiles.

These are additive behind the existing seams rather than prerequisites for the primary UX.
