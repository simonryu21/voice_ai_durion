# Pipecat Voice UI 마이그레이션 가이드

> 대상: `voice-ui-kit` (React Front) / `mobile_pipecat_android` (Compose App)
> 레퍼런스: `docs/pipecat-gemini-ui/project/` 의 Claude Design 핸드오프
> (`Pipecat Voice UI.html` + `app.jsx` + `android-frame.jsx`)
> 작성일: 2026-05-19 (Gemini orb 톤 → Pipecat vermilion 톤 전면 개정)

---

## 0. 이력

- v1 (2026-05-19 초안): Google Gemini Live 톤(블루/퍼플/핑크 그라데이션 오브) 기준.
  - 1차 구현 완료: `voice-ui-kit`에 `GeminiOrb`/`GlassControlBar`/`Caption` 추가, Android에 `Gemini` 토큰·새 `GeminiOrb`·글래스 컨트롤 적용.
- **v2 (2026-05-19 개정)**: Claude Design 핸드오프 디자인이 도착함. 톤이 완전히 다름(따뜻한 다크 + deep vermilion + 막대 비주얼라이저 기본 + 5개 화면 패턴). v1 시각 토큰·컴포넌트 색은 모두 폐기, 컴포넌트 골격(Orb 비주얼라이저, Glass 컨테이너, Caption hook)은 일부 재활용.
- **v2 구현 (2026-05-19 현재)**: Phase 1~4 + 옵션 A(Android 5개 화면) + Phase 5 후속 작업(Console 톤 통일, Ladle Stories, 실측 latency, PipecatPreviewActivity, i18n, vite chunks, AskBar 입력 모드)까지 모두 완료. 자세한 진행 현황은 §9, 실행 방법은 §13, 남은 후보는 §14.

---

## 1. 레퍼런스 디자인 언어

`app.jsx` 주석에 명시: *"Original design — not a recreation of Gemini. Deep vermilion accent."* 즉 Gemini가 아니라 **Pipecat 자체 보이스 UI**다. 시각 특징을 8가지로 정리:

| 항목 | 설명 |
| --- | --- |
| **따뜻한 다크 베이스** | 다크 `#0E0B0A` (warm black) · `#1A1413` (surface) · 라이트 `#FAF6F2` (cream). 푸른 톤 전무 |
| **단일 vermilion 악센트** | `#C73E1D` 기본, 톤 변형 4개 (`#D94A2B`/`#E55934`/`#B33A1A`). 그라데이션 없음, 솔리드 |
| **3종 비주얼라이저** | `bars`(32개 막대, 기본) / `orb`(동심원 + 솔리드 코어) / `particles`(11×11 도트 그리드). 사용자가 선택 |
| **Geist 타이포** | `Geist` 400/500/600/700 · `Geist Mono` 400/500/600. tracking `-0.01em ~ -0.02em` |
| **AppBar + AskBar 패턴** | 상단 워드마크+상태 칩+이력 / 하단 입력 pill (Plus / placeholder / Mic / Wave) — 헤더·푸터가 양쪽에 모두 있는 클래식 모바일 챗 레이아웃 |
| **StatusPill** | 6px 도트 + label(`ready`/`listening`/`thinking`/`speaking`) + ` · 142ms` latency. 도트 색: idle은 녹색(`#4ADE80`), 그 외 vermilion |
| **5개 화면 패턴** | Idle / Listening / Conversation / Overlay bar / Bubble — 통역 한 화면이 아니라 어시스턴트 라이프사이클 전체 |
| **Pipecat 워드마크** | vermilion 사각형(`rx=5`) + 흰 막대 두 개(긴 거 + 짧은 거) + "pipecat" Geist 600 |

> 핵심 원칙: **"AppBar(워드마크 + StatusPill) — 화면별 콘텐츠 — AskBar"** 의 3단 구성을 일관되게 가져가고, 상태는 viz/StatusPill/AskBar의 색·애니메이션으로만 표현. 컬러 추가나 카드 보더 추가로 표현하지 않는다.

---

## 2. 디자인 핸드오프 인덱스

> 핸드오프 묶음(`docs/pipecat-gemini-ui-extracted/`, `docs/pipecat_gemini_ui-handoff.zip`)은 로컬 작업물이며 `.gitignore`로 추적에서 제외돼 있다. 원본 zip을 같은 위치에 풀어둔 상태에서 아래 링크가 동작한다. 작업이 끝나면 해당 폴더는 삭제해도 무방.

| 파일 | 역할 |
| --- | --- |
| `Pipecat Voice UI.html` | 셸 + 5개 화면 캔버스 + Tweaks 패널 마운트 |
| `app.jsx` | **핵심**. 5개 ScreenXxx, AppBar, AskBar, StatusPill, Wordmark, Conversation, OverlayBar, FloatingBubble, Viz(Bars/Orb/Particles), `themes` |
| `android-frame.jsx` | Material 3 device frame. 디자인 자체와 무관(콘텐츠를 끼우는 액자) |
| `design-canvas.jsx` / `tweaks-panel.jsx` | 캔버스/노브 인프라. 구현 대상 아님 |

이후 §4~§7의 모든 컬러·치수·SVG는 `app.jsx`에서 직접 인용한 값이다.

---

## 3. 현재 코드 상태 (v1 적용 결과)

### 3.1 `voice-ui-kit`

- 새로 추가된 (v1) 파일:
  - [package/src/visualizers/GeminiOrb/index.tsx](../../voice-ui-kit/package/src/visualizers/GeminiOrb/index.tsx) — `CircularWaveform` 래퍼 + 라디얼 글로우. **컬러를 vermilion으로 갈고 이름을 `VoiceViz` 또는 `PipecatOrb`로 변경** 필요.
  - [package/src/components/elements/GlassControlBar.tsx](../../voice-ui-kit/package/src/components/elements/GlassControlBar.tsx) — pill + backdrop-blur. **이 디자인의 AskBar에는 backdrop-blur가 없고 솔리드 배경**이라 GlassControlBar는 더 이상 직접 안 쓰임. Overlay 화면(§7.4)에서만 부분적으로 살아남음.
  - [package/src/components/elements/Caption.tsx](../../voice-ui-kit/package/src/components/elements/Caption.tsx) — assistant 마지막 메시지 fade-in. **그대로 유효** (Listening 화면의 인용 문구로 활용).
- [package/src/css/index.css](../../voice-ui-kit/package/src/css/index.css)에 `--gemini-*` 토큰 추가됨. **삭제하거나 `--pipecat-*`로 리네임**.
- [examples/04-vite/src/main.tsx](../../voice-ui-kit/examples/04-vite/src/main.tsx) 풀스크린 다크 + 오브 + 글래스 컨트롤 — 새 디자인은 **Phone shell**이라 레이아웃 재설계.

### 3.2 `mobile_pipecat_android`

- v1에서 [translation/ui/theme/Tokens.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/theme/Tokens.kt), [components/GeminiOrb.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/components/GeminiOrb.kt), [components/GlassControlBar.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/components/GlassControlBar.kt), [components/LanguageChip.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/components/LanguageChip.kt), [components/CaptionLine.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/components/CaptionLine.kt) 신규, [translation/ui/GeminiLiveTranslationApp.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/GeminiLiveTranslationApp.kt) 재구성.
- 새 디자인에서는 **통역 전용 화면이 아닌 일반 어시스턴트 화면**이라 전면 재구성이 필요. 단, 컴포넌트 골격(Orb Canvas, GlassControlBar, BottomSheet 언어 피커)은 재활용 가능.

---

## 4. 디자인 토큰 (v2 / `app.jsx` 직접 인용)

CSS 변수와 Kotlin 상수에 1:1 미러링한다. 모든 값은 `app.jsx`에서 직접 옮긴 값이다.

