"""Render a `CandidateSet` into a markdown section for the supervisor prompt.

See `app-concierge-agent-design.md` §5.2.
"""

from __future__ import annotations

from app_catalog.candidate_builder import CandidateSet

_HEADER = """
================================================================================
[사용자 폰에 설치된 앱 — 직접 실행 가능]
================================================================================
당신은 아래 앱들을 직접 실행해 사용자에게 더 빠른 도움을 줄 수 있습니다.
실행이 필요하다고 판단하면 각 앱 전용 도구 `open_<app_id>`(예: `open_tmap`,
`open_naver_map`)를 호출하세요. 도구의 파라미터 스키마(필수/선택)는 도구 정의에
포함돼 있으므로, 필요한 값이 발화에서 추출되지 않으면 도구를 호출하지 말고
한국어 1문장으로 사용자에게 되물어주세요.

각 `open_<app_id>` 도구는 `spoken_intro`(앱을 열기 직전에 사용자에게 말할
1문장 한국어 안내, 예: "네이버 지도로 강남역 길찾기 열어드릴게요.")를
필수로 받습니다. 도구 호출 직후 spoken_intro가 자동으로 음성으로 안내된 뒤
실제 앱 실행이 일어납니다.

다음 두 가지를 비교해 더 도움 되는 쪽을 선택하세요:
- (A) 텍스트로 답하거나 검색 도구로 조사한다.
- (B) 사용자의 폰에서 직접 앱을 연다.

**(B)를 선택해야 하는 신호**:
- 사용자가 "앱에서", "OO로 열어줘", "켜줘", "실행해줘" 같은 명령형
- 결과물 자체가 앱 안에서 바로 쓰일 때 (길찾기, 메시지 작성, 결제, 음악 재생)
- 답을 텍스트로 듣는 것보다 앱 화면이 훨씬 정확/풍부할 때 (지도, 시세, 차트)

**(A)를 선택해야 하는 신호**:
- 단순 정보 질의 ("강남역이 어디야?" → 텍스트로 충분)
- 비교/요약 질의 ("이 두 가지 차이 알려줘")
- 사용자가 "검색해줘", "찾아봐", "알려줘" 등 정보 동사를 명시
""".strip()


def render_candidates_section(candidates: CandidateSet) -> str:
    """Render the candidate set as a system-prompt section.

    Returns an empty string when there are no candidates so that the caller
    can simply skip appending in that case.
    """
    if not candidates.by_category:
        return ""

    lines: list[str] = [_HEADER, "", "### 카테고리별 사용 가능한 앱"]

    for category_id, apps in candidates.by_category.items():
        if not apps:
            continue
        lines.append("")
        # Use the first app's category attribute for the heading; falling back
        # to the dict key keeps the section coherent if data drifts.
        lines.append(f"#### {category_id}")
        for app in apps:
            tag = "내 폰" if app.source == "user_inventory" else "추천"
            use_when = " ".join(app.use_when.split())  # collapse whitespace
            line = f"- [{tag}] **{app.display_name}** (`{app.app_id}`) — {use_when}"
            lines.append(line)
            if app.examples:
                examples_str = ", ".join(f'"{ex}"' for ex in app.examples)
                lines.append(f"  - 예시: {examples_str}")

    return "\n".join(lines)
