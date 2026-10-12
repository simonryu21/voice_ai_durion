# Durion — Voice AI

English | [한국어](README_ko.md)

Durion is an AI assistant that supports voice conversations, remembers user context, and connects mobile capabilities with app actions. This repository brings together five projects: the voice server, memory layer, web client, UI components, and Android app.

## Introducing Durion

- [Architecture and features](01-structure-features.html)
- [Use cases](02-use-cases.html)

Download these HTML files and open them in a browser to view the introduction pages, which are written in Korean. GitHub's file viewer displays their source code. The two pages link to each other; the originals are also available in `mobile_pipecat/docs/durion-promo/`.

## Projects

| Project | Description | Key locations |
| --- | --- | --- |
| [mobile_pipecat](mobile_pipecat/) | Real-time voice server built on Pipecat, including an STT → LLM → TTS pipeline, WebRTC/RTVI, OAuth, memory retrieval, location and search tools, and app integration | `mobile/voice_ai.py`, `mobile/tools/`, `mobile/auth/`, `src/pipecat/` |
| [mobile_mem0](mobile_mem0/) | Long-term memory storage and retrieval based on Mem0, with Python/TypeScript SDKs, a REST server, and OpenMemory code | `mem0/`, `server/`, `mem0-ts/`, `openmemory/` |
| [mobile_pipecat_frontend](mobile_pipecat_frontend/) | React/Vite web client and a Python package for serving the static UI through FastAPI | `client/`, `pipecat_ai_prebuilt/`, `test/` |
| [mobile_pipecat_ui_module](mobile_pipecat_ui_module/) | React voice UI components, hooks, templates, Ladle component previews, and example apps | `package/`, `docs/`, `examples/` |
| [mobile_pipecat_android](mobile_pipecat_android/) | Kotlin Android client with WebView, native plugins, mobile integrations, and a Go JWT test server | `app/`, `test-server/` |

The web and Android clients connect to the voice server, which calls AI providers and the Mem0 REST server. Default development ports are `7860` for the voice server, `5174` for the web UI, and `8888` for Mem0. The separate Go login test server uses port `8080`.

## Prerequisites

- Git, Python 3.11 or 3.12, and `uv`
- Node.js 22 LTS, npm, and pnpm (for UI module development)
- Docker and Docker Compose (for the Mem0 server and databases)
- Android Studio, JDK 17, Android SDK 34, and NDK `25.1.8937393` (for Android development)
- API keys for your chosen speech recognition, LLM, and speech synthesis providers

```bash
git clone https://github.com/simonryu21/voice_ai_durion.git
cd voice_ai_durion
```

Unless stated otherwise, each setup section starts at the repository root. Each project manages its own environment; there is no single installation command at the root.

## 1. Memory server: mobile_mem0

```bash
cd mobile_mem0/server
cp .env.example .env
# Set OPENAI_API_KEY in .env.
docker compose up -d --build
```

Compose starts the Mem0 API, PostgreSQL/pgvector, and Neo4j. API documentation is available at `http://localhost:8888/docs`. Database connection addresses are configured inside Compose. The example database passwords are intended for local development and must be changed for external deployment.

To develop only the Python SDK:

```bash
cd mobile_mem0
uv venv --python 3.11
uv pip install -e .
```

See the [REST server guide](mobile_mem0/server/README.md) and [OpenMemory guide](mobile_mem0/openmemory/README.md).

## 2. Web client: mobile_pipecat_frontend

```bash
cd mobile_pipecat_frontend/client
npm ci
npm run build
npm run dev
```

The development UI runs at `https://localhost:5174`. The current Vite configuration proxies `/api` to `https://localhost:7860`, so run the voice server below with HTTPS enabled. For access from a phone, use your computer's LAN IP address, allow microphone access, and trust the development certificate. When using OAuth, register redirect URIs that match the actual addresses you use.

The voice server can also serve the `client/dist/` build at `/client/`. To bundle the UI as a Python package, use `mobile_pipecat_frontend/scripts/local_build.sh`. See [test/README.md](mobile_pipecat_frontend/test/README.md) for the separate sample server setup.

