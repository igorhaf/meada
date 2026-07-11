"""
Elo API — Central AI orchestrator for the Meada ecosystem.

Architecture:
  - Primary provider: Claude Code CLI (cost-effective via subscription)
  - Fallback 1: DeepSeek API
  - Future: OpenAI, Gemini, etc.

Providers are tried in order. On any failure, the next provider takes over
transparently — consumers never need to know which provider responded.
"""
import asyncio
import logging
import json
import time
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Depends, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse, JSONResponse, FileResponse
from pydantic import BaseModel

from config import UNSUPPORTED_PARAMS
from models import MessagesRequest, SimpleChatRequest
from auth import require_auth
from sessions import list_sessions, delete_session, clear_sessions
from persona import inject_persona_system
from middleware import RequestLogger
from admin_routes import router as admin_router
import database  # triggers migrations on import
import graphify_service
import quota_tracker  # noqa: F401 — triggers migrations + initializes singleton

from core.orchestrator import Orchestrator
from providers.claude_code import ClaudeCodeProvider
from providers.deepseek import DeepSeekProvider

# ─── Logging ───
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("elo")

# ─── Orchestrator ───
orchestrator = Orchestrator()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Register providers + start graphify worker + jsonl quota scanner."""
    orchestrator.register(ClaudeCodeProvider())
    orchestrator.register(DeepSeekProvider())
    logger.info(f"Elo started with {len(orchestrator.providers)} providers")

    graphify_task = asyncio.create_task(graphify_service.worker_loop())
    logger.info("Graphify worker scheduled")

    # v2.4 — periodic jsonl scanner (Claude CLI usage logs => fallback quota source)
    from jsonl_usage_scanner import scanner_loop
    scanner_shutdown = asyncio.Event()
    scanner_task = asyncio.create_task(scanner_loop(interval_sec=60, shutdown_event=scanner_shutdown))
    logger.info("JSONL usage scanner started (60s tick)")

    # v2.5.1 — prime Anthropic oauth usage cache (primary quota source)
    try:
        from anthropic_oauth_usage import fetch_usage
        await fetch_usage()
        logger.info("Anthropic /api/oauth/usage primed")
    except Exception as e:
        logger.warning(f"OAuth usage prefetch failed: {e}")

    try:
        yield
    finally:
        scanner_shutdown.set()
        graphify_task.cancel()
        for t in (graphify_task, scanner_task):
            try:
                await t
            except (asyncio.CancelledError, Exception):
                pass
        logger.info("Elo shutting down")


app = FastAPI(title="Elo API", version="2.0.0", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

# Mount admin routes
app.include_router(admin_router)


# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#  Messages API (Anthropic-compatible)
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@app.post("/v1/messages")
async def messages(request: MessagesRequest, key_info: dict = Depends(require_auth)):
    """Anthropic-compatible messages endpoint with automatic fallback."""
    request.system = inject_persona_system(request.system, key_info=key_info)
    ignored = [p for p in UNSUPPORTED_PARAMS if getattr(request, p, None) is not None]

    req_logger = RequestLogger()
    req_logger.start(
        model=request.model,
        api_key_label=key_info.get("label", ""),
        session_key=request.session_key or "",
    )

    if request.stream:
        async def generate():
            try:
                async for chunk in orchestrator.stream(request):
                    yield chunk
                req_logger.finish_success(orchestrator._providers[0].name if orchestrator._providers else "unknown")
            except Exception as e:
                req_logger.finish_error(str(e))
                yield f"event: error\ndata: {{\"error\": \"{str(e)[:200]}\"}}\n\n"
        return StreamingResponse(generate(), media_type="text/event-stream")

    try:
        response = await orchestrator.complete(request)
        req_logger.finish_success(
            response.provider,
            input_tokens=response.usage.get("input_tokens", 0),
            output_tokens=response.usage.get("output_tokens", 0),
        )
        include_thinking = request.thinking is not None
        return JSONResponse(response.to_api_response(include_thinking=include_thinking, ignored_params=ignored or None))
    except Exception as e:
        req_logger.finish_error(str(e))
        return JSONResponse({"error": str(e)}, status_code=502)


# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#  Simple Chat API
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@app.post("/api/chat")
async def chat(request: SimpleChatRequest, key_info: dict = Depends(require_auth)):
    """Simple chat endpoint with streaming SSE."""
    messages_req = MessagesRequest(
        model="claude-sonnet-4-6",
        messages=[{"role": "user", "content": request.message}],
        system=request.system_prompt,
        stream=True,
        session_key=request.session_id,
    )
    messages_req.system = inject_persona_system(messages_req.system, key_info=key_info)

    async def generate():
        async for chunk in orchestrator.stream(messages_req):
            if "text_delta" in chunk:
                try:
                    data_start = chunk.index("data: ") + 6
                    data_end = chunk.index("\n\n", data_start)
                    data = json.loads(chunk[data_start:data_end])
                    text = data.get("delta", {}).get("text", "")
                    if text:
                        yield f"event: chunk\ndata: {json.dumps({'text': text})}\n\n"
                except (ValueError, json.JSONDecodeError):
                    pass
            elif "message_stop" in chunk:
                yield f"event: done\ndata: {json.dumps({'session_id': request.session_id})}\n\n"
            elif "error" in chunk and "All providers" in chunk:
                yield f"event: error\ndata: {json.dumps({'text': 'Servico temporariamente indisponivel'})}\n\n"

    return StreamingResponse(generate(), media_type="text/event-stream")


# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#  Persona API (public read, auth write)
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@app.get("/api/persona")
async def get_persona():
    from database import get_default_persona
    persona = get_default_persona()
    return persona or {}


@app.put("/api/persona", dependencies=[Depends(require_auth)])
async def update_persona_endpoint(body: dict):
    from database import update_persona, get_default_persona
    persona = get_default_persona()
    if persona:
        return update_persona(persona["id"], body)
    return body


# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#  Session Management
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@app.get("/v1/sessions", dependencies=[Depends(require_auth)])
async def get_sessions():
    return list_sessions()


@app.delete("/v1/sessions/{session_key}", dependencies=[Depends(require_auth)])
async def remove_session(session_key: str):
    if delete_session(session_key):
        return {"message": f"Session {session_key} deleted"}
    return JSONResponse({"message": "Session not found"}, status_code=404)


@app.delete("/v1/sessions", dependencies=[Depends(require_auth)])
async def remove_all_sessions():
    count = clear_sessions()
    return {"message": f"Cleared {count} sessions"}


# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#  Health & Stats
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@app.get("/api/health")
async def health():
    providers = []
    for p in orchestrator.providers:
        available = await p.is_available()
        stats = orchestrator.stats.get(p.name, {})
        providers.append({
            "name": p.name,
            "display_name": p.display_name,
            "available": available,
            "requests": stats.get("requests", 0),
            "successes": stats.get("successes", 0),
            "failures": stats.get("failures", 0),
            "fallbacks_triggered": stats.get("fallbacks_triggered", 0),
            "last_error": stats.get("last_error"),
        })
    return {"status": "ok", "version": "2.0.0", "providers": providers}


# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#  Quota Tracking (Claude subscription window)
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@app.get("/api/quota/status", dependencies=[Depends(require_auth)])
async def quota_status():
    """Snapshot of current quota window.

    v2.5.1: now fetches Anthropic's official /api/oauth/usage endpoint
    (cached 60s) on every call. Returns the EXACT data Anthropic's own
    /usage screen surfaces, instead of a heuristic reconstruction.
    """
    from quota_tracker import get_tracker
    return await get_tracker().snapshot_async()


@app.post("/api/quota/probe", dependencies=[Depends(require_auth)])
async def quota_probe():
    """Send a minimal ping to Claude to verify quota availability.

    Costs ~5 tokens. Use sparingly — prefer /api/quota/status for cheap polling.
    """
    from quota_tracker import get_tracker
    from models import MessagesRequest as MR
    tracker = get_tracker()
    try:
        req = MR(
            model="claude-sonnet-4-6",
            max_tokens=10,
            messages=[{"role": "user", "content": "ping"}],
            stream=False,
        )
        response = await orchestrator.complete(req)
        in_tok = response.usage.get("input_tokens", 0) or 0
        out_tok = response.usage.get("output_tokens", 0) or 0
        if tracker.looks_like_quota_response(response.text, in_tok, out_tok):
            resets_at = tracker.record_exhausted(response.text, req.model)
            return {"available": False, "reason": "quota_exhausted", "resets_at": resets_at,
                    "raw": response.text[:200]}
        # Successful response — clear any stale exhausted state
        tracker.record_available()
        return {"available": True, "reason": "ok", "resets_at": None,
                "raw": (response.text or "")[:200]}
    except Exception as e:
        logger.exception("quota_probe error")
        return {"available": False, "reason": "unreachable", "resets_at": None,
                "raw": f"{type(e).__name__}: {str(e)[:200]}"}


@app.get("/api/quota/history", dependencies=[Depends(require_auth)])
async def quota_history(limit: int = 50):
    """Recent quota events for charts."""
    from quota_tracker import get_tracker
    return {"events": get_tracker().recent_cycles(limit)}


@app.post("/api/quota/scan-now", dependencies=[Depends(require_auth)])
async def quota_scan_now():
    """Force a synchronous scan of ~/.claude/projects jsonl files.
    Useful after manual CLI activity to refresh the shared quota view."""
    from jsonl_usage_scanner import get_scanner
    from quota_tracker import get_tracker
    stats = get_scanner().scan_now()
    return {"scan": stats, "snapshot": get_tracker().snapshot()}


@app.post("/api/quota/plan", dependencies=[Depends(require_auth)])
async def quota_plan(payload: dict):
    """Estimate a Deep Pipeline run's cost against the current quota window.

    Body: { project_meta: {n_files, n_domains?, n_epics?},
            mode: 'aggressive'|'balanced'|'conservative',
            profile?: {...} }

    Returns: { mode, fits, recommendation, estimate, remaining, budget,
               suggested_profile?, suggested_mode?, reason }
    """
    from quota_tracker import get_tracker
    from pipeline_planner import plan, plan_to_dict

    payload = payload or {}
    project_meta = payload.get("project_meta") or {}
    mode = (payload.get("mode") or "balanced").lower()
    profile = payload.get("profile")

    snap = get_tracker().snapshot()
    remaining_in = int(snap.get("tokens_remaining", {}).get("input", 0))
    remaining_out = int(snap.get("tokens_remaining", {}).get("output", 0))

    result = plan(project_meta, remaining_in, remaining_out, mode=mode, profile=profile)
    return {
        **plan_to_dict(result),
        "quota_source": snap.get("source"),
        "cycle_resets_at": snap.get("cycle_resets_at"),
        "time_remaining_sec": snap.get("time_remaining_sec"),
    }


@app.post("/api/quota/tier", dependencies=[Depends(require_auth)])
async def quota_set_tier(payload: dict):
    """Change active plan tier (pro | max_5x | max_20x)."""
    from quota_tracker import get_tracker
    tier = (payload or {}).get("tier", "").lower()
    ok = get_tracker().set_tier(tier)
    if not ok:
        raise HTTPException(status_code=400, detail="Invalid tier. Use: pro | max_5x | max_20x")
    return get_tracker().snapshot()


# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#  Graphify API
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

class GraphifyRequest(BaseModel):
    folder_path: str
    project_id: str | None = None
    options: dict | None = None


@app.post("/api/graphify")
async def graphify_enqueue(req: GraphifyRequest, key_info: dict = Depends(require_auth)):
    try:
        job_id = await graphify_service.enqueue(req.folder_path, req.project_id, req.options)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    return {"job_id": job_id, "status": "queued"}


@app.get("/api/graphify/jobs")
async def graphify_list(
    project_id: str | None = Query(None),
    limit: int = Query(50, ge=1, le=200),
    key_info: dict = Depends(require_auth),
):
    return {"jobs": await graphify_service.list_jobs(project_id, limit)}


@app.get("/api/graphify/{job_id}")
async def graphify_status(job_id: str, key_info: dict = Depends(require_auth)):
    job = await graphify_service.get_status(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="job nao encontrado")
    return job


@app.delete("/api/graphify/{job_id}")
async def graphify_delete(job_id: str, key_info: dict = Depends(require_auth)):
    ok = await graphify_service.delete_job(job_id)
    if not ok:
        raise HTTPException(status_code=404, detail="job nao encontrado")
    return {"deleted": job_id}


def _graphify_file(job_id: str, filename: str) -> Path:
    out = graphify_service.output_dir(job_id) / filename
    if not out.exists():
        raise HTTPException(status_code=404, detail=f"{filename} ainda nao gerado")
    return out


@app.get("/api/graphify/{job_id}/html")
async def graphify_html(job_id: str, key_info: dict = Depends(require_auth)):
    return FileResponse(_graphify_file(job_id, "graph.html"), media_type="text/html")


@app.get("/api/graphify/{job_id}/graph.json")
async def graphify_graph_json(job_id: str, key_info: dict = Depends(require_auth)):
    return FileResponse(_graphify_file(job_id, "graph.json"), media_type="application/json")


@app.get("/api/graphify/{job_id}/report.md")
async def graphify_report(job_id: str, key_info: dict = Depends(require_auth)):
    return FileResponse(_graphify_file(job_id, "GRAPH_REPORT.md"), media_type="text/markdown")