```yaml
# 다크가 기본
color:
  bg.base.dark:       "#0E0B0A"      # warm black
  surface.dark:       "#1A1413"
  surfaceElev.dark:   "#241B19"
  border.dark:        "rgba(255,255,255,0.08)"
  text.dark:          "#F7F1ED"
  textDim.dark:       "rgba(247,241,237,0.6)"
  textFaint.dark:     "rgba(247,241,237,0.36)"
  inputBg.dark:       "#1F1816"
  chipBg.dark:        "rgba(255,255,255,0.04)"
  chipBorder.dark:    "rgba(255,255,255,0.08)"

  bg.base.light:      "#FAF6F2"      # cream
  surface.light:      "#FFFFFF"
  border.light:       "rgba(14,11,10,0.08)"
  text.light:         "#0E0B0A"
  textDim.light:      "rgba(14,11,10,0.6)"
  textFaint.light:    "rgba(14,11,10,0.36)"

  # 디바이스 외곽(데모/스토리북용)
  device.body:        "#0E0B0A"
  device.bezel:       "#15110F"
  canvas.dark:        "#1B1614"
  canvas.light:       "#E8E2DC"

  # 악센트 (4개 톤. 디폴트는 첫 번째)
  accent.primary:     "#C73E1D"      # deep vermilion (default)
  accent.alt1:        "#D94A2B"
  accent.alt2:        "#E55934"
  accent.alt3:        "#B33A1A"

  # 시맨틱
  state.ready.dot:    "#4ADE80"      # idle 상태일 때 StatusPill 도트
  # 그 외 상태(listening/thinking/speaking)는 accent 사용

font:
  sans:    "'Geist', ui-sans-serif, system-ui, -apple-system"
  mono:    "'Geist Mono', ui-monospace, monospace"
  weights: [400, 500, 600, 700]

radius:
  pill:        9999
  ask.outer:   28          # AskBar 외곽
  ask.inner:   22          # AskBar 안쪽 컨테이너
  chip:        16          # SuggestionCard
  bubble.in:   "18px 18px 18px 4px"   # AI 버블 (좌측 꼭짓점 잘림)
  bubble.out:  "18px 18px 4px 18px"   # 유저 버블 (우측 꼭짓점 잘림)
  overlay:     26          # OverlayBar 외곽
  overlay.in:  22
  device:      44
  device.in:   34

motion:
  pulseDot:     "1.2s ease-in-out infinite"   # listening/active 도트
  blinkCaret:   "1s steps(2) infinite"         # AskBar 카렛
  typingBounce: "1.1s ease-in-out infinite"    # 3-dot typing
  fast:    180ms
  base:    240ms
  slow:    520ms
```

CSS 변수 네이밍: `--pc-bg-base`, `--pc-accent`, `--pc-text-dim` … (`pc` = pipecat). v1의 `--gemini-*`는 모두 폐기.

---

## 5. 컴포넌트 라이브러리 (양 플랫폼 공통)

핸드오프의 `app.jsx`를 컴포넌트 단위로 분해한 결과. 이름은 Pascal case로 통일.

| 컴포넌트 | 책임 | 핵심 prop |
| --- | --- | --- |
| `Wordmark` | pipecat 로고 SVG + "pipecat" 텍스트 | `accent`, `mono` |
| `AppBar` | 상단 바: Wordmark + StatusPill + History 아이콘 | `state`, `latency` |
| `StatusPill` | 도트 + label + latency 텍스트 | `state ∈ {idle, listening, thinking, speaking}`, `latency` |
| `AskBar` | 하단 입력 pill: Plus 아이콘 + placeholder/listening + Mic/Stop + Wave | `state`, `onMicTap`, `placeholder` |
| `MicCaret` | "listening" 옆에 깜빡이는 2px 카렛 | `accent` |
| `VoiceViz` | bars/orb/particles 중 하나를 렌더 | `style`, `accent`, `active`, `size`, `audioTrack` |
| `SuggestionGrid` | 2×2 카드, 각 카드에 tag chip + 제목 | `cards`, `onPick` |
| `Conversation` | 버블 리스트(유저/AI/typing) + 코드 블록 + 칩 액션 | `messages` |
| `OverlayBar` | 호스트 앱 위에 떠 있는 가로 컨테이너 (Wordmark + 미니 viz + label + Stop) | `state`, `accent` |
| `FloatingBubble` | 호스트 앱 위 56dp FAB (Wordmark + 펄스 헤일로 + 활성 도트) | `accent`, `active` |
| `IconButton` | 32~38px 라운드 버튼 (chipBg 배경) | `icon`, `size` |
| `Chip` | 9~12px 라벨 칩 (mono 폰트, 8% bg) | `tone ∈ {default, accent}` |
| `CodeBlock` | 검정 배경 + Geist Mono 12px + 단순 키워드 컬러링 | `lines` |
| `Suggestion` | Idle 화면 카드 하나 | `tag`, `title` |
| `TypingDots` | AI 응답 중 3-dot 바운스 | `accent` |

`AppBar`/`AskBar`를 정확하게 갖춰두면 모든 화면이 그 사이에 콘텐츠만 갈아끼우는 구조라 화면 추가 비용이 매우 낮다.

---

## 6. Front (`voice-ui-kit`) 변경안

### 6.1 토큰

[package/src/css/index.css](../../voice-ui-kit/package/src/css/index.css)의 기존 `--gemini-*` 블록을 `--pc-*`로 **이름 변경 + 값 갈아끼우기**.

```css
:root {
  /* light */
  --pc-bg-base: #FAF6F2;
  --pc-surface: #FFFFFF;
  --pc-text: #0E0B0A;
  --pc-text-dim: rgba(14,11,10,0.6);
  --pc-text-faint: rgba(14,11,10,0.36);
  --pc-border: rgba(14,11,10,0.08);
  --pc-chip-bg: #FFFFFF;
  --pc-chip-border: rgba(14,11,10,0.1);
  --pc-input-bg: #FFFFFF;
  --pc-accent: #C73E1D;
  --pc-state-ready: #4ADE80;
}
.dark {
  --pc-bg-base: #0E0B0A;
  --pc-surface: #1A1413;
  --pc-surface-elev: #241B19;
  --pc-text: #F7F1ED;
  --pc-text-dim: rgba(247,241,237,0.6);
  --pc-text-faint: rgba(247,241,237,0.36);
  --pc-border: rgba(255,255,255,0.08);
  --pc-chip-bg: rgba(255,255,255,0.04);
  --pc-chip-border: rgba(255,255,255,0.08);
  --pc-input-bg: #1F1816;
}
```

`tailwindcss` v4 `@theme` 블록과 충돌하지 않도록 `:root`/`.dark` 변수로 둔 채 Tailwind 유틸과 병행해 쓴다.

### 6.2 컴포넌트 매핑

