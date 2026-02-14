#!/usr/bin/env python3
"""
会话入口 -> SSE 终态收敛端到端压测脚本。

目标指标：
1) 提交成功率（POST /api/v3/chat/messages）
2) 终态收敛成功率（answer.final + stream.completed）
3) 终态收敛时延（从提交开始到 stream.completed）
4) SSE 重连率/重连次数（连接中断后按 Last-Event-ID 重连）

示例：
python3 scripts/chat_e2e_perf.py \
  --base-url http://127.0.0.1:8091 \
  --user-id perf-user \
  --requests 20 \
  --concurrency 5
"""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import random
import statistics
import string
import sys
import threading
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen


@dataclass
class RunResult:
    index: int
    ok: bool
    submit_ok: bool
    stream_ok: bool
    session_id: Optional[int]
    plan_id: Optional[int]
    turn_id: Optional[int]
    submit_latency_ms: float
    converge_latency_ms: Optional[float]
    first_event_latency_ms: Optional[float]
    reconnects: int
    answer_final_seen: bool
    stream_completed_seen: bool
    error: Optional[str]


def percentile(values: List[float], p: float) -> Optional[float]:
    if not values:
        return None
    if p <= 0:
        return min(values)
    if p >= 100:
        return max(values)
    ordered = sorted(values)
    rank = (len(ordered) - 1) * p / 100.0
    low = int(rank)
    high = min(low + 1, len(ordered) - 1)
    weight = rank - low
    return ordered[low] * (1 - weight) + ordered[high] * weight


def parse_api_data(body: str) -> Dict:
    payload = json.loads(body)
    if isinstance(payload, dict) and "data" in payload and isinstance(payload["data"], dict):
        return payload["data"]
    if isinstance(payload, dict):
        return payload
    raise ValueError("unexpected response body")