## 3. Voice server: mobile_pipecat

```bash
cd mobile_pipecat
uv sync --python 3.11 --extra mobile --group mobile
cp mobile/env.example mobile/.env
```

Configure your credentials and connection details in `mobile/.env`.

| Setting | Purpose |
| --- | --- |
| `OPENAI_API_KEY`, `OPENAI_MODEL` | LLM and OpenAI TTS |
| `STT_PROVIDER=soniox`, `SONIOX_API_KEY` | Soniox speech recognition |
| `STT_PROVIDER=google`, `GOOGLE_APPLICATION_CREDENTIALS` | Service account file path when using Google STT |
| `TTS_PROVIDER=openai` | OpenAI speech synthesis; configure the corresponding keys for other providers |
| `MEM0_HOST=http://localhost:8888` | Local memory server |
| `DATABASE_URL`, `JWT_SECRET_KEY`, OAuth provider settings | Authentication; see the [OAuth guide](mobile_pipecat/mobile/docs/README_OAUTH.md) |
| `TAVILY_API_KEY`, map and weather keys | Optional features; see `mobile/env.example` |

The example file defaults to Google for `STT_PROVIDER`. Change it explicitly to use Soniox. Replace placeholder key values with your own credentials.

Generate a development certificate to match the web UI's HTTPS proxy, then start the server:

```bash
cd mobile
openssl req -x509 -newkey rsa:4096 -nodes \
  -keyout key.pem -out cert.pem -days 365 -subj '/CN=localhost'
./start_voice_ai.sh
```

The server defaults to `https://localhost:7860`. If you built the web client in the previous step, it is also available at `https://localhost:7860/client/`. Set `FRONTEND_DIST_PATH` to use a different build directory. Without certificates, the startup script uses HTTP; adjust the web development proxy accordingly.

Local dependency paths point to the modified Pipecat and Mem0 projects in this repository. After path changes, `uv sync` may update lock files. The existing voice server Dockerfile still requires validation against this combined directory layout, so use the local setup above as the reference workflow.

## 4. UI components: mobile_pipecat_ui_module

```bash
cd mobile_pipecat_ui_module
pnpm install
pnpm build
pnpm dev
```

`pnpm dev` starts the UI package's Ladle component development environment. To run an example:

```bash
cd examples/01-console
pnpm dev
```

Set the voice server's WebRTC URL in the example configuration. This project develops reusable components. The web client uses the UI Kit version published on npm by default; applying local component changes to the client requires a separate local package link.

## 5. Android app: mobile_pipecat_android

1. Open `mobile_pipecat_android/` in Android Studio.
2. Copy `local.properties.example` to `local.properties` and configure `sdk.dir`, plus `GEMINI_API_KEY` and `tMapAppKey` for the features you use.
3. Obtain `google-services.json` from your own Firebase project for application ID `com.lgcns.codeengine` and place it in `app/`.
4. Change `BASE_URL` in `app/build.gradle` to the web UI address. Use your computer's LAN IP for a physical device or `10.0.2.2` to access the computer from an Android emulator.
5. Sync Gradle, then build and run the debug app. The SDK/NDK and external library dependencies are required.

Private signing files are excluded. Debug builds use Android's default development signing configuration. For custom release signing, create a local keystore and set `KEYSTORE_PASSWORD` and `KEY_PASSWORD`. Update the release signing path and alias to match your own configuration.

To run the separate JWT test server:

```bash
cd mobile_pipecat_android/test-server
cp .env.example .env
# Set your MONGO_ATLAS_URI and a random JWT_SECRET_KEY.
go run .
```

## Public repository maintenance

API keys, `.env` files, service account JSON, Firebase configuration, private keys, keystores, local databases, and build outputs are excluded from uploads. The original five repositories' Git histories are preserved locally in `.repo-history/`; the combined repository records a source snapshot.

Keep the LICENSE files and copyright notices included in each project. Check the individual directories for the licenses of Pipecat, Mem0, Voice UI Kit, and other upstream projects and external libraries.

This guide reflects the current source and configuration files. Full execution with real API credentials, databases, and Android/Firebase environments still requires separate validation.