| 핸드오프 컴포넌트 | voice-ui-kit 적용 위치 |
| --- | --- |
| `Wordmark` | 신규 [package/src/components/elements/Wordmark.tsx](../../voice-ui-kit/package/src/components/elements/Wordmark.tsx) — `PipecatLogo`와 분리 (Wordmark = 로고+텍스트) |
| `AppBar` | 신규 [package/src/components/elements/AppBar.tsx](../../voice-ui-kit/package/src/components/elements/AppBar.tsx) |
| `StatusPill` | 신규. `usePipecatConnectionState` + Observer에서 latency 통계 추출 |
| `AskBar` | 신규 [package/src/components/elements/AskBar.tsx](../../voice-ui-kit/package/src/components/elements/AskBar.tsx) — `UserAudioControl`, `TextInput`, `ConnectButton`을 결합. **기존 `ControlBar`/`GlassControlBar`를 대체** |
| `VoiceViz` | **v1 `GeminiOrb`를 리네임/리워크**. `style="bars"`는 기존 `VoiceVisualizer`(barCount/barColor 그대로 활용 가능), `style="orb"`는 `app.jsx`의 동심원 + 솔리드 코어 패턴으로 신규, `style="particles"`는 새 캔버스 |
| `Conversation` | 기존 [package/src/components/elements/Conversation.tsx](../../voice-ui-kit/package/src/components/elements/Conversation.tsx) 위에 **버블 스타일 추가**. 코드 블록은 `MessageContent`의 markdown 출력에 hex 컬러만 매핑 |
| `Caption` (v1) | 그대로 유지. Listening 화면에서 "사용자가 말한 마지막 문장"을 인용형으로 표시 |
| `SuggestionGrid` | 신규 [package/src/components/elements/SuggestionGrid.tsx](../../voice-ui-kit/package/src/components/elements/SuggestionGrid.tsx) |
| `OverlayBar` | 신규 [package/src/templates/Overlay/index.tsx](../../voice-ui-kit/package/src/templates/Overlay/index.tsx) — 별도 템플릿 |
| `FloatingBubble` | 신규 [package/src/templates/Bubble/index.tsx](../../voice-ui-kit/package/src/templates/Bubble/index.tsx) |

`GlassControlBar`는 OverlayBar 안쪽 컨테이너로 흡수, AskBar는 별도. 둘은 시각이 다르다 (Glass=blur, Ask=솔리드 input pill).

### 6.3 비주얼라이저 3종 구현 메모

- **Bars** (기본): `app.jsx` `VizBars` — 32개 막대, `requestAnimationFrame`에 따라 `h = 0.18 + 0.5·|sin(phase·5 + t·3.5)| × sin(phase·π)^1.4`. **실제 트랙 레벨로 대체**해야 하므로 `usePipecatClientMediaTrack` + `AnalyserNode` 도입. 색은 `var(--pc-accent)` 단일.
- **Orb**: 동심원 3개(`border` only, opacity = `(1-pulse)·0.7`) + 중앙 솔리드 38% (box-shadow glow). v1 `CircularWaveform`은 모양이 너무 달라 재사용 불가 — **새 컴포넌트로 별도 추가**, v1 GeminiOrb는 폐기/리네임.
- **Particles**: 11×11 도트 그리드, `wave = sin(dist·0.9 − t·3)`로 동심원 펄스. WebGL 없이 div 121개로 충분.

### 6.4 예제 ([04-vite](../../voice-ui-kit/examples/04-vite/src/main.tsx)) 재구성

현재 풀스크린 다크 + 오브 + 글래스컨트롤 → **Phone shell 안의 5개 화면 캔버스(또는 단일 Idle)** 로 교체.

기본 권장:
1. 04-vite는 `<ScreenIdle />` 한 화면만 보여주는 데모로 (가장 가시성 높음).
2. 5개 화면 전체는 Ladle stories에 `Console.stories.tsx`처럼 추가해서 디자이너/리뷰어가 토글할 수 있게.

### 6.5 Console 템플릿 영향

[package/src/templates/Console/index.tsx](../../voice-ui-kit/package/src/templates/Console/index.tsx)는 **데스크톱 콘솔**이라 디자인 핸드오프의 모바일 5종과 분리해 둔다. 이번 v2 범위에서는 손대지 않는다 (Phase 5 후속).

---

## 7. Android (`mobile_pipecat_android`) 변경안

### 7.1 토큰

[Tokens.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/theme/Tokens.kt)의 `Gemini` 객체를 **`Pipecat`로 리네임 + 값 교체**:

```kotlin
object Pipecat {
    // 다크 (default)
    val BgDark           = Color(0xFF0E0B0A)
    val SurfaceDark      = Color(0xFF1A1413)
    val SurfaceElevDark  = Color(0xFF241B19)
    val TextDark         = Color(0xFFF7F1ED)
    val TextDimDark      = Color(0x99F7F1ED)   // alpha 0.6
    val TextFaintDark    = Color(0x5CF7F1ED)   // alpha 0.36
    val InputBgDark      = Color(0xFF1F1816)
    val ChipBgDark       = Color(0x0AFFFFFF)
    val ChipBorderDark   = Color(0x14FFFFFF)

    // 라이트
    val BgLight          = Color(0xFFFAF6F2)
    val SurfaceLight     = Color(0xFFFFFFFF)
    val TextLight        = Color(0xFF0E0B0A)
    val TextDimLight     = Color(0x990E0B0A)
    val TextFaintLight   = Color(0x5C0E0B0A)
    val BorderLight      = Color(0x140E0B0A)

    // 악센트 (디폴트 + 3개 대안)
    val Vermilion        = Color(0xFFC73E1D)
    val VermilionAlt1    = Color(0xFFD94A2B)
    val VermilionAlt2    = Color(0xFFE55934)
    val VermilionAlt3    = Color(0xFFB33A1A)

    // StatusPill 도트 (idle 전용)
    val ReadyGreen       = Color(0xFF4ADE80)
}
```

`GeminiPalette.forTheme(isDark)`도 `PipecatPalette`로 같이 리네임.

### 7.2 GeminiLiveTranslationApp 재정의

지금 화면은 "통역 1개"였지만 새 디자인은 어시스턴트 5개 화면이다. 이 앱이 통역 전용임을 유지하려면 두 옵션 중 하나를 선택:

- **A. 5개 화면 풀세트 구현**(권장): Idle(통역 시작 전 suggestions가 "한↔영", "한↔일" 같은 페어), Listening(통역 청취), Conversation(번역 결과 리스트), Overlay(다른 앱 위 통역 바), Bubble(통역 FAB).
- **B. Listening 화면 한 개만 채택**: 통역 시작/정지 + 비주얼라이저 + 인용 자막. 가장 작은 변경.

기본 권장은 **B**로 시작 → 운영 안정화 후 A. 이번 v2 가이드의 §8 작업분해도 B 기준으로 정리한다.

### 7.3 v1 컴포넌트 처리

| v1 파일 | v2 처리 |
| --- | --- |
| [GeminiOrb.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/components/GeminiOrb.kt) | **삭제 또는 리네임 → VoiceVizOrb.kt**. 컬러를 vermilion 단일로 바꾸고 sweep gradient는 솔리드 → 동심원 ring으로 갈음 |
| [GlassControlBar.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/components/GlassControlBar.kt) | OverlayBar 안쪽 컨테이너로 흡수. 메인 Listening 화면의 컨트롤은 새 `AskBar`로 교체 |
| [LanguageChip.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/components/LanguageChip.kt) | `Chip` 일반 컴포넌트로 일반화. Listening 화면에서는 상단 AppBar 우측에 작은 칩으로 노출 |
| [CaptionLine.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/components/CaptionLine.kt) | 그대로 유지 (Listening 화면의 "How do I deploy this to a Raspberry Pi?" 인용 영역에 매핑) |
| [Tokens.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/theme/Tokens.kt) | 7.1대로 갈아엎기 |
| [GeminiLiveTranslationApp.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/GeminiLiveTranslationApp.kt) | 7.2 옵션 B 기준 재작성 |

### 7.4 Compose 구현 메모

- **Backdrop blur**는 API 31+의 `RenderEffect.createBlurEffect`. 새 디자인의 메인 AskBar는 blur가 없으므로(솔리드 input bg) 31+ 의존 없이 구현 가능. Overlay 화면만 blur 필요.
- **VizBars**는 `Canvas { drawRoundRect }` 32회. AnalyserNode가 없는 Compose에서는 `interpretationUseCase.audioAmplitude`를 32개 페이즈로 샘플링해 envelope 함수와 곱한다.
- **StatusPill의 latency**는 `interpretationUseCase`에 `lastRoundTripMs` 같은 필드를 추가하고 1초 주기로 노출. 측정값이 없으면 칩 자체를 숨긴다(추정치 X).