def login_token(base_url: str, username: str, password: str, timeout_sec: float) -> str:
    endpoint = f"{base_url.rstrip('/')}/api/auth/login"
    req = Request(
        endpoint,
        data=json.dumps({"username": username, "password": password}).encode("utf-8"),
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    with urlopen(req, timeout=timeout_sec) as resp:
        body = resp.read().decode("utf-8")
    payload = json.loads(body)
    code = str(payload.get("code") or "")
    if code and code != "0000":
        raise ValueError(f"auth failed: code={code}, info={payload.get('info')}")
    data = parse_api_data(body)
    token = str(data.get("token") or "").strip()
    if not token:
        raise ValueError("auth token missing from /api/auth/login response")
    return token


def resolve_plan_id_from_history(
    base_url: str,
    session_id: int,
    turn_id: Optional[int],
    timeout_sec: float,
    auth_token: str = "",
) -> int:
    endpoint = f"{base_url.rstrip('/')}/api/v3/chat/sessions/{session_id}/history"
    deadline = time.perf_counter() + max(timeout_sec, 1.0)
    while time.perf_counter() <= deadline:
        headers = {"Accept": "application/json"}
        if auth_token:
            headers["Authorization"] = f"Bearer {auth_token}"
        req = Request(endpoint, headers=headers, method="GET")
        with urlopen(req, timeout=max(timeout_sec, 1.0)) as resp:
            body = resp.read().decode("utf-8")
        data = parse_api_data(body)
        turns = data.get("turns") if isinstance(data, dict) else None
        if isinstance(turns, list) and turn_id is not None:
            for turn in turns:
                if not isinstance(turn, dict):
                    continue
                if int(turn.get("turnId") or 0) != turn_id:
                    continue
                plan_id = int(turn.get("planId") or 0)
                if plan_id > 0:
                    return plan_id
        latest_plan_id = int((data.get("latestPlanId") if isinstance(data, dict) else 0) or 0)
        if latest_plan_id > 0:
            return latest_plan_id
        time.sleep(0.2)
    return 0


def post_chat_message(
    base_url: str,
    user_id: str,
    message: str,
    timeout_sec: float,
    auth_token: str = "",
) -> Tuple[int, int, Optional[int]]:
    endpoint = f"{base_url.rstrip('/')}/api/v3/chat/messages"
    request_body = {
        "clientMessageId": f"perf-{int(time.time() * 1000)}-{random.randint(1000, 9999)}",
        "userId": user_id,
        "message": message,
        "scenario": "CHAT_DEFAULT",
        "metaInfo": {
            "source": "chat-e2e-perf",
            "entry": "perf-script"
        }
    }
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if auth_token:
        headers["Authorization"] = f"Bearer {auth_token}"
    req = Request(
        endpoint,
        data=json.dumps(request_body).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    with urlopen(req, timeout=timeout_sec) as resp:
        body = resp.read().decode("utf-8")
    data = parse_api_data(body)
    session_id = int(data.get("sessionId") or 0)
    plan_id = int(data.get("planId") or 0)
    turn_id = data.get("turnId")
    turn_id = int(turn_id) if turn_id is not None else None
    if session_id > 0 and plan_id <= 0:
        plan_id = resolve_plan_id_from_history(base_url, session_id, turn_id, timeout_sec, auth_token)
    if session_id <= 0 or plan_id <= 0:
        raise ValueError(f"invalid response sessionId={session_id}, planId={plan_id}, raw={body}")
    return session_id, plan_id, turn_id


def iter_sse_events(stream: Iterable[bytes]):
    event_type = "message"
    event_id: Optional[str] = None
    data_lines: List[str] = []

    for raw in stream:
        line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
        if line == "":
            if data_lines or event_id is not None or event_type != "message":
                yield {
                    "event": event_type,
                    "id": event_id,
                    "data": "\n".join(data_lines),
                }
            event_type = "message"
            event_id = None
            data_lines = []
            continue

        if line.startswith(":"):
            continue

        field, sep, value = line.partition(":")
        if not sep:
            continue
        if value.startswith(" "):
            value = value[1:]

        if field == "event":
            event_type = value or "message"
        elif field == "data":
            data_lines.append(value)
        elif field == "id":
            event_id = value


def stream_until_completed(
    base_url: str,
    session_id: int,
    plan_id: int,
    timeout_sec: float,
    max_reconnects: int,
    auth_token: str = "",
) -> Dict:
    start_ts = time.perf_counter()
    cursor = 0
    reconnects = 0
    first_event_latency_ms: Optional[float] = None
    answer_final_seen = False
    completed_seen = False
    received_events = 0

    while True:
        if time.perf_counter() - start_ts > timeout_sec:
            raise TimeoutError(f"stream timeout after {timeout_sec}s")
        if reconnects > max_reconnects:
            raise TimeoutError(f"exceeded max reconnects={max_reconnects}")

        endpoint = (
            f"{base_url.rstrip('/')}/api/v3/chat/sessions/{session_id}/stream"
            f"?planId={plan_id}&lastEventId={cursor}"
        )
        if auth_token:
            endpoint = f"{endpoint}&accessToken={quote(auth_token, safe='')}"
        req = Request(endpoint, headers={"Accept": "text/event-stream"}, method="GET")
        if cursor > 0:
            req.add_header("Last-Event-ID", str(cursor))

        try:
            with urlopen(req, timeout=timeout_sec) as resp:
                for event in iter_sse_events(resp):
                    event_name = str(event.get("event") or "")
                    event_id = str(event.get("id") or "").strip()
                    if event_id.isdigit():
                        cursor = max(cursor, int(event_id))

                    if event_name != "stream.heartbeat":
                        received_events += 1
                        if first_event_latency_ms is None:
                            first_event_latency_ms = (time.perf_counter() - start_ts) * 1000.0

                    if event_name == "answer.final":
                        answer_final_seen = True
                    elif event_name == "stream.completed":
                        completed_seen = True
                        converge_latency_ms = (time.perf_counter() - start_ts) * 1000.0
                        return {
                            "answer_final_seen": answer_final_seen,
                            "stream_completed_seen": completed_seen,
                            "converge_latency_ms": converge_latency_ms,
                            "first_event_latency_ms": first_event_latency_ms,
                            "reconnects": reconnects,
                            "received_events": received_events,
                            "cursor": cursor,
                        }

            reconnects += 1
        except (HTTPError, URLError, TimeoutError, ConnectionError):
            reconnects += 1
            time.sleep(min(0.4 * reconnects, 1.5))


def run_one(index: int, args) -> RunResult:
    seed = ''.join(random.choices(string.ascii_lowercase + string.digits, k=6))
    message = args.message_template.format(index=index, seed=seed)

    submit_begin = time.perf_counter()
    session_id = None
    plan_id = None
    turn_id = None

    try:
        session_id, plan_id, turn_id = post_chat_message(
            args.base_url,
            args.user_id,
            message,
            args.http_timeout_sec,
            args.auth_token,
        )
        submit_latency_ms = (time.perf_counter() - submit_begin) * 1000.0
    except Exception as ex:
        return RunResult(
            index=index,
            ok=False,
            submit_ok=False,
            stream_ok=False,
            session_id=session_id,
            plan_id=plan_id,
            turn_id=turn_id,
            submit_latency_ms=(time.perf_counter() - submit_begin) * 1000.0,
            converge_latency_ms=None,
            first_event_latency_ms=None,
            reconnects=0,
            answer_final_seen=False,
            stream_completed_seen=False,
            error=f"submit failed: {type(ex).__name__}: {ex}",
        )

    try:
        stream_state = stream_until_completed(
            args.base_url,
            session_id,
            plan_id,
            args.stream_timeout_sec,
            args.max_reconnects,
            args.auth_token,
        )
        answer_final_seen = bool(stream_state["answer_final_seen"])
        completed_seen = bool(stream_state["stream_completed_seen"])
        stream_ok = answer_final_seen and completed_seen
        return RunResult(
            index=index,
            ok=stream_ok,
            submit_ok=True,
            stream_ok=stream_ok,
            session_id=session_id,
            plan_id=plan_id,
            turn_id=turn_id,
            submit_latency_ms=submit_latency_ms,
            converge_latency_ms=float(stream_state["converge_latency_ms"]),
            first_event_latency_ms=stream_state["first_event_latency_ms"],
            reconnects=int(stream_state["reconnects"]),
            answer_final_seen=answer_final_seen,
            stream_completed_seen=completed_seen,
            error=None if stream_ok else "missing answer.final or stream.completed",
        )
    except Exception as ex:
        return RunResult(
            index=index,
            ok=False,
            submit_ok=True,
            stream_ok=False,
            session_id=session_id,
            plan_id=plan_id,
            turn_id=turn_id,
            submit_latency_ms=submit_latency_ms,
            converge_latency_ms=None,
            first_event_latency_ms=None,
            reconnects=args.max_reconnects,
            answer_final_seen=False,
            stream_completed_seen=False,
            error=f"stream failed: {type(ex).__name__}: {ex}",
        )


def build_summary(results: List[RunResult], elapsed_sec: float, args) -> Dict:
    total = len(results)
    submit_ok = sum(1 for item in results if item.submit_ok)
    stream_ok = sum(1 for item in results if item.stream_ok)
    fully_ok = sum(1 for item in results if item.ok)
    reconnect_runs = [item for item in results if item.reconnects > 0]

    submit_latencies = [item.submit_latency_ms for item in results if item.submit_ok]
    converge_latencies = [item.converge_latency_ms for item in results if item.converge_latency_ms is not None]
    first_event_latencies = [item.first_event_latency_ms for item in results if item.first_event_latency_ms is not None]

    return {
        "params": {
            "baseUrl": args.base_url,
            "userId": args.user_id,
            "requests": args.requests,
            "concurrency": args.concurrency,
            "streamTimeoutSec": args.stream_timeout_sec,
            "maxReconnects": args.max_reconnects,
        },
        "elapsedSec": elapsed_sec,
        "totals": {
            "total": total,
            "submitOk": submit_ok,
            "streamOk": stream_ok,
            "fullyOk": fully_ok,
            "failed": total - fully_ok,
        },
        "rates": {
            "submitOkRate": round(submit_ok / total, 4) if total else 0,
            "streamOkRate": round(stream_ok / total, 4) if total else 0,
            "fullyOkRate": round(fully_ok / total, 4) if total else 0,
            "reconnectRunRate": round(len(reconnect_runs) / total, 4) if total else 0,
        },
        "reconnect": {
            "totalReconnects": sum(item.reconnects for item in results),
            "avgReconnectsPerRun": round(statistics.mean([item.reconnects for item in results]), 3) if results else 0,
            "runsWithReconnect": len(reconnect_runs),
        },
        "latencyMs": {
            "submitAvg": round(statistics.mean(submit_latencies), 2) if submit_latencies else None,
            "submitP95": round(percentile(submit_latencies, 95), 2) if submit_latencies else None,
            "convergeAvg": round(statistics.mean(converge_latencies), 2) if converge_latencies else None,
            "convergeP95": round(percentile(converge_latencies, 95), 2) if converge_latencies else None,
            "firstEventAvg": round(statistics.mean(first_event_latencies), 2) if first_event_latencies else None,
            "firstEventP95": round(percentile(first_event_latencies, 95), 2) if first_event_latencies else None,
        },
    }


def print_progress(done: int, total: int, result: RunResult):
    badge = "OK" if result.ok else "FAIL"
    converge = "-" if result.converge_latency_ms is None else f"{result.converge_latency_ms:.0f}ms"
    print(
        f"[{done:>3}/{total}] {badge} "
        f"submit={result.submit_latency_ms:.0f}ms "
        f"converge={converge} "
        f"reconnects={result.reconnects} "
        f"plan={result.plan_id}"
    )


def parse_args():
    parser = argparse.ArgumentParser(description="会话 -> SSE 终态收敛压测脚本")
    parser.add_argument("--base-url", default="http://127.0.0.1:8091", help="后端地址")
    parser.add_argument("--user-id", default="perf-user", help="压测用户 ID")
    parser.add_argument("--requests", type=int, default=20, help="总请求数")
    parser.add_argument("--concurrency", type=int, default=5, help="并发数")
    parser.add_argument("--http-timeout-sec", type=float, default=20, help="提交接口超时")
    parser.add_argument("--stream-timeout-sec", type=float, default=180, help="单次 SSE 等待终态超时")
    parser.add_argument("--max-reconnects", type=int, default=6, help="SSE 最大重连次数")
    parser.add_argument("--auth-username", default="", help="本地登录用户名（留空则不登录）")
    parser.add_argument("--auth-password", default="", help="本地登录密码（留空则不登录）")
    parser.add_argument(
        "--message-template",
        default="请生成一段用于性能回归测试的商品文案，编号#{index}-{seed}",
        help="消息模板，可用 {index}/{seed}",
    )
    parser.add_argument("--budget-file", default="", help="SLO 预算文件（JSON）")
    parser.add_argument("--min-submit-ok-rate", type=float, default=0.98, help="提交成功率下限（0~1）")
    parser.add_argument("--min-stream-ok-rate", type=float, default=0.95, help="终态收敛成功率下限（0~1）")
    parser.add_argument("--max-converge-p95-ms", type=float, default=120000, help="终态收敛 P95 上限（毫秒）")
    parser.add_argument("--max-reconnect-run-rate", type=float, default=0.50, help="发生重连请求占比上限（0~1）")
    parser.add_argument(
        "--output",
        default="",
        help="输出结果 JSON 文件路径（默认 scripts/output/perf-chat-e2e-<timestamp>.json）",
    )
    return parser.parse_args()


def load_budget(args) -> Dict[str, float]:
    budget = {
        "minSubmitOkRate": float(args.min_submit_ok_rate),
        "minStreamOkRate": float(args.min_stream_ok_rate),
        "maxConvergeP95Ms": float(args.max_converge_p95_ms),
        "maxReconnectRunRate": float(args.max_reconnect_run_rate),
    }
    if not args.budget_file:
        return budget
    path = Path(args.budget_file)
    if not path.exists():
        raise FileNotFoundError(f"budget file not found: {path}")
    payload = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(payload, dict):
        budget.update({k: float(v) for k, v in payload.items() if isinstance(v, (int, float))})
    return budget


def evaluate_budget(summary: Dict, budget: Dict[str, float]) -> List[str]:
    violations: List[str] = []
    rates = summary.get("rates", {}) if isinstance(summary, dict) else {}
    latency = summary.get("latencyMs", {}) if isinstance(summary, dict) else {}

    submit_ok_rate = float(rates.get("submitOkRate") or 0.0)
    stream_ok_rate = float(rates.get("streamOkRate") or 0.0)
    reconnect_run_rate = float(rates.get("reconnectRunRate") or 0.0)
    converge_p95 = latency.get("convergeP95")
    converge_p95_value = float(converge_p95) if converge_p95 is not None else float("inf")

    if submit_ok_rate < budget["minSubmitOkRate"]:
        violations.append(
            f"submitOkRate={submit_ok_rate:.4f} < minSubmitOkRate={budget['minSubmitOkRate']:.4f}"
        )
    if stream_ok_rate < budget["minStreamOkRate"]:
        violations.append(
            f"streamOkRate={stream_ok_rate:.4f} < minStreamOkRate={budget['minStreamOkRate']:.4f}"
        )
    if converge_p95_value > budget["maxConvergeP95Ms"]:
        violations.append(
            f"convergeP95={converge_p95_value:.2f}ms > maxConvergeP95Ms={budget['maxConvergeP95Ms']:.2f}ms"
        )
    if reconnect_run_rate > budget["maxReconnectRunRate"]:
        violations.append(
            f"reconnectRunRate={reconnect_run_rate:.4f} > maxReconnectRunRate={budget['maxReconnectRunRate']:.4f}"
        )
    return violations


def main():
    args = parse_args()
    args.requests = max(1, args.requests)
    args.concurrency = max(1, min(args.concurrency, args.requests))
    args.auth_token = ""
    budget = load_budget(args)

    if args.auth_username or args.auth_password:
        if not args.auth_username or not args.auth_password:
            raise ValueError("auth 参数不完整：--auth-username 与 --auth-password 必须同时提供")
        args.auth_token = login_token(args.base_url, args.auth_username, args.auth_password, args.http_timeout_sec)
        print(f"[AUTH] 登录成功，token 已获取（user={args.auth_username}）")

    begin = time.perf_counter()
    results: List[RunResult] = []
    lock = threading.Lock()

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [executor.submit(run_one, idx + 1, args) for idx in range(args.requests)]
        done = 0
        for future in concurrent.futures.as_completed(futures):
            result = future.result()
            with lock:
                done += 1
                results.append(result)
                print_progress(done, args.requests, result)

    elapsed_sec = time.perf_counter() - begin
    summary = build_summary(results, elapsed_sec, args)
    violations = evaluate_budget(summary, budget)

    failed_runs = [asdict(item) for item in results if not item.ok]
    output_payload = {
        "summary": summary,
        "budget": budget,
        "budgetViolations": violations,
        "failedRuns": failed_runs,
        "runs": [asdict(item) for item in sorted(results, key=lambda it: it.index)],
    }

    output_path = Path(args.output) if args.output else Path("scripts/output") / f"perf-chat-e2e-{int(time.time())}.json"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(output_payload, ensure_ascii=False, indent=2), encoding="utf-8")

    print("\n=== PERF SUMMARY ===")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if violations:
        print("\n=== SLO CHECK: FAIL ===")
        for item in violations:
            print(f"- {item}")
    else:
        print("\n=== SLO CHECK: PASS ===")
    print(f"\n结果已写入: {output_path}")
    if violations:
        sys.exit(2)


if __name__ == "__main__":
    main()
