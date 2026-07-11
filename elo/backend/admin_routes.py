"""
Elo — Admin API routes.
All endpoints under /api/admin/* require admin authentication.
"""
import json
import logging
from fastapi import APIRouter, Depends, HTTPException
from auth import require_admin
from database import (
    get_log_stats, get_logs,
    get_provider_configs, update_provider_config,
    list_personas, get_persona, create_persona, update_persona, delete_persona, activate_persona,
    list_api_keys, create_api_key, delete_api_key,
    get_all_settings, set_setting,
)
from persona import build_system_prompt
from sessions import list_sessions, delete_session, clear_sessions
from models import MessagesRequest

logger = logging.getLogger("elo.admin")

router = APIRouter(prefix="/api/admin", dependencies=[Depends(require_admin)])


# ━━━ Dashboard / Stats ━━━

@router.get("/stats")
async def stats():
    from core.orchestrator import Orchestrator
    return get_log_stats()


@router.get("/health")
async def admin_health():
    """Extended health with provider details."""
    from main import orchestrator
    providers = []
    for p in orchestrator.providers:
        available = await p.is_available()
        s = orchestrator.stats.get(p.name, {})
        providers.append({
            "name": p.name,
            "display_name": p.display_name,
            "available": available,
            "requests": s.get("requests", 0),
            "successes": s.get("successes", 0),
            "failures": s.get("failures", 0),
            "fallbacks_triggered": s.get("fallbacks_triggered", 0),
            "last_error": s.get("last_error"),
        })
    return {"providers": providers}


# ━━━ Providers ━━━

@router.get("/providers")
async def get_providers():
    configs = get_provider_configs()
    # Merge with runtime status
    from main import orchestrator
    for cfg in configs:
        for p in orchestrator.providers:
            if p.name == cfg["name"]:
                available = await p.is_available()
                runtime = orchestrator.stats.get(p.name, {})
                cfg["available"] = available
                cfg["requests"] = runtime.get("requests", 0)
                cfg["successes"] = runtime.get("successes", 0)
                cfg["failures"] = runtime.get("failures", 0)
                cfg["last_error"] = runtime.get("last_error")
                break
    return configs


@router.put("/providers/{name}")
async def update_provider(name: str, body: dict):
    allowed = {"is_enabled", "priority", "api_key", "base_url", "config_json"}
    filtered = {k: v for k, v in body.items() if k in allowed}
    if not filtered:
        raise HTTPException(400, "No valid fields to update")
    update_provider_config(name, **filtered)
    return {"message": f"Provider {name} updated"}


@router.post("/providers/{name}/test")
async def test_provider(name: str):
    from main import orchestrator
    for p in orchestrator.providers:
        if p.name == name:
            try:
                available = await p.is_available()
                if not available:
                    return {"success": False, "error": "Provider not available"}
                req = MessagesRequest(
                    model="claude-sonnet-4-6",
                    messages=[{"role": "user", "content": "Responda apenas: OK"}],
                    max_tokens=10,
                )
                resp = await p.complete(req)
                return {"success": True, "text": resp.text[:100], "provider": resp.provider}
            except Exception as e:
                return {"success": False, "error": str(e)[:200]}
    raise HTTPException(404, f"Provider {name} not found")


# ━━━ Models ━━━

@router.get("/models")
async def get_models():
    from config import MODEL_MAX_OUTPUT, MODEL_CLI_ALIAS, MODEL_TIMEOUT, DEEPSEEK_MODEL_MAP
    models = []
    for model_id, max_tokens in MODEL_MAX_OUTPUT.items():
        alias = MODEL_CLI_ALIAS.get(model_id, "")
        timeout = MODEL_TIMEOUT.get(alias, 600)
        ds_model = DEEPSEEK_MODEL_MAP.get(model_id, "")
        models.append({
            "id": model_id,
            "alias": alias,
            "max_tokens": max_tokens,
            "timeout": timeout,
            "deepseek_map": ds_model,
        })
    return models


# ━━━ Personas ━━━

@router.get("/personas")
async def get_personas():
    return list_personas()


@router.post("/personas")
async def new_persona(body: dict):
    return create_persona(body)


@router.get("/personas/{pid}")
async def get_one_persona(pid: int):
    p = get_persona(pid)
    if not p:
        raise HTTPException(404, "Persona not found")
    return p


@router.put("/personas/{pid}")
async def edit_persona(pid: int, body: dict):
    p = update_persona(pid, body)
    if not p:
        raise HTTPException(404, "Persona not found")
    return p


@router.delete("/personas/{pid}")
async def remove_persona(pid: int):
    if not delete_persona(pid):
        raise HTTPException(404, "Persona not found")
    return {"message": "Persona deleted"}


@router.post("/personas/{pid}/activate")
async def set_default_persona(pid: int):
    p = get_persona(pid)
    if not p:
        raise HTTPException(404, "Persona not found")
    activate_persona(pid)
    return {"message": f"Persona '{p['name']}' activated as default"}


@router.post("/personas/{pid}/preview")
async def preview_persona(pid: int):
    p = get_persona(pid)
    if not p:
        raise HTTPException(404, "Persona not found")
    prompt = build_system_prompt(p)
    return {"system_prompt": prompt}


# ━━━ API Keys ━━━

@router.get("/keys")
async def get_keys():
    keys = list_api_keys()
    # Mask key values for security
    for k in keys:
        full = k["key"]
        k["key_masked"] = f"{full[:6]}...{full[-4:]}" if len(full) > 10 else "***"
        k["key_full"] = full  # Admin can see full key
    return keys


@router.post("/keys")
async def new_key(body: dict):
    key = create_api_key(
        label=body.get("label", ""),
        project=body.get("project", ""),
        rate_limit=body.get("rate_limit", 60),
    )
    return key


@router.delete("/keys/{kid}")
async def remove_key(kid: int):
    if not delete_api_key(kid):
        raise HTTPException(404, "API key not found")
    return {"message": "API key revoked"}


# ━━━ Sessions ━━━

@router.get("/sessions")
async def get_sessions():
    return list_sessions()


@router.delete("/sessions/{key}")
async def remove_session(key: str):
    if delete_session(key):
        return {"message": "Session deleted"}
    raise HTTPException(404, "Session not found")


@router.delete("/sessions")
async def clear_all():
    count = clear_sessions()
    return {"message": f"Cleared {count} sessions"}


# ━━━ Logs ━━━

@router.get("/logs")
async def get_request_logs(limit: int = 50, offset: int = 0,
                           provider: str = None, model: str = None, status: str = None):
    return get_logs(limit=limit, offset=offset, provider=provider, model=model, status=status)


# ━━━ Settings ━━━

@router.get("/settings")
async def get_settings():
    return get_all_settings()


@router.put("/settings")
async def update_settings(body: dict):
    for k, v in body.items():
        set_setting(k, str(v))
    return {"message": "Settings updated"}