---

## 8. 5개 화면 상세 (Idle / Listening / Conversation / Overlay / Bubble)

각 화면별로 (1) AppBar 상태, (2) 본문, (3) AskBar 상태, (4) 핸드오프 인용 좌표를 정리. 좌표는 `app.jsx`의 라인 번호.

### 8.1 Idle ([app.jsx:448](./project/app.jsx#L448))

- AppBar: `state="idle"` → StatusPill 도트 녹색, label "ready · 142ms"
- 본문:
  - 인사말: `Good morning,\nhow can I help today?` — 28sp / weight 500 / tracking `-0.02em` / `today`만 vermilion 33% 알파 하이라이트 박스
  - 세션 라벨: `SESSION · 2026·05·19` (Geist Mono 10.5sp / textFaint)
  - "SUGGESTIONS · see all →" 헤더 (mono, vermilion CTA)
  - `SuggestionGrid` 2×2:
    - `[code]` Explain this Python traceback (첫 카드만 tag 칩이 vermilion 솔리드 + 흰 글자)
    - `[plan]` Draft a 5-step launch checklist
    - `[voice]` Practice my standup intro
    - `[data]` Summarize the attached CSV
- AskBar: `state="idle"` → 외곽 보더 textFaint, mic 버튼은 chipBg, placeholder "Ask anything"

### 8.2 Listening ([app.jsx:493](./project/app.jsx#L493))

- AppBar: `state="listening"` → StatusPill 도트 vermilion + pulseDot, label "listening · 218ms"
- 본문:
  - 라디얼 글로우 `radial-gradient(circle at 50% 55%, accent·15% 0%, transparent 55%)` (`#C73E1D26`)
  - `VoiceViz` 가운데 (size=240)
  - 인용 문구: `"How do I deploy this\nto a Raspberry Pi?"` — 22sp / weight 500
  - 하단 시스템 칩: vermilion 펄스 도트 + `microphone active · whisper-large-v3` (Geist Mono 10.5)
- AskBar: `state="listening"` → 외곽 보더 vermilion, box-shadow `0 0 0 4px #C73E1D22` + `0 8px 24px rgba(0,0,0,0.18)`, placeholder를 `listening<MicCaret/>`로 교체, mic 자리에 Stop 아이콘 (vermilion 솔리드 + `0 6px 18px #C73E1D66`)

### 8.3 Conversation ([app.jsx:537](./project/app.jsx#L537))

- AppBar: `state="thinking"` → StatusPill 도트 vermilion + pulseDot, label "thinking · 218ms"
- 본문 (`Conversation` 컴포넌트):
  - 유저 버블 (우측, 82% 최대 너비): `accent` 채움, 흰 글씨, radius `18 18 4 18`, 하단 "you · 09:32" 메타
  - AI 버블 (좌측, 88% 최대): Sparkle + `pipecat · gemma-3-27b` 메타 → surface 배경 + border → `1s` 인라인 코드 칩 + 본문 → 검정 CodeBlock(2줄 Python) → 칩 3개 (Show in editor / Explain jitter / Try aiohttp variant)
  - 스트리밍 버블: Sparkle + `typing<TypingDots/>` (3-dot bounce, 0/0.15/0.3s 지연)
- AskBar: `state="idle"`, placeholder "Reply or ask follow-up"

### 8.4 Overlay bar ([app.jsx:617](./project/app.jsx#L617), 스크린은 [L693](./project/app.jsx#L693))

- 배경: `FakeHostApp` (Notes 같은 호스트 앱 mock)
- 오버레이 컨테이너 (absolute bottom 12px, left/right 12px):
  - 외곽 패딩 4px, radius 26, bg `accent · 13% alpha`, box-shadow `0 0 0 1.5px accent, 0 20px 50px rgba(0,0,0,0.4), 0 6px 16px accent33%`
  - `backdrop-filter: blur(20px)` 적용
  - 내부 (radius 22, bg dark=`rgba(20,16,15,0.92)` / light=`rgba(255,255,255,0.92)`):
    - 좌: Pipecat 워드마크 SVG (22×22)
    - 가운데: 미니 VizBars (22개 막대, width 3, height 28)
    - 우측: `listening` 라벨 (Geist Mono 10.5) → Stop 버튼 (36×36 vermilion + `0 4px 12px accent88`)
- AskBar 없음. **이 화면은 System Overlay** 라서 AppBar/AskBar는 호스트 앱 것을 그대로 노출

### 8.5 Bubble ([app.jsx:704](./project/app.jsx#L704))

- 배경: `FakeHostApp`
- FAB (absolute bottom 16, left 16, 56×56):
  - 헤일로 1: `accent · 35% alpha`, scale `1 + pulse·0.35`, blur 1px
  - 헤일로 2: `box-shadow: 0 0 0 (2+pulse·6)px accent33`
  - 코어: 원형 vermilion + Pipecat 워드마크 SVG 26×26 (사각 없음, 막대 2개만), box-shadow `0 8px 22px accent66, 0 2px 6px rgba(0,0,0,0.3)`
  - 활성 배지: 우상단 14×14 흰 원 + 안에 8×8 vermilion 펄스 도트

---

## 9. 작업 분해 (Phase 1~5+) — 진행 현황

체크박스 끝의 hash는 해당 작업의 대표 커밋. voice-ui-kit은 `vui:`, mobile_pipecat_android는 `and:` 접두로 표기. 이 문서는 git 미관리(로컬 전용) 이며, **커밋 hash가 작업 추적의 단일 진실 원천**.

### Phase 1 — v1 자산 정리 + 토큰 교체 ✅
- [x] voice-ui-kit: `--gemini-*` → `--pc-*` 변수 리네임 + vermilion 값 교체 (light/dark) — `vui:887037e`
- [x] voice-ui-kit: v1 `GeminiOrb` 디렉토리 삭제 → `VoiceViz` 신규 — `vui:887037e`
- [x] voice-ui-kit: v1 `GlassControlBar` 토큰만 갱신, OverlayBar 안쪽 컨테이너로 흡수 — `vui:887037e`
- [x] Android: `Gemini` 토큰 객체 → `Pipecat` 리네임, vermilion 값 교체 — `and:eec75c7`
- [x] Android: 사망 파일 일괄 삭제 (`GeminiOrb`/`GlassControlBar`/`LanguageChip`/`AudioVisualizer`/`Common`/`StatusRing`) — `and:eec75c7`
- [x] Android: `GeminiLiveTranslationApp` 옵션 B로 재정의 (이름 유지, 내용 갈아치움) — `and:eec75c7`

### Phase 2 — 공통 컴포넌트 ✅
- [x] Wordmark / PipecatAppBar / StatusPill / IconBtn / PcChip 신규 (양 플랫폼) — `vui:887037e`, `and:eec75c7`
- [x] AskBar (3-state: idle / listening / disabled) — `vui:887037e`, `and:eec75c7`
- [x] VoiceViz 3종 (Bars / Orb / Particles): voice-ui-kit은 모두 새 컴포넌트, Android는 Bars + Orb (Particles는 안 만듦, 모바일은 입력 레벨 기반 Bars 위주라) — `vui:887037e`, `and:eec75c7`
- [x] Caption: v1 컴포넌트를 storybook-safe로 분리 (`text` prop 명시 시 hook 미호출) — `vui:b219b8b`

