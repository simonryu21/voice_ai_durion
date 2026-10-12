"""mcp.json 연결 정보로 MCP 에이전트 1회 호출 (공용).

에이전트마다 스크립트를 만들 필요 없이, .cursor/mcp.json의 url/headers로
JSON-RPC tools/call을 호출합니다. 메일/일정/게시판 등 모든 invoke_* 도구에 사용 가능.

사용 예:
    # mcp.json 사용 (Cursor에서 쓰는 연결 정보 그대로)
    python tests/mcp/call_agent.py mail_agent "최근 메일 요약해줘"
    python tests/mcp/call_agent.py planner_agent "오늘 일정 알려줘"
    python tests/mcp/call_agent.py bbs_agent "게시판 최근 글 요약"

    # mcp.json 경로 지정
    set MCP_CONFIG_PATH=.cursor/mcp.json
    python tests/mcp/call_agent.py planner_agent "오늘 일정 알려줘"

    # mcp.json 없이 로그인 후 호출 (MCP_API_KEY, MCP_PASSWORD 필요)
    python tests/mcp/call_agent.py --login mail_agent "최근 메일 요약해줘"

    # 로그인 안 된 상태(401)에서도 MCP_API_KEY/MCP_PASSWORD 있으면 자동 로그인 후 재시도
"""
# ruff: noqa: S310  # urlopen: _allowed_url()로 http(s)만 허용 후 호출

from __future__ import annotations

import argparse
import contextlib
import json
import os
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent.parent
MCP_TOOL_PREFIX = "invoke_"


def _norm_tool_name(name: str) -> str:
    """mail_agent -> invoke_mail_agent, invoke_mail_agent -> invoke_mail_agent."""
    name = name.strip()
    if not name:
        return ""
    if name.startswith(MCP_TOOL_PREFIX):
        return name
    return f"{MCP_TOOL_PREFIX}{name}"


def _load_dotenv() -> None:
    try:
        from dotenv import load_dotenv

        for d in (PROJECT_ROOT, SCRIPT_DIR):
            p = d / ".env"
            if p.exists():
                load_dotenv(p)
                break
    except ImportError:
        pass


def _mcp_json_path() -> Path:
    p = os.environ.get("MCP_CONFIG_PATH", "").strip()
    if p:
        return Path(p) if Path(p).is_absolute() else PROJECT_ROOT / p
    return PROJECT_ROOT / ".cursor" / "mcp.json"


def _read_mcp_config(path: Path) -> tuple[str, dict[str, str]]:
    """mcp.json에서 호출 가능한 서버의 url과 headers 반환.

    `mcpServers`에는 stdio 기반 서버(예: postgres)처럼 `url`이 없는 항목도
    함께 들어갈 수 있으므로, `url`이 있는 서버를 찾아 사용합니다.
    """
    if not path.exists():
        raise SystemExit(
            f"mcp.json 없음: {path}. "
            "Cursor용 MCP 설정을 만들었거나, --login 과 MCP_API_KEY/MCP_PASSWORD 를 사용하세요."
        )
    raw = path.read_text(encoding="utf-8")
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as e:
        raise SystemExit(f"mcp.json 파싱 실패: {e}") from e

    servers = data.get("mcpServers") or {}
    if not servers:
        raise SystemExit("mcp.json에 mcpServers 항목이 없습니다.")

    chosen: dict | None = None
    # Prefer HTTP-based servers (Cursor uses type=streamableHttp for remote MCP).
    for server in servers.values():
        if isinstance(server, dict) and server.get("url"):
            chosen = server
            if server.get("type") in {"streamableHttp", "http", "https"}:
                break
    if not chosen:
        raise SystemExit("mcp.json에 url이 있는 MCP 서버가 없습니다.")

    url = (chosen.get("url") or "").rstrip("/")
    if not url:
        raise SystemExit("mcp.json에 url이 없습니다.")

    headers = dict(chosen.get("headers") or {})
    if "Content-Type" not in headers:
        headers["Content-Type"] = "application/json"

    return url, headers


def _allowed_url(url: str) -> bool:
    """http/https만 허용 (Ruff S310)."""
    return url.startswith("http://") or url.startswith("https://")


def _login(base_url: str) -> str:
    """POST /api/mcp/login 후 access_token 반환."""
    api_key = os.environ.get("MCP_API_KEY", "").strip()
    password = os.environ.get("MCP_PASSWORD", "").strip()
    if not api_key or not password:
        raise SystemExit("MCP_API_KEY, MCP_PASSWORD를 설정하세요 (tests/mcp/.env 또는 환경 변수).")
    url = f"{base_url.rstrip('/')}/api/mcp/login"
    if not _allowed_url(url):
        raise SystemExit(f"허용되지 않은 URL 스킴: {url}")
    body = json.dumps({"api_key": api_key, "password": password}).encode("utf-8")
    req = Request(url, data=body, method="POST", headers={"Content-Type": "application/json"})
    try:
        with urlopen(req, timeout=30) as r:
            data = json.loads(r.read().decode("utf-8"))
    except HTTPError as e:
        body = e.read().decode("utf-8") if e.fp else ""
        raise SystemExit(f"로그인 실패: {e.code} {body}") from e
    except URLError as e:
        raise SystemExit(f"연결 실패: {e.reason}") from e
    token = data.get("access_token")
    if not token:
        raise SystemExit("로그인 응답에 access_token 없음.")
    return token


