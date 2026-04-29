# Forge Bridge

A local-first Android app that runs a unified, OpenAI-compatible HTTP API on
`localhost:8745`. Any app on the device — or anything reachable on `adb
forward` — can talk to multiple AI providers (OpenAI, Anthropic, Gemini,
ChatGPT proxy, Claude proxy) through one consistent interface.

Forge Bridge also exposes the same surface to other apps via an AIDL service,
so on-device clients can call it without going through HTTP.

## Features

- **One API, many providers.** All requests use the OpenAI-compatible chat
  completions shape. The bridge translates to and from each upstream.
- **API tier:** OpenAI, Anthropic, Gemini.
- **Proxy tier:** ChatGPT (chat.openai.com session) and Claude
  (claude.ai session) signed in through an embedded WebView.
- **Streaming** over Server-Sent Events for every provider.
- **Encrypted vault** — all API keys, OAuth tokens, and session cookies are
  stored in `EncryptedSharedPreferences` backed by Android Keystore.
- **Foreground service** keeps the bridge running while the app is backgrounded.
- **AIDL service** (`com.forge.bridge.IForgeBridge`) for cross-app callers.
- **File upload** endpoint at `POST /files/upload` with SHA-256 hashing.
- **Automatic retry with backoff + jitter** on network and 5xx errors.
- **Token refresh scheduler** that lets proxy adapters refresh access tokens
  in the background.
- **Conversation state isolation** — proxy adapters guard conversation
  pointers under a coroutine `Mutex` so concurrent calls don't race.

## HTTP API

Base URL: `http://localhost:8745`

| Method | Path                       | Description                                  |
| ------ | -------------------------- | -------------------------------------------- |
| GET    | `/health`                  | Liveness probe.                              |
| GET    | `/providers`               | List all configured providers + auth state.  |
| GET    | `/providers/available`     | Only providers reporting authenticated.      |
| GET    | `/providers/{id}/models`   | Model list for a specific provider.          |
| POST   | `/v1/chat/completions`     | Chat completion (OpenAI-compatible). Set     |
|        |                            | `"stream": true` for SSE.                    |
| POST   | `/files/upload`            | Multipart upload; returns id + sha256.       |
| GET    | `/files`                   | List uploaded files.                         |

### Chat example

```bash
curl http://localhost:8745/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": false
  }'
```

The bridge picks the provider for a model in this order:

1. Explicit `"provider": "<id>"` field.
2. First adapter that lists `model` in its `availableModels`.
3. First authenticated adapter.

## AIDL surface

```aidl
interface IForgeBridge {
    List<ProviderInfo> listProviders();
    ChatResponse chat(in ChatRequest request);
    void streamChat(in ChatRequest request, IChatStreamCallback callback);
    String healthCheck();
}
```

Bind to component `com.forge.bridge/.service.ForgeInterfaceService`
(action `com.forge.bridge.IForgeBridge`).

## Build

Requires JDK 17. The repo ships a Gradle wrapper.

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

GitHub Actions builds the debug APK automatically. The workflow caps at 20
minutes and uploads the APK as an artifact named `forge-bridge-debug`.

## Project layout

```
app/src/main/
├── java/com/forge/bridge/
│   ├── ForgeBridgeApp.kt            # Application + notification channel
│   ├── data/
│   │   ├── local/                   # Room + EncryptedSharedPreferences vault
│   │   └── remote/
│   │       ├── api/                 # Ktor server + routes + retry/refresh
│   │       └── providers/           # One adapter per upstream
│   ├── di/AppModule.kt              # Hilt providers
│   ├── forge/                       # AIDL Parcelable types
│   ├── service/                     # Foreground + AIDL services
│   └── ui/                          # Compose screens + WebView auth
├── aidl/com/forge/bridge/           # IForgeBridge + parcelable shims
└── res/                             # Themes, icons, strings
```

## Adding a new provider

1. Create `data/remote/providers/<name>/` with a `*Models.kt` and `*Adapter.kt`.
2. Implement `ProviderAdapter` (id, name, tier, models, features,
   `isAuthenticated`, `chat`, `streamChat`).
3. Inject the adapter into `ProviderRegistry`.
4. Optionally override `refreshIfNeeded` for proxy/OAuth flows.

The unified models live in `data/remote/api/UnifiedModels.kt` — your adapter
translates to and from those types and you're done. No route changes needed.

## License

MIT.