### Phase 3 — 5개 화면 ✅ (옵션 A 풀세트)
- [x] voice-ui-kit `templates/PipecatLive/`: PhoneShell + FakeHostApp + ScreenIdle/Listening/Conversation/Overlay/Bubble — `vui:4b1b336`
- [x] voice-ui-kit elements: ConversationBubble, CodeBlock, TypingDots, OverlayBar, FloatingBubble — `vui:4b1b336`
- [x] 04-vite를 5개 화면 토글 캔버스로 — `vui:4b1b336`
- [x] Android `translation/ui/screens/`: 동일 5개 Screen + 보조 components(SuggestionGrid/TypingDots/CodeBlock/ConversationBubble/FakeHostApp/OverlayBar/FloatingBubble) — `and:e537727`
- [x] PipecatPreviewActivity (5개 화면 탭 토글 데모) + AndroidManifest 등록 — `and:e537727`
- [x] **잔여 비범위 해소**: Android System Overlay(SYSTEM_ALERT_WINDOW)/Bubble API 시스템 인프라 — §14-B·C에서 구현 완료 (`and:1e40d17`)

### Phase 4 — 데이터 연결 + QA ✅
- [x] voice-ui-kit `usePipecatLatency` hook (RTVIEvent.Metrics TTFB → EMA 0.6) — `vui:e9cd67f`
- [x] AppBar `autoLatency` prop (기본 true) — `vui:e9cd67f`
- [x] Android `InterpretationUseCase.latencyMs` StateFlow (마지막 mic chunk 전송 ~ 첫 TTS audio 도착 EMA) — `and:bf7382d`
- [x] Android ViewModel.uiState.latencyMs → PipecatAppBar 연결 — `and:47be0e8`, `and:bf7382d`
- [x] Idle 인사말 시간대 분기 (morning/afternoon/evening) — `vui:4b1b336`, `and:e537727`
- [x] 04-vite Tweaks 패널(light/dark · accent 4종 · viz 3종) — `vui:e9cd67f`
- [x] 접근성: 주요 아이콘 버튼 aria-label/contentDescription 확인, prop override 가능 — `vui:2d7ec7a`, `and:46d9058`
- [x] 라이트 모드 검증 (palette 통한 자동 분기 확인) — `and:eec75c7`
- [~] SuggestionGrid 카드 동적화 — 시간대/요일 분기는 §14-F에서 완료. 앱별/사용자별 추천을 ConfigService·server에서 가져오는 부분만 후속

### Phase 5+ — 가이드 명시 외 후속 작업 ✅
- [x] **Console 데스크톱 템플릿 톤 통일**: PipecatLogo → Wordmark 교체, `accent` prop 추가로 인스턴스별 `--pc-accent` override — `vui:fd46ddd`
- [x] **Ladle Stories**: Wordmark/StatusPill/AppBar/AskBar/VoiceViz(3종)/OverlayBar/FloatingBubble/ConversationBubble/CodeBlock/TypingDots/Caption/SuggestionGrid + 5개 Screen 토글 (7개 파일) — `vui:b219b8b`
- [x] **storybook-safe 분리**: AppBar `autoLatency=false`/Caption `text` 명시 시 client hook 미호출 — `vui:b219b8b`
- [x] **PipecatPreviewActivity 진입 경로**: 신규 `PipecatPreviewPlugin` (JS bridge `PipecatPreview / open`) + `pipecat://preview` deep link — `and:bf7382d`
- [x] **라벨/aria-label prop 외부화**: StatusPill `labels` map, AskBar 5개 라벨, OverlayBar `stopLabel`, ScreenIdle 6개 라벨, ScreenConversation 2개 — `vui:2d7ec7a`, `and:46d9058`
- [x] **Android strings.xml(ko/en) i18n**: `pipecat_*` 23개 라벨 + `rememberPipecatStrings()` helper + `greetingForHour()` — `and:188fd2c`
- [x] **04-vite vite chunks 경고 해소**: `manualChunks`로 8개 vendor 청크 분리 (1087KB → 가장 큰 게 483KB) — `vui:db3fd4c`
- [x] **AskBar 텍스트 입력 모드**: 양 플랫폼 controlled `value`/`onValueChange`/`onSubmit` prop. value 미정의면 visual-only(현행 유지) — `vui:70a1e14`, `and:77e70a7`
- [x] **AskBar 텍스트 전송 흐름 연결** (§14-D): voice-ui-kit `usePipecatTextInput` hook(`injectMessage`로 로컬 표시 + `client.appendToContext`로 bot 트리거) → `ScreenConversation` `onSendMessage` prop, 04-vite는 hook 연결 + live 메시지를 turns로 매핑. Android는 `GeminiLiveService.sendText`(`clientContent`/`turnComplete`) → `InterpretationUseCase.sendText`(세션 미연결 시 자동 start + `pendingText` flush) → `ViewModel.sendTextInput` → `GeminiLiveTranslationApp` `onSendText` — `vui:3a163cf`, `and:20bc33c`
- [x] **chart.js 지연 로딩** (§14-A): `ConversationPanel`이 `Metrics`를 `React.lazy`로 분리 + `tsup splitting:true`로 async chunk 경계 생성. `package.json`에 `sideEffects:["**/*.css"]` 추가(npm 소비자 트리셰이킹용). 결과: 04-vite 초기 번들에서 chart.js 150KB 제거 — async chunk(`metrics`+`vendor-charts`)로 이동해 Metrics 탭 렌더 시에만 로드, `index.html` modulepreload에서도 빠짐 — `vui:e45faa5`
- [x] **SuggestionGrid 시간대/요일 동적화** (§14-F): ScreenIdle 기본 카드 셋을 morning/afternoon/evening/weekend 4종으로 분기 (`defaultCards()` / `suggestionsForContext()`). 외부 의존(server/ConfigService) 없이 로컬 시계만으로 결정. `cards`/`suggestions` prop override는 그대로 유지 — `vui:7db2cb7`, `and:7f422dc`
- [x] **voice-ui-kit lint/format 정리** (§14-E): `eslint --fix`로 20개 파일 prettier 포매팅 통일(sort-imports 포함). `PipecatAppBase`의 `window.__pipecatClient`를 `declare global`로 타입화해 `no-explicit-any` 2건 제거. `eslint ./src` 에러 72 → 0 — `vui:856e123`
- [x] **가이드 파일 리네임** (§14-H): `gemini-ui-migration.md` → `pipecat-voice-ui-migration.md` (로컬 untracked라 git 영향 없음)
- [x] **Android System Overlay 인프라** (§14-B): `SYSTEM_ALERT_WINDOW` 권한 + `PipecatOverlayService`(foreground service) — `WindowManager`(`TYPE_APPLICATION_OVERLAY`)로 `OverlayBar`를 다른 앱 위에 표시. 서비스가 `LifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner`를 겸해 `ComposeView` 호스팅. `PipecatPreviewPlugin`에 `openOverlay`/`closeOverlay`(권한 미허용 시 설정 화면 유도) — `and:1e40d17`
- [x] **Android Bubble API** (§14-C): `PipecatBubbleManager`가 long-lived dynamic shortcut + `Person` + `BubbleMetadata`로 시스템 Bubble 알림 게시(API 30+ guard). 확장 화면은 embedded `PipecatBubbleActivity`(`allowEmbedded`/`resizeableActivity`/`documentLaunchMode`). `PipecatPreviewPlugin`에 `openBubble`/`closeBubble`(API<30 → `unsupported`, 알림 꺼짐 → `notifications_disabled`) — `and:1e40d17`
- [x] **테스트 추가** (§14-G): voice-ui-kit Vitest + React Testing Library 인프라 신규 + 컴포넌트 테스트 5파일 28케이스(StatusPill/AppBar/AskBar/Wordmark/IconBtn — a11y 라벨·prop 외부화·controlled 입력). Android Compose UI Test 의존성 추가 + 계측 테스트 2파일(`PipecatPreviewActivityTest` 5화면 전환 / `AskBarComposeTest` content description·토글·controlled 입력). `pnpm test` 28 passed + `eslint` 0 errors, `compileDebugAndroidTestKotlin` BUILD SUCCESSFUL — `vui:88dbdf5`, `and:efef98f`