def _call_json_rpc(url: str, headers: dict[str, str], tool_name: str, request_text: str) -> dict:
    """JSON-RPC tools/call 호출 후 응답 dict 반환. 401 시 HTTPError 발생."""
    if not _allowed_url(url):
        raise SystemExit(f"허용되지 않은 URL 스킴: {url}")
    body = json.dumps(
        {
            "jsonrpc": "2.0",
            "id": "1",
            "method": "tools/call",
            "params": {
                "name": tool_name,
                "arguments": {"subagent_request": request_text},
            },
        }
    ).encode("utf-8")
    req = Request(url, data=body, method="POST", headers=headers)
    with urlopen(req, timeout=120) as r:
        return json.loads(r.read().decode("utf-8"))


def _can_auto_login() -> bool:
    """MCP_API_KEY, MCP_PASSWORD가 있으면 자동 로그인 가능."""
    return bool(
        os.environ.get("MCP_API_KEY", "").strip() and os.environ.get("MCP_PASSWORD", "").strip()
    )


def main() -> int:
    _load_dotenv()
    parser = argparse.ArgumentParser(
        description="mcp.json 또는 로그인으로 MCP 에이전트 1회 호출",
    )
    parser.add_argument(
        "agent",
        help="에이전트 이름 (예: mail_agent, planner_agent) 또는 도구명 (invoke_mail_agent)",
    )
    parser.add_argument(
        "request",
        help="에이전트에 넘길 요청 문장 (예: 최근 메일 요약해줘)",
    )
    parser.add_argument(
        "--login",
        action="store_true",
        help="mcp.json 대신 MCP_API_KEY/MCP_PASSWORD로 로그인 후 호출",
    )
    parser.add_argument(
        "--config",
        default=None,
        help="mcp.json 경로 (기본: .cursor/mcp.json 또는 MCP_CONFIG_PATH)",
    )
    args = parser.parse_args()

    tool_name = _norm_tool_name(args.agent)
    if not tool_name:
        print("agent 이름을 입력하세요.", file=sys.stderr)
        return 1
    request_text = args.request.strip()

    if args.login:
        base_url = os.environ.get("MCP_BASE_URL", "http://localhost:8000").rstrip("/")
        token = _login(base_url)
        x_user_id = os.environ.get("MCP_X_USER_ID", "admin").strip() or "admin"
        url = f"{base_url}/api/json-rpc"
        headers = {
            "Authorization": f"Bearer {token}",
            "X-User-Id": x_user_id,
            "Content-Type": "application/json",
        }
    else:
        if args.config:
            path = Path(args.config)
            if not path.is_absolute():
                path = PROJECT_ROOT / path
        else:
            path = _mcp_json_path()
        url, headers = _read_mcp_config(path)
        if "json-rpc" not in url:
            url = f"{url.rstrip('/')}/api/json-rpc"

    try:
        out = _call_json_rpc(url, headers, tool_name, request_text)
    except HTTPError as e:
        if e.code == 401 and not args.login and _can_auto_login():
            base_url = url.replace("/api/json-rpc", "").rstrip("/") or os.environ.get(
                "MCP_BASE_URL", "http://localhost:8000"
            ).rstrip("/")
            token = _login(base_url)
            x_user_id = os.environ.get("MCP_X_USER_ID", "admin").strip() or "admin"
            url = f"{base_url}/api/json-rpc"
            headers = {
                "Authorization": f"Bearer {token}",
                "X-User-Id": x_user_id,
                "Content-Type": "application/json",
            }
            out = _call_json_rpc(url, headers, tool_name, request_text)
        else:
            body = e.read().decode("utf-8") if e.fp else ""
            raise SystemExit(f"도구 호출 실패: {e.code} {body}") from e
    except URLError as e:
        raise SystemExit(f"연결 실패: {e.reason}") from e

    if "error" in out:
        print(out["error"].get("message", out["error"]), file=sys.stderr)
        return 1

    result = out.get("result") or {}
    for c in result.get("content") or []:
        if c.get("type") == "text":
            text = c.get("text", "")
            # Windows 콘솔 cp949 이모지 오류 방지
            reconfigure = getattr(sys.stdout, "reconfigure", None)
            if (
                callable(reconfigure)
                and sys.stdout.encoding
                and sys.stdout.encoding.lower() not in ("utf-8", "utf8")
            ):
                with contextlib.suppress(OSError):
                    reconfigure(encoding="utf-8")
            print(text)
            break
    else:
        print("(응답 본문 없음)", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
