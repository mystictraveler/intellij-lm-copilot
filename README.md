# intellij-lm-copilot

A bridge plugin that registers GitHub Copilot as a provider for the IntelliJ LM API. Any plugin that consumes models through `LmService` can transparently use Copilot-backed LLMs without taking a direct dependency on the Copilot plugin.

## How it works

1. **OAuth token retrieval** -- `CopilotHttpClient` calls `AuthHelper.getAccounts()` from the Copilot plugin to get the user's GitHub OAuth token. The user must already be signed in to Copilot.
2. **API token exchange** -- The OAuth token is exchanged for a short-lived Copilot API token via `https://api.github.com/copilot_internal/v2/token`. Tokens are cached in memory and refreshed automatically when they approach expiry (5-minute buffer).
3. **Chat completions** -- Requests are sent to `https://api.githubcopilot.com/chat/completions` using the OpenAI-compatible format. All HTTP connections go through `HttpConfigurable.getInstance().openHttpConnection()`, so IntelliJ's configured proxy and SSL settings are respected.

## Available models

| Model ID                       | Display name      | Family        | Max input tokens |
|--------------------------------|-------------------|---------------|------------------|
| `gpt-4o`                       | GPT-4o            | gpt-4o        | 128 000          |
| `gpt-4o-mini`                  | GPT-4o Mini       | gpt-4o-mini   | 128 000          |
| `o3-mini`                      | o3-mini           | o3-mini       | 200 000          |
| `claude-sonnet-4-5-20250514`   | Claude Sonnet 4.5 | claude        | 200 000          |

## Architecture

```
LmService  --->  CopilotLmProvider  --->  CopilotChatModel  --->  CopilotHttpClient
(platform)       (registers models)       (sends requests)        (auth + HTTP)
```

- **`CopilotLmProvider`** -- Implements `LmProvider`. Returns the list of available `CopilotChatModel` instances from `getAvailableModels()`. Registered via the `com.intellij.lm.provider` extension point in `plugin.xml`.
- **`CopilotChatModel`** -- Implements `LmChatModel`. Delegates each `sendRequest` call to `CopilotHttpClient` and wraps the result in a `StreamingLmChatResponse` flow.
- **`CopilotHttpClient`** -- Singleton that manages OAuth-to-API token exchange, token caching, and the HTTP calls to the Copilot chat completions endpoint. Uses `HttpConfigurable` for proxy support and Gson for JSON serialization.

## Dependencies

| Dependency            | Purpose                                                              |
|-----------------------|----------------------------------------------------------------------|
| `com.intellij.lm`    | The IntelliJ LM API plugin that defines `LmProvider`, `LmChatModel`, etc. Built from the sibling `intellij-lm-api` project and loaded as a local plugin. |
| `com.github.copilot`  | The GitHub Copilot plugin (>= 1.7.1). Provides `AuthHelper` and `GitHubAccountCredentials` for authentication. |
| `com.intellij.modules.platform` | Standard IntelliJ platform APIs (`HttpConfigurable`, logging, etc.). |

## Building

Prerequisites: JDK 17+, and a built copy of [intellij-lm-api](../intellij-lm-api) at `../intellij-lm-api/build/distributions/intellij-lm-api-0.0.1.zip`.

```bash
# Build the sibling LM API plugin first
cd ../intellij-lm-api && ./gradlew buildPlugin && cd -

# Build this plugin
./gradlew buildPlugin
```

The distributable zip is written to `build/distributions/`.

To run a sandboxed IDE with the plugin loaded:

```bash
./gradlew runIde
```

## How consumers use it

Consumers never depend on this plugin directly. They depend only on the LM API (`com.intellij.lm`) and discover models at runtime through `LmService`:

```kotlin
val lmService = LmService.getInstance()
val models = lmService.getAvailableModels()  // includes Copilot models when this plugin is installed
val gpt4o = models.first { it.id == "gpt-4o" }

val response = gpt4o.sendRequest(
    messages = listOf(LmChatMessage(LmChatRole.USER, "Explain coroutines")),
    options = LmChatRequestOptions()
)
```

As long as the user has this bridge plugin and the Copilot plugin installed and is signed in, the Copilot models appear alongside any other LM providers automatically.

## Compatibility

- IntelliJ Platform: 2024.3 -- 2025.3.*
- JVM target: 17
- Kotlin: 2.1.0
- Gradle: 8.10 (wrapper included)
