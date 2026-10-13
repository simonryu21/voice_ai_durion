# DuriOn — Voice AI

[English](README.md) | 한국어

DuriOn은 음성으로 대화하고, 사용자의 맥락을 기억하며, 모바일 기능과 앱 실행을 연결하는 AI 어시스턴트입니다. 이 저장소는 서버, 메모리, 웹 UI, UI 컴포넌트, Android 앱 등 5개 프로젝트를 함께 관리합니다.

## DuriOn 소개

- [구조와 주요 기능](https://htmlpreview.github.io/?https://github.com/simonryu21/voice_ai_durion/blob/main/01-structure-features.html)
- [사용 시나리오](https://htmlpreview.github.io/?https://github.com/simonryu21/voice_ai_durion/blob/main/02-use-cases.html)

위 링크는 HTML Preview를 통해 한국어 소개 화면을 바로 엽니다. 다운로드할 필요가 없습니다. 각 페이지 상단에서 영어 버전으로 전환할 수 있습니다. 소스는 [구조와 기능 HTML](01-structure-features.html), [사용 시나리오 HTML](02-use-cases.html)에서 확인할 수 있습니다.

## 프로젝트 구성

| 프로젝트 | 내용 | 주요 위치 |
| --- | --- | --- |
| [mobile_pipecat](mobile_pipecat/) | Pipecat 기반 실시간 음성 서버. STT → LLM → TTS 파이프라인, WebRTC/RTVI, OAuth, 기억 검색, 위치·검색 도구와 앱 연결 기능 | `mobile/voice_ai.py`, `mobile/tools/`, `mobile/auth/`, `src/pipecat/` |
| [mobile_mem0](mobile_mem0/) | Mem0 기반 장기 기억 저장·검색. Python/TypeScript SDK와 REST 서버, OpenMemory 관련 코드 | `mem0/`, `server/`, `mem0-ts/`, `openmemory/` |
| [mobile_pipecat_frontend](mobile_pipecat_frontend/) | React/Vite 웹 클라이언트와 FastAPI에서 정적 UI를 제공하는 Python 패키지 | `client/`, `pipecat_ai_prebuilt/`, `test/` |
| [mobile_pipecat_ui_module](mobile_pipecat_ui_module/) | React 음성 UI 컴포넌트·훅·템플릿, Ladle, 예제 앱 | `package/`, `docs/`, `examples/` |
| [mobile_pipecat_android](mobile_pipecat_android/) | Kotlin Android 클라이언트. WebView와 네이티브 플러그인, 모바일 기능 연동 및 Go JWT 테스트 서버 | `app/`, `test-server/` |

웹/Android 클라이언트가 음성 서버에 연결되고, 음성 서버가 AI 제공 업체와 Mem0 REST 서버를 호출합니다. 기본 개발 포트는 음성 서버 `7860`, 웹 UI `5174`, Mem0 `8888`입니다. Go 테스트 서버는 별도 로그인 테스트용이며 `8080`을 사용합니다.

## 공통 준비

- Git, Python 3.11 또는 3.12, `uv`
- Node.js 22 LTS, npm, pnpm (UI 모듈 개발용)
- Docker와 Docker Compose (Mem0 서버와 DB 실행용)
- Android Studio, JDK 17, Android SDK 34 / NDK `25.1.8937393` (Android 개발용)
- 선택한 음성 인식·LLM·음성 합성 제공 업체의 API 키

```bash
git clone https://github.com/simonryu21/voice_ai_durion.git
cd voice_ai_durion
```

아래 명령은 별도 안내가 없으면 저장소 최상위에서 시작합니다. 프로젝트별 환경은 각각 관리하며 최상위에는 통합 설치 명령이 없습니다.

## 1. 메모리 서버: mobile_mem0

```bash
cd mobile_mem0/server
cp .env.example .env
# .env의 OPENAI_API_KEY를 설정합니다.
docker compose up -d --build
```

Compose가 Mem0 API, PostgreSQL/pgvector, Neo4j를 실행합니다. API 문서는 `http://localhost:8888/docs`에서 확인할 수 있습니다. DB 연결 주소는 Compose 안에서 설정됩니다. 예제 DB 암호는 로컬 개발용이므로 외부 배포 시 변경해야 합니다.

Python SDK만 개발할 때는 다음처럼 설치합니다.

```bash
cd mobile_mem0
uv venv --python 3.11
uv pip install -e .
```

[REST 서버 안내](mobile_mem0/server/README.md), [OpenMemory 안내](mobile_mem0/openmemory/README.md)를 참고하세요.

## 2. 웹 클라이언트: mobile_pipecat_frontend

```bash
cd mobile_pipecat_frontend/client
npm ci
npm run build
npm run dev
```

개발 UI는 `https://localhost:5174`입니다. 현재 Vite 설정은 `/api`를 `https://localhost:7860`에 프록시하므로 아래 음성 서버도 HTTPS로 실행합니다. 휴대폰에서 접속할 때는 PC의 LAN IP를 사용하고 마이크 권한 및 개발 인증서를 허용해야 합니다. OAuth를 사용할 경우 실제 접속 주소에 맞춰 리디렉션 URI를 등록합니다.

빌드 결과 `client/dist/`는 음성 서버의 `/client/` 경로에서도 제공할 수 있습니다. Python 패키지로 UI를 묶으려면 `mobile_pipecat_frontend/scripts/local_build.sh`를 사용합니다. 별도 샘플 서버의 설정은 [test/README.md](mobile_pipecat_frontend/test/README.md)를 참고하세요.

## 3. 음성 서버: mobile_pipecat

```bash
cd mobile_pipecat
uv sync --python 3.11 --extra mobile --group mobile
cp mobile/env.example mobile/.env
```

`mobile/.env`에서 실제 사용할 키와 연결 정보를 설정합니다.

| 설정 | 용도 |
| --- | --- |
| `OPENAI_API_KEY`, `OPENAI_MODEL` | LLM 및 OpenAI TTS |
| `STT_PROVIDER=soniox`, `SONIOX_API_KEY` | Soniox 음성 인식 |
| `STT_PROVIDER=google`, `GOOGLE_APPLICATION_CREDENTIALS` | Google STT를 선택할 경우 서비스 계정 파일 경로 |
| `TTS_PROVIDER=openai` | OpenAI 음성 합성; 다른 제공 업체는 해당 키도 설정 |
| `MEM0_HOST=http://localhost:8888` | 로컬 메모리 서버 |
| `DATABASE_URL`, `JWT_SECRET_KEY`, OAuth 제공 업체 설정 | 로그인 기능; [OAuth 안내](mobile_pipecat/mobile/docs/README_OAUTH.md) 참고 |
| `TAVILY_API_KEY`, 지도·날씨 관련 키 | 선택 기능; `mobile/env.example` 참고 |

예제의 `STT_PROVIDER` 기본값은 Google입니다. Soniox를 사용하려면 명시적으로 바꿔 주세요. 예제 키 문자열은 실제 발급받은 값으로 교체합니다.

웹 UI의 HTTPS 프록시와 연결할 개발 인증서를 생성하고 서버를 실행합니다.

```bash
cd mobile
openssl req -x509 -newkey rsa:4096 -nodes \
  -keyout key.pem -out cert.pem -days 365 -subj '/CN=localhost'
./start_voice_ai.sh
```

서버는 기본 `https://localhost:7860`에서 실행됩니다. 앞 단계에서 웹 UI를 빌드했다면 `https://localhost:7860/client/`에서도 제공합니다. `FRONTEND_DIST_PATH`로 다른 빌드 경로를 지정할 수 있습니다. 인증서가 없으면 실행 스크립트는 HTTP로 실행하므로 웹 개발 프록시도 이에 맞춰 변경해야 합니다.

이 저장소의 로컬 Pipecat·Mem0 수정본을 사용하도록 의존성 경로를 연결했습니다. 경로 변경 후 `uv sync`가 잠금 파일을 갱신할 수 있습니다. 기존 음성 서버 Dockerfile은 이 통합 폴더 배치에 맞춘 검증이 필요하므로 위 로컬 실행을 기준으로 사용하세요.

## 4. UI 컴포넌트: mobile_pipecat_ui_module

```bash
cd mobile_pipecat_ui_module
pnpm install
pnpm build
pnpm dev
```

`pnpm dev`는 UI 패키지의 Ladle 컴포넌트 개발 환경을 실행합니다. 예제 실행:

```bash
cd examples/01-console
pnpm dev
```

연결할 음성 서버의 WebRTC URL을 예제 설정에 지정합니다. 이 프로젝트는 재사용할 컴포넌트를 개발하는 곳이며 웹 클라이언트는 기본적으로 npm에 배포된 UI Kit 버전을 사용합니다. 로컬 컴포넌트 수정 사항을 클라이언트에 적용하려면 별도 로컬 패키지 연결이 필요합니다.

## 5. Android 앱: mobile_pipecat_android

1. Android Studio에서 `mobile_pipecat_android/`를 엽니다.
2. `local.properties.example`을 `local.properties`로 복사하고 `sdk.dir`, 사용할 `GEMINI_API_KEY`, `tMapAppKey`를 설정합니다.
3. 본인의 Firebase 프로젝트에서 해당 application ID(`com.lgcns.codeengine`)용 `google-services.json`을 받아 `app/`에 넣습니다.
4. `app/build.gradle`의 `BASE_URL`을 웹 UI 주소로 변경합니다. 실기기에서는 PC의 LAN IP, 에뮬레이터에서는 PC 접근용 `10.0.2.2`를 사용합니다.
5. Gradle 동기화 후 debug 앱을 빌드·실행합니다. SDK/NDK와 외부 라이브러리 의존성이 필요합니다.

개인 서명 파일은 포함하지 않습니다. debug 빌드는 Android 기본 개발 서명을 사용합니다. 커스텀 release 서명을 사용할 경우 키스토어를 로컬에서 생성하고 `KEYSTORE_PASSWORD`, `KEY_PASSWORD`를 설정합니다. release 서명 경로·별칭도 본인 설정에 맞춰 변경하세요.

별도 JWT 테스트 서버가 필요하면:

```bash
cd mobile_pipecat_android/test-server
cp .env.example .env
# 본인의 MONGO_ATLAS_URI와 임의의 JWT_SECRET_KEY 설정
go run .
```

## 공개 저장소 관리

API 키, `.env`, 서비스 계정 JSON, Firebase 설정, 개인키·키스토어, 로컬 DB와 빌드 산출물은 업로드 대상에서 제외합니다. 기존 5개 저장소의 Git 이력은 로컬 `.repo-history/`에 보관하고 통합 저장소에는 현재 소스 스냅샷을 기록합니다.

각 프로젝트에 포함된 LICENSE 및 저작권 고지를 유지합니다. Pipecat, Mem0, Voice UI Kit 등 원본 프로젝트와 외부 라이브러리의 라이선스는 각 폴더의 파일을 확인하세요.

이 안내는 현재 소스와 설정 파일을 기준으로 작성했습니다. 실제 API 키·DB·Android/Firebase 환경을 연결하는 전체 실행 검증은 별도로 필요합니다.

