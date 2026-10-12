# Pipecat 챗봇 에이전트 / UI 재구성 계획서

> 작성일: 2026-05-22
> 범위: 실제 운영 챗봇(웹 클라이언트 + Pipecat 봇 + Android WebView 앱)
> 별개 문서: `pipecat-voice-ui-migration.md` 는 voice-ui-kit 컴포넌트 + Android 통역 화면 마이그레이션을 다룸. 이 문서는 그와 별개로 **에이전트 구조 재구성**을 다룬다.

---

## 0. 이 문서의 목적

대화 중 드러난 요구사항을 한 곳에 정리하고, **코드를 건드리기 전에** 작업 범위·순서·미해결 질문을 합의하기 위한 계획서. 승인 후 Phase 단위로 구현한다.

---

## 1. 현재 구조 (코드로 확인한 사실)

| 구성요소 | 위치 | 역할 |
| --- | --- | --- |
| **Pipecat 봇** | [pipecat/mobile/voice_ai.py](../mobile/voice_ai.py) | 메인 음성 AI. `:7860`에서 서빙. RTVI `on_client_message`로 에이전트 ON/OFF 처리 |
| **번역 모드** | [pipecat/mobile/tools/translation_mode.py](../mobile/tools/translation_mode.py) | `TRANSLATION_LLM_PROVIDER` env로 web/native 결정 |
| **웹 클라이언트** | [voice-ui-kit/small-webrtc-prebuilt/client/src/index.tsx](../../voice-ui-kit/small-webrtc-prebuilt/client/src/index.tsx) | `ConsoleTemplate` 렌더. 봇이 `:7860/client`로 서빙, Android WebView가 로드 |
| **콘솔 UI** | [voice-ui-kit/package/src/templates/Console/index.tsx](../../voice-ui-kit/package/src/templates/Console/index.tsx) | 에이전트 버튼·번역 모드 토글 로직 |
| **에이전트 버튼** | [voice-ui-kit/package/src/components/panels/ConversationPanel.tsx#L134-L149](../../voice-ui-kit/package/src/components/panels/ConversationPanel.tsx#L134-L149) | "회의 Agent" / "번역 Agent" 버튼 |
| **Android 네이티브 통역** | `activity/GeminiLiveTranslationActivity.kt` | Gemini Live 네이티브 오디오 동시통역 화면 |
| **Android 브리지** | `web/plugin/LiveTranslationPlugin.kt` | JS bridge `LiveTranslation/startTranslation` → 네이티브 화면 실행 |

### 1.1 현재 번역 Agent 흐름

```
[번역 Agent 클릭]
  → Console toggleTranslationMode()  (Console/index.tsx:451)
  → client.sendClientMessage("start_translation_mode")
  → 봇 voice_ai.py on_client_message  (voice_ai.py:1323)
  → translation_mode.py create_start_translation_mode()
  → env TRANSLATION_LLM_PROVIDER 로 분기:
       · "openai" / "gemini_google"        → 봇이 웹 번역 파이프라인 생성
       · "gemini_google_native"            → 봇은 None 반환, 앱이 처리
  → 봇이 "translation_mode_started" { llm_provider } 응답
  → Console: llm_provider == "gemini_google_native" 일 때만
       callLiveTranslationPlugin("startTranslation")  (Console/index.tsx:1060)
  → Android LiveTranslationPlugin → GeminiLiveTranslationActivity
```

**핵심 제약**: web/native 선택이 **전역 env var 단일 스위치**다. 그래서 "번역 Agent(web)"와 "동시통역 Agent(native)"를 **동시에 둘 수 없다**.

---

## 2. 목표

1. **"번역 Agent" → 웹 번역** 고정 (봇 내 번역 파이프라인)
2. **"동시통역 Agent"(신규) → 네이티브 번역** (`GeminiLiveTranslationActivity`)
3. Gemini Live를 **따로 새로 구현하지 않음** — 기존 네이티브 화면이 곧 동시통역 Agent
4. 첫 화면의 카드(VOICE/CODE/DATA)를 **회의 / 번역 / 통시통역** 으로 바꿔 각 에이전트로 이동. **PLAN 카드는 그대로** 둠
5. 텍스트 입력 "+" 버튼 → **AppBar 형태**로 변경

---

## 3. 핵심 설계 결정

### 3.1 web/native를 요청 단위(per-request)로 전환

전역 env var 대신 **클라이언트가 `start_translation_mode` 요청에 mode를 실어 보낸다**. 봇은 그 값을 env var보다 우선 적용한다. env var는 기본값(fallback)으로만 남긴다.

```
start_translation_mode { provider: "gemini_google" }       ← 번역 Agent
start_translation_mode { provider: "gemini_google_native" } ← 동시통역 Agent
```

이 방식이면 봇 코드 변경이 최소(인자 전달 경로만)이고, 기존 `llm_provider` 응답·네이티브 dispatch 로직을 그대로 재사용한다.

---

## 4. 작업 분해

### Phase 1 — 에이전트 web/native 분리 ✅ (구현·검증 완료, 2026-05-22)

**봇** (`pipecat/mobile/`)
- [x] `voice_ai.py` `on_client_message`: `start_translation_mode` 처리에서 `msg_data`를 `MockFunctionCallParams(arguments=...)`로 전달 (기존 `arguments = {}`)
- [x] `translation_mode.py` `start_translation_mode`: `args`의 `provider`/`llm_provider`가 있으면 env보다 우선 적용. `_create_translation_service`에 `llm_provider` 인자 추가해 thread
- [x] provider 미지정 시 기존 env 기본값(`TRANSLATION_LLM_PROVIDER`) 유지

**웹 클라이언트** (`voice-ui-kit/package/`)
- [x] `ConversationPanel.tsx`: "동시통역 Agent" 버튼 + `isSimultaneousModeActive`/`onToggleSimultaneousMode` prop 추가
- [x] `Console/index.tsx`: `toggleTranslationMode(provider)` 인자화 + `translationProvider` state. "번역 Agent" → `gemini_google`, "동시통역 Agent" → `gemini_google_native`. 3개 버튼 위치(BotArea 직접 + ConversationPanel ×2) 모두 분기
- [x] native dispatch(`callLiveTranslationPlugin`)는 `gemini_google_native` 응답일 때만 발동 (현행 로직 그대로 — 동시통역 경로에서만)

**Android** (`mobile_pipecat_android`)
- [x] 변경 없음 (네이티브 화면·플러그인 그대로).

**검증 완료**: 봇 `py_compile` + `ruff check` 통과. 웹 `eslint` 0 errors · `pnpm build` 성공 · `pnpm test` 28 passed.

**실기기 디버깅 (2026-05-22)**: 동시통역 Agent 클릭 시 버튼 라벨만 바뀌고 동작 없음 → 봇 로그에서 원인 확인:
`'RTVIServerMessageFrame' object has no attribute 'model_dump'`. pipecat upstream 머지로 RTVI API가 바뀌어
`rtvi.push_transport_message(RTVIServerMessageFrame(...))`가 깨진 상태였음(`push_transport_message`는 이제
`BaseModel`을 요구). → `translation_mode.py`의 두 알림 호출을 `rtvi.send_server_message({...})`로 교체
(start/end 양쪽). 미사용 `RTVIServerMessageFrame` import 제거. 봇 재시작 후 재검증 필요.
(`meeting_mode.py`에도 같은 깨진 패턴이 남아 있음 — 별도 후속.)

**실기기 디버깅 2 (2026-05-22)**: send_server_message 수정 후 동시통역 Agent → 네이티브
`GeminiLiveTranslationActivity` 정상 진입 확인 ✅. 단 네이티브 화면이 "탭하여 시작"
(`CaptionLine` Idle 텍스트)을 보여주는데 그 텍스트가 클릭 불가라 사용자가 탭해도
세션이 시작 안 됨 → `GeminiLiveTranslationApp`의 중앙 영역(viz+caption Box)에
`clickable`(무-ripple) 추가해 탭 시 세션 토글(start/stop). AskBar Mic 버튼과 동일
`toggleSession` 람다 공유. `./gradlew :app:installDebug` BUILD SUCCESSFUL.

**실기기 디버깅 3 (2026-05-22)**: 네이티브 통역에서 말해도 반응 없음. 봇 로그 +
logcat 분석 결과 — (1) WebView의 WebRTC 세션이 OS 마이크를 계속 점유해 네이티브
`AudioRecord`가 캡처 불가, (2) `AudioRecorderManagerImpl`이 마이크 점유 시 graceful
실패 대신 `AudioRecord.stop()` `IllegalStateException`으로 앱 FATAL 크래시.
→ 수정 2건:
- `Console/index.tsx`: `translation_mode_started`의 `gemini_google_native` 분기에서
  `callLiveTranslationPlugin` 직후 `client.disconnect()` — 웹이 마이크를 놓아 네이티브가
  단독 점유. `toggleTranslationMode` 종료 경로는 end 메시지 실패해도 fast-reconnect가
  진행되도록 try/catch 분리(네이티브 종료 후 웹 챗봇 자동 복귀).
- `AudioRecorderManager.kt`: `startRecording`/`stopRecording`을 크래시 방지로 보강
  (마이크 점유 시 예외 대신 빈 flow 종료 + `stop()` try/catch).
`./gradlew :app:installDebug` BUILD SUCCESSFUL. 웹은 build:watch 반영.

**Phase 1 커밋 (2026-05-22)** — 실기기에서 동시통역 음성 번역 동작 확인 후 커밋:
`pipecat:7199d7dc0` · `vui:e84e1f2` · `and:92452a0`. (남은 엣지케이스 — 통역
화면 종료 후 재진입, stop→restart 등 — 은 후속 과제.)

> ⚠️ 미커밋 + 미배포: 웹 클라이언트(`small-webrtc-prebuilt/client`)는 `@pipecat-ai/voice-ui-kit`를 의존하므로, 실기기 동작 확인 전 voice-ui-kit 재빌드 → 클라이언트 재빌드 → 봇 재배포가 필요.

### Phase 2 — 첫 화면 카드 → 에이전트 런처 ✅ (구현 완료, 검증 대기, 2026-05-22)

요구: 마이그레이션 "01. Idle" 화면의 4개 카드를 **회의 / 번역 / 동시통역 + PLAN**으로.
로그인 후 첫 화면(홈)으로 띄우되 자동 연결은 백그라운드 유지, 명시적 Connect 버튼 제거.

**구현** (`Console/index.tsx`):
- `showHome` state — 마운트 시 `true`. 로그인 후 첫 화면으로 `SuggestionGrid`(4 카드:
  회의/번역/통역/plan)를 `absolute inset-0 z-30` 오버레이로 표시.
- 카드 선택 → `setShowHome(false)` + 에이전트 활성화: 회의→`toggleMeetingMode`,
  번역→`toggleTranslationMode("gemini_google")`, 동시통역→`toggleTranslationMode("gemini_google_native")`.
  PLAN은 별도 에이전트 없이 일반 대화 진입(기능 미정 — 후속).
- `ConnectButton`은 `className="hidden"` div로 감싸 **마운트는 유지**(자동 연결 계속) +
  화면에서만 제거.
- `eslint` 0 errors · `pnpm build` 성공. 실기기 검증 대기.

미해결: 자동 연결 완료 전 카드 선택 시 타이밍(에이전트 토글이 미연결 client에
메시지 전송) — §Phase 1 엣지케이스와 함께 후속.

### Phase 3 — "03 Conversation" 레이아웃 리워크 ✅ (완료, 2026-05-22)

요구(사용자): 챗봇 대화 화면을 마이그레이션 "03 Conversation"처럼 —
상단 4카드 → 대화 → 하단 AskBar, 메시지 쌓이면 카드가 위로 스크롤.

**완료 (커밋됨)**
- ✅ 봇 시작 시 LLM 인사말 호출 제거 (`voice_ai.py` — `pipecat:f28cb40ec`).
  welcome message가 UI에서 빠졌으므로 startup `LLMRunFrame` 불필요.
- ✅ Video 버튼 → 화면 최상단 우측 고정 (`fixed top-2 right-2 z-[60]` — `vui:07b8b38`).
- ✅ PLAN 카드 → 링크 제거. "오늘 할일 계획" 미설계 기능이라 클릭 무동작 (`vui:07b8b38`).
- ✅ **Task A** — 플로팅 "+" 버튼/텍스트 패널 제거. `geolocation.js`
  `initializeEnhancements`에서 `createControlPanel`/`createShowPanelButton`
  호출 제거(위치 전송 유지) — `small-webrtc-prebuilt` 서브모듈 `ba44059`.

**B·C — `ConsoleTemplate` 대화 레이아웃 리워크 ✅ (완료, 2026-05-22 — `vui:f3c2279`)**

- ✅ **Agent 버튼 3개 제거** — 회의/번역/동시통역 버튼을 `ConversationPanel.tsx`
  헤더와 `Console/index.tsx` 데스크톱 BotArea 양쪽에서 삭제. 에이전트 진입은
  홈 카드로 일원화. `ConversationPanel`에서 더 이상 안 쓰는 prop 8개
  (`activeAgent`/`onAgentChange`/`is*ModeActive`/`onToggle*Mode`)와 핸들러
  제거. 헤더 콘텐츠가 전부 데스크톱 전용이라 `PanelHeader`에 `max-sm:hidden`
  추가(모바일에서 빈 헤더 줄 제거).
- ✅ **B — AskBar 하단 고정**: `AskBar`를 Console 모바일 레이아웃 하단에
  `shrink-0`으로 고정. 텍스트 입력 → `usePipecatTextInput`(`sendTextMessage`),
  마이크 버튼 → `PipecatClientMicToggle`로 OS 마이크 토글. AskBar에 3개 prop
  추가: `micActive`(state와 무관하게 마이크 활성 표시 — 입력창은 계속 사용
  가능), `noAttach`/`noWave`(챗봇에 불필요한 +/Wave 버튼 숨김). 미연결 시
  `state="disabled"`.
- ✅ **C — 인-스크롤 홈 카드**: `Conversation`에 옵션 `headerSlot` prop 추가 —
  스크롤 컨테이너 최상단(메시지 위)에 렌더, 메시지가 쌓이면 같이 위로
  스크롤됨. Phase 2의 `showHome` `absolute` 오버레이 제거 → 4카드 + 인사말을
  `headerSlot`으로 대화 스크롤에 인라인. `conversationElementProps` 경유라
  데스크톱·모바일 `ConversationPanel` 양쪽에 자동 반영.
- 설계 메모: `headerSlot`은 옵션 prop이라 기존 `Conversation` 소비자에 영향
  없음(미지정 시 현행 동작 그대로). 메시지 렌더는 `messageList` 변수로 추출해
  중복 제거.

**검증 완료**: voice-ui-kit `eslint` 0 errors · `pnpm test` 28 passed ·
`pnpm build` 성공. `small-webrtc-prebuilt/client` `tsc` + `vite build` 성공.
실기기 동작 확인 후 `vui:f3c2279`로 커밋(main). (배포 시 voice-ui-kit
재빌드 → 클라이언트 재빌드 → 봇 재배포.)

미해결(후속): 자동 연결 완료 전 카드 선택 타이밍(§Phase 1·2 엣지케이스와 동일).
데스크톱 레이아웃에는 AskBar 미적용 — 개발용 콘솔이라 InfoPanel 마이크 컨트롤
유지. PLAN 카드 클릭은 여전히 무동작(기능 미설계).

### Phase 4 — (선택) 네이밍·디자인 정리

`Translation`/`Interpretation` 네이밍 리네임, 새 Pipecat Voice UI 디자인 적용 등. Phase 1~3 안정화 후 별도 합의.

---

## 5. 질문 답변 (2026-05-22 사용자 확인 완료)

- **Q1 (해결)** — 카드는 마이그레이션 UI **"01. Idle" 화면의 4개 카드**(CODE / PLAN / DATA / IDEA)를 가리킴. → Phase 2에 반영.
- **Q2 (해결)** — "+" 버튼은 `:5780/client` **우측 하단 파란 플로팅 버튼**(흰 "+", 터치 시 텍스트 입력창). "AppBar"는 마이그레이션 UI **"04. Overlay bar"** 를 의미. → Phase 3에 반영.
- **Q3 (해결)** — 리네임은 Phase 1과 분리, **Phase 4**에서 별도 진행.

---

## 6. 권장 진행 순서

1. ~~**Phase 1**~~ ✅ 완료 (2026-05-22).
2. **Phase 2·3** — 요구는 확정. "01. Idle"/"04. Overlay bar" 디자인을 웹 클라이언트에 도입하는 방식 설계 후 진행.
3. **Phase 4**(리네임)는 마지막.