---

## 10. 비범위 (Out of scope)

### 이미 해소된 항목
- ~~voice-ui-kit `Console` 데스크톱 템플릿~~ → Phase 5에서 톤 통일 완료 (`vui:fd46ddd`)
- ~~chart.js(150KB)가 04-vite 초기 번들에 묶임~~ → §14-A에서 `Metrics` 지연 로딩으로 해소 (§9 Phase 5+)
- ~~Android System Overlay (`SYSTEM_ALERT_WINDOW`) 인프라~~ → §14-B에서 `PipecatOverlayService`로 구현 (`and:1e40d17`)
- ~~Android Bubble API (API 30+) 시스템 버블 등록~~ → §14-C에서 `PipecatBubbleManager`로 구현 (`and:1e40d17`)
- ~~테스트 부재 (양 플랫폼 unit/UI 테스트 없음)~~ → §14-G에서 voice-ui-kit Vitest 28케이스 + Android Compose UI Test 2파일로 초기 커버리지 확보 (§9 Phase 5+)

### 여전히 비범위 (다음 후보 — §14 참조)
- **음성 파이프라인 자체** (Pipecat 처리 흐름) 수정 없음. UI 레이어와 토큰만 다룸.
- **새 디자인 시스템 패키지 분리** — 현재 voice-ui-kit 한 패키지에 모두 있음.
- **voice-ui-kit `panels`를 별도 export entry로 분리** — chart.js 문제는 §14-A(지연 로딩)로 해소됐으므로 더는 급하지 않음. 순수하게 API 표면을 정리하고 싶을 때의 선택적 리팩터(breaking).
- **테스트 커버리지 확대** — §14-G에서 leaf 컴포넌트 + PipecatPreview 전환을 덮음. 템플릿(`PipecatLive`/`Console`)·hooks·screens 단위 테스트, Android 계측 테스트의 CI 기기 실행은 후속.

---

## 11. 파일명/명명 정합성 메모

이 가이드는 v1 흔적인 `gemini-ui-migration.md`였으나, 디자인 자체가 Gemini가 아니므로 §14-H에서 `pipecat-voice-ui-migration.md`로 리네임 완료(로컬 untracked 파일이라 git 영향 없음).

또 v1에서 만든 컴포넌트 이름(`GeminiOrb`, `GlassControlBar`, `Gemini` 토큰)이 살아있는 동안에는 본문에서 v1 이름을 그대로 인용하되, Phase 1이 끝나면 모두 `Pipecat`/`VoiceViz`/`Pc` 접두로 통일된다.

---

## 12. 참고 파일 한눈에 (v2 구현 후)

### 디자인 핸드오프 (로컬 전용, git 미추적)
- [docs/pipecat-gemini-ui/README.md](README.md)
- [docs/pipecat-gemini-ui/project/Pipecat Voice UI.html](project/Pipecat%20Voice%20UI.html)
- [docs/pipecat-gemini-ui/project/app.jsx](project/app.jsx) — **§8 화면별 상세의 라인 인용 출처**

### voice-ui-kit — 현재 구조

**진입/데모**
- [examples/04-vite/src/main.tsx](../../voice-ui-kit/examples/04-vite/src/main.tsx) — 5개 화면 토글 + Tweaks 패널 (light/dark, accent, viz)
- [examples/04-vite/vite.config.ts](../../voice-ui-kit/examples/04-vite/vite.config.ts) — manualChunks 설정

**디자인 토큰**
- [package/src/css/index.css](../../voice-ui-kit/package/src/css/index.css) — `--pc-*` 토큰 + `pc-*` keyframes

**핵심 신규 컴포넌트** (`package/src/components/elements/`)
- `Wordmark.tsx` · `AppBar.tsx` · `StatusPill.tsx` · `IconBtn.tsx` · `PcChip.tsx`
- `AskBar.tsx` (controlled text input 지원) · `Caption.tsx` (storybook-safe)
- `ConversationBubble.tsx` · `CodeBlock.tsx` · `TypingDots.tsx`
- `OverlayBar.tsx` · `FloatingBubble.tsx` · `SuggestionGrid.tsx`
- `GlassControlBar.tsx` — OverlayBar 내부용으로 토큰만 갱신
- `*.stories.tsx` — 7개 stories 파일

**비주얼라이저** (`package/src/visualizers/`)
- `VoiceViz/` — Bars/Orb/Particles 신규
- `CircularWaveform/` — 별도 컴포넌트로 유지 (v2와 직접 연관 없음)
- `VoiceVisualizer/` — 레거시 막대 visualizer

**Hooks** (`package/src/hooks/`)
- `usePipecatLatency.ts` — RTVIEvent.Metrics TTFB 추적

**Templates**
- `templates/PipecatLive/` — PhoneShell + FakeHostApp + 5개 Screen + Screens.stories
- `templates/Console/index.tsx` — Wordmark + `accent` prop 적용

**테스트** (Vitest + React Testing Library)
- `vitest.config.ts` (jsdom) · `vitest.setup.ts` (jest-dom 매처 + cleanup)
- `src/components/elements/*.test.tsx` — StatusPill / AppBar / AskBar / Wordmark / IconBtn (28 케이스). 실행: `pnpm test`

### mobile_pipecat_android — 현재 구조

**진입**
- [activity/GeminiLiveTranslationActivity.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/activity/GeminiLiveTranslationActivity.kt) — 통역 화면(옵션 B Listening 단일)
- [activity/PipecatPreviewActivity.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/activity/PipecatPreviewActivity.kt) — 5개 화면 미리보기 (탭 토글). `pipecat://preview` deep link 가능
- [web/plugin/PipecatPreviewPlugin.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/web/plugin/PipecatPreviewPlugin.kt) — JS bridge `PipecatPreview / open`

**디자인 토큰 & i18n**
- [translation/ui/theme/Tokens.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/theme/Tokens.kt) — Pipecat 객체 + PipecatPalette
- [translation/ui/components/PipecatStrings.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/components/PipecatStrings.kt) — `rememberPipecatStrings()`
- [res/values/strings.xml](../../mobile_pipecat_android/app/src/main/res/values/strings.xml) (ko) + [res/values-en/strings.xml](../../mobile_pipecat_android/app/src/main/res/values-en/strings.xml)

**핵심 컴포넌트** (`translation/ui/components/`)
- `Wordmark.kt` · `AppBar.kt` (PipecatAppBar) · `StatusPill.kt` · `AskBar.kt` (controlled input 지원)
- `VoiceVizBars.kt` · `VoiceVizOrb.kt`
- `ConversationBubble.kt` · `CodeBlock.kt` · `TypingDots.kt` · `SuggestionGrid.kt`
- `OverlayBar.kt` · `FloatingBubble.kt` · `FakeHostApp.kt`
- `CaptionLine.kt` — Listening 인용 영역
- `LanguageSelector.kt` — `LanguagePickerContent`만 사용 (BottomSheet)

**5개 화면** (`translation/ui/screens/`)
- `ScreenIdle.kt` · `ScreenListening.kt` · `ScreenConversation.kt` · `ScreenOverlay.kt` · `ScreenBubble.kt`

**메인 화면 + ViewModel**
- [translation/ui/GeminiLiveTranslationApp.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/ui/GeminiLiveTranslationApp.kt) — 통역 Listening 단일 화면
- [translation/presentation/GeminiLiveTranslationViewModel.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/presentation/GeminiLiveTranslationViewModel.kt) — `uiState.latencyMs`
- [translation/domain/usecase/InterpretationUseCase.kt](../../mobile_pipecat_android/app/src/main/java/com/lgcns/codeengine/translation/domain/usecase/InterpretationUseCase.kt) — `latencyMs: StateFlow<Int?>` (실측)

**테스트** (Compose UI Test, `app/src/androidTest/`)
- `translation/ui/PipecatPreviewActivityTest.kt` — 5개 화면 탭 전환 검증
- `translation/ui/AskBarComposeTest.kt` — AskBar a11y/토글/controlled 입력
- 컴파일: `./gradlew :app:compileDebugAndroidTestKotlin` (JDK 17). 실행은 기기/에뮬레이터 필요

---

## 13. 실행해서 보기 (Quickstart)

단계별로 어디서 어떻게 UI를 띄울 수 있는지.

### voice-ui-kit (웹)

**04-vite 데모** — Phase 3부터 5개 화면이 모두 보임.

```bash
cd /Users/ds.ryu/project/voice-ui-kit/examples/04-vite
pnpm install   # 한 번만
pnpm dev       # http://localhost:5173
```

| 단계 | 04-vite에서 보이는 것 |
| --- | --- |
| Phase 2 끝 (`vui:887037e`) | Phone shell + ScreenIdle 하나만 |
| Phase 3 끝 (`vui:4b1b336`) | **5개 화면 탭 토글** (Idle/Listening/Conversation/Overlay/Bubble) |
| Phase 4 (`vui:e9cd67f`) | 우상단 **Tweaks 패널** (light/dark · vermilion 4종 · viz 3종) |
| Phase 5 Console (`vui:fd46ddd`) | Console 데스크톱 템플릿도 새 톤 (04-vite엔 아직 안 띄움 — Ladle에서) |

**Ladle Stories** (Phase 5 — `vui:b219b8b`) — `PipecatAppBase` 없이도 모든 컴포넌트 단독 데모.

```bash
cd /Users/ds.ryu/project/voice-ui-kit/package
pnpm dev   # ladle dev server (보통 http://localhost:61000)
```

스토리 카테고리:
- *Pipecat Voice UI / Identity* — Wordmark · PcChip · IconBtn · SuggestionGrid
- *Pipecat Voice UI / Status* — StatusPill + AppBar 4 상태
- *Pipecat Voice UI / Controls* — AskBar 3 상태 + 텍스트 입력 데모 (`vui:70a1e14`)
- *Pipecat Voice UI / Visualizers* — Bars/Orb/Particles 3종
- *Pipecat Voice UI / Floating* — OverlayBar + FloatingBubble
- *Pipecat Voice UI / Conversation* — Bubble · CodeBlock · TypingDots · Caption
- *Pipecat Voice UI / Screens* — 5개 화면 토글 + Focus 5종

### Android

**통역 화면** — Phase 1 끝(`and:eec75c7`)부터 새 톤으로 동작. 옵션 B(Listening 단일).

```bash
cd /Users/ds.ryu/project/mobile_pipecat_android
./gradlew :app:installDebug
# 앱 안에서 JS bridge로 LiveTranslation/startTranslation 호출
```

기존 WebView 진입 경로 그대로. Phase 5 i18n(`and:188fd2c`) 이후로는 시스템 언어가 한국어면 자동으로 한글 라벨.

**PipecatPreviewActivity (5개 화면 미리보기)** — Phase 3 끝(`and:e537727`) + Phase 5 deep link(`and:bf7382d`)부터 가능.

```bash
# ADB 한 줄로 띄우기 (가장 간단)
adb shell am start -a android.intent.action.VIEW -d "pipecat://preview"
```

또는 JS bridge:
```js
pipecatBridge.execute("PipecatPreview", "open", {}, requestId)
```

상단 vermilion mono pill 탭으로 5개 화면 전환. 시스템 언어 자동 반영.

### 가장 빠르게 보고 싶다면

- **풀세트 화면 + Tweaks**: `voice-ui-kit/examples/04-vite` → `pnpm dev`
- **개별 컴포넌트**: `voice-ui-kit/package` → `pnpm dev` (Ladle)
- **Android 통역 새 톤**: `./gradlew :app:installDebug` 후 기존 진입 경로
- **Android 5개 화면**: `adb shell am start -a android.intent.action.VIEW -d "pipecat://preview"`

### 테스트 실행

```bash
# voice-ui-kit 컴포넌트 테스트 (Vitest + RTL, jsdom)
cd /Users/ds.ryu/project/voice-ui-kit/package && pnpm test

# Android 계측 테스트 — 컴파일만 (JDK 17 필요, Gradle 8.2는 JDK 21 미지원)
cd /Users/ds.ryu/project/mobile_pipecat_android
JAVA_HOME=<jdk17> ./gradlew :app:compileDebugAndroidTestKotlin
# 실제 실행은 기기/에뮬레이터 연결 후:
JAVA_HOME=<jdk17> ./gradlew :app:connectedDebugAndroidTest
```

---

## 14. 다음 작업 후보 (우선순위 + 시작점)

남은 비범위 + 후속 후보. 각 항목은 **시작 파일/명령**까지 적어놓아 새 세션에서 바로 이어갈 수 있게 정리.

### A. voice-ui-kit chart.js 트리셰이킹 — ✅ 완료 (§9 Phase 5+ 참조)
- **결과**: 보수적 옵션(`React.lazy`)으로 해결. breaking change 없음.
  - `ConversationPanel`이 `Metrics`를 `lazy(() => import("@/components/metrics"))` + `Suspense`로 지연 로딩. `ConversationPanel`/`ConsoleTemplate` 어느 경로로 도달하든 chart.js가 정적 그래프에서 빠짐.
  - `tsup.config.ts` `splitting:true` — 안 하면 esbuild가 동적 import를 다시 inline해버려 chunk 경계가 안 생김. dist에 `chunk-*.js` 공유 청크가 생기지만 `exports` 매핑은 그대로 동작.
  - `package.json` `sideEffects:["**/*.css"]` 추가. npm 설치 소비자는 이걸로 chart.js를 완전히 트리셰이킹. (단 04-vite는 workspace 심링크라 dist가 node_modules 밖으로 resolve돼 `sideEffects`가 무시됨 → 04-vite에선 지연 로딩만 효과.)
- **결과 측정**: 04-vite 초기 번들 chart.js 0 (이전 eager 150KB). `index.html`이 `vendor-charts`를 modulepreload하지 않음. `metrics`(4.3KB)+`vendor-charts`(150KB)는 async chunk로만 존재, Metrics 탭을 열 때만 fetch.
- **남은 후속**: §10의 "panels 별도 entry 분리"는 여전히 비범위 — 현 해법으로 충분.

### B. Android System Overlay 인프라 — ✅ 완료 (§9 Phase 5+ 참조)
- **결과**: `app/.../translation/overlay/PipecatOverlayService.kt` 신규.
  - 매니페스트: `SYSTEM_ALERT_WINDOW` 권한 + `foregroundServiceType="specialUse"` 서비스 등록(`ForegroundAppService` 패턴 차용).
  - `PipecatOverlayService`가 `LifecycleOwner`+`ViewModelStoreOwner`+`SavedStateRegistryOwner`를 직접 구현 → `ComposeView`에 `setViewTreeLifecycleOwner`/`...ViewModelStoreOwner`/`...SavedStateRegistryOwner` attach 후 `WindowManager.addView`(`TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE|FLAG_NOT_TOUCH_MODAL`, bottom gravity).
  - 권한 미허용 시 서비스가 self-stop(방어). `OverlayBar` Stop 버튼 / 알림 Stop 액션으로 종료.
  - 진입: `PipecatPreviewPlugin`에 `openOverlay`(권한 없으면 `ACTION_MANAGE_OVERLAY_PERMISSION` 유도 후 `permission_required` 반환)/`closeOverlay`.
- **남은 후속**: 오버레이 위치 드래그, 멀티 윈도우 대응은 후속.

### C. Bubble API Android Service — ✅ 완료 (§9 Phase 5+ 참조)
- **결과**: `app/.../translation/bubble/PipecatBubbleManager.kt` + `activity/PipecatBubbleActivity.kt` 신규.
  - `PipecatBubbleManager.show()`: 알림 채널(`setAllowBubbles`) + long-lived dynamic shortcut(`ShortcutManagerCompat.pushDynamicShortcut`) + `Person` + `MessagingStyle` + `BubbleMetadata`(pendingIntent/icon, `autoExpand`/`suppressNotification`)로 conversation 알림 게시. API 30(`R`) guard.
  - `PipecatBubbleActivity`: `allowEmbedded`+`resizeableActivity`+`documentLaunchMode="always"`로 선언, 확장 버블 안에서 `OverlayBar` 렌더.
  - 진입: `PipecatPreviewPlugin`에 `openBubble`/`closeBubble`. API<30 → `unsupported`, 알림 비활성 → `notifications_disabled`.
- **남은 후속**: 버블 첫 표시 시 OS가 일반 알림으로 띄우고 사용자가 버블 승격을 허용해야 함(OS 정책) — 자동 강제 불가.

### D. AskBar 텍스트 입력의 실제 전송 흐름 연결 — ✅ 완료 (§9 Phase 5+ 참조)
- **결과**:
  - voice-ui-kit: `usePipecatTextInput` hook 신규(`package/src/hooks/`). `injectMessage`로 타이핑한 메시지를 대화 스트림에 로컬 표시 + `client.appendToContext({ role:"user", run_immediately:true })`로 bot 응답 트리거. `ScreenConversation`에 `onSendMessage` prop(있으면 AskBar가 편집 가능 필드, 없으면 visual-only). 04-vite는 hook 연결 + `usePipecatConversation().messages`를 turns로 매핑해 왕복 표시.
  - Android: `GeminiLiveService.sendText`(`clientContent`+`turnComplete:true` WebSocket 메시지) → `InterpretationUseCase.sendText(text, targetLanguage)`(세션 Active면 즉시, 미연결이면 자동 `start` + `pendingText`를 Connected 시 flush) → `ViewModel.sendTextInput` → `GeminiLiveTranslationApp` `onSendText` + AskBar controlled 연결.
- **남은 후속**: live 메시지 → turns 매핑이 04-vite(예제)에만 있음. `ScreenConversation`이 직접 `usePipecatConversation`을 구독하게 만들지는 의도적으로 보류(템플릿 화면은 presentational 유지). Android `screens/ScreenConversation.kt`(미리보기 전용)는 백엔드가 없어 미연결.

### E. voice-ui-kit lint/format 정리 — ✅ 완료 (§9 Phase 5+ 참조)
- **결과**: `eslint ./src` 72 errors → 0. prettier 포매팅 70건 `--fix` 자동수정 + `no-explicit-any` 2건 수동 수정(`PipecatAppBase`의 `window.__pipecatClient`를 `declare global`로 타입화). 미사용 변수/import는 발견된 것 없음.
- **남은 경고 2건**(의도적 보류): `ConversationProvider`의 `react-refresh/only-export-components`(고치려면 `useConversationContext`를 별도 파일로 분리 — import churn), `TranscriptOverlay.stories`의 `react-hooks/exhaustive-deps`(stories 전용 로직 경고, 포매팅 정리 범위 밖). 둘 다 빌드 무해.

### F. SuggestionGrid 동적화 — ✅ 완료 (§9 Phase 5+ 참조)
- **결과**: 양 플랫폼 ScreenIdle 기본 카드 셋이 morning/afternoon/evening/weekend 4종으로 분기.
  - voice-ui-kit: `ScreenIdle.tsx`의 `defaultCards(now)` — 요일(주말)·시각(`getHours()`)으로 결정. `DEFAULT_CARDS` 단일 상수 제거.
  - Android: `screens/ScreenIdle.kt`의 `suggestionsForContext(date, time)` — `DayOfWeek` 주말 + `LocalTime.hour`. `suggestions` 기본 인자로 연결.
  - 카드 타이틀은 양쪽 다 화면 파일에 영문 하드코딩(기존 `DEFAULT_*`와 동일 정책, i18n 대상 아님). `cards`/`suggestions` prop으로 여전히 override 가능.
- **남은 후속**: 앱별/사용자별 추천을 ConfigService(Android)·server fetch(voice-ui-kit)로 가져오는 외부 데이터 소싱 (§9 Phase 4의 `[~]` 항목).

### G. 테스트 추가 — ✅ 완료 (§9 Phase 5+ 참조)
- **결과**:
  - voice-ui-kit: Vitest + React Testing Library 인프라 신규(`vitest.config.ts` jsdom 환경 + `@` alias, `vitest.setup.ts`에서 `@testing-library/jest-dom` 매처 등록 + `afterEach(cleanup)`, `package.json`에 `test`/`test:watch` 스크립트). 컴포넌트 테스트 5파일 28케이스 — `StatusPill`(상태별 기본 라벨·`label`/`labels` 우선순위·latency 표시·도트 `aria-hidden`), `AppBar`(`autoLatency={false}` storybook-safe 경로·`History` aria-label·`historyLabel`/`statusLabels` 외부화·`noHistory`·latency 전달), `AskBar`(attach/mic/wave aria-label·listening 시 stop 라벨·탭 핸들러·`disabled`·controlled 입력 `onValueChange`/`onSubmit`), `Wordmark`/`IconBtn`(leaf 위생). `pnpm test` 28 passed, `eslint ./src` 0 errors 유지(테스트 파일도 prettier 통과).
  - Android: Compose UI Test 의존성 추가(`androidTestImplementation` junit/espresso/`ui-test-junit4` + `debugImplementation ui-test-manifest`, `defaultConfig.testInstrumentationRunner`). 계측 테스트 2파일 — `PipecatPreviewActivityTest`(5개 탭 렌더 + Idle 기본 화면 + Listening/Conversation 탭 전환 시 화면 콘텐츠 교체 검증, 무한 애니메이션 대응 `mainClock.autoAdvance=false`), `AskBarComposeTest`(content description·mic/stop 토글·`onMicTap`·`disabled`·controlled 입력 타이핑+IME submit). `./gradlew :app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL(JDK 17 — Gradle 8.2는 JDK 21 미지원).
- **남은 후속**: Android 계측 테스트는 실행에 기기/에뮬레이터 필요 — 본 작업에서는 컴파일 검증까지. voice-ui-kit는 leaf 컴포넌트 위주이며, `Conversation`/`PipecatLive` 템플릿·hooks(`usePipecatLatency`/`usePipecatTextInput`) 커버리지는 후속 후보.

### H. 가이드 파일 리네임 — ✅ 완료
- `gemini-ui-migration.md` → `pipecat-voice-ui-migration.md`로 리네임. 로컬 untracked 파일이라 git 영향 없음. 본문 내 자기참조(§11)도 갱신.

### 새 세션 시작 시 체크리스트

1. **상태 확인**:
   ```
   cd /Users/ds.ryu/project/voice-ui-kit && git log --oneline -5 main
   cd /Users/ds.ryu/project/mobile_pipecat_android && git log --oneline -5 master
   cd /Users/ds.ryu/project/pipecat && git status
   ```
2. **이 문서 §9 진행 현황** 훑어보고 가장 최근 ✅를 매핑
3. **§13 Quickstart로 현재 상태 시각적으로 확인** (선택 — `pnpm dev` 또는 `pipecat://preview`)
4. **§14에서 다음 후보 선택** (A~H 전부 완료. 남은 후보는 §10 "여전히 비범위" — 테스트 커버리지 확대, 패키지 분리 등)
5. **선택한 후보의 "시작점"** 따라가서 첫 파일/명령부터 진행
