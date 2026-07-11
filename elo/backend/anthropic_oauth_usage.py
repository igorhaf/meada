"""
Elo — Anthropic official usage endpoint client.

Discovered in claude-code CLI binary (strings dump): the CLI authenticates
via OAuth tokens cached in ~/.claude/.credentials.json and queries
GET https://api.anthropic.com/api/oauth/usage to render the /usage screen.

Response shape (live sample):
{
  "five_hour":  {"utilization": 12.0, "resets_at": "2026-05-25T06:30:00+00:00"},
  "seven_day":  {"utilization": 25.0, "resets_at": "..."},
  "seven_day_sonnet": {"utilization": 30.0, "resets_at": "..."},
  "seven_day_opus":   null,
  "extra_usage": {"is_enabled": false, ...}
}

This is the GROUND TRUTH for the Max subscription window — supersedes the
heuristic jsonl-based reconstruction, which only sees calls that went
through the elo container (missing direct IDE/terminal usage from
other Claude apps that share the same subscription).

Caches the response for 60s to avoid hammering the endpoint.
"""
import json
import logging
import time
from pathlib import Path
from typing import Any, Optional

import httpx

logger = logging.getLogger("elo.oauth_usage")

CREDENTIALS_PATH = Path("/opt/elo/.claude/.credentials.json")
USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
ANTHROPIC_BETA = "oauth-2025-04-20"
CACHE_TTL_SEC = 60.0


_cache: dict[str, Any] = {"data": None, "fetched_at": 0.0, "error": None}


def _read_token() -> Optional[str]:
    try:
        d = json.loads(CREDENTIALS_PATH.read_text())
        return (d.get("claudeAiOauth") or {}).get("accessToken")
    except Exception as e:
        logger.warning(f"anthropic_oauth_usage: cannot read token: {e}")
        return None


def _read_tier() -> Optional[str]:
    try:
        d = json.loads(CREDENTIALS_PATH.read_text())
        return (d.get("claudeAiOauth") or {}).get("rateLimitTier")
    except Exception:
        return None


async def fetch_usage(force: bool = False) -> Optional[dict]:
    """Fetch /api/oauth/usage from Anthropic.

    Returns the parsed JSON (with our own metadata under `_meta`) or None
    on failure. Cached for CACHE_TTL_SEC unless force=True.
    """
    now = time.time()
    if not force and _cache["data"] is not None and (now - _cache["fetched_at"]) < CACHE_TTL_SEC:
        return _cache["data"]

    token = _read_token()
    if not token:
        _cache["error"] = "no_token"
        return None

    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(10.0, connect=5.0)) as client:
            resp = await client.get(
                USAGE_URL,
                headers={
                    "authorization": f"Bearer {token}",
                    "anthropic-beta": ANTHROPIC_BETA,
                },
            )
        if resp.status_code != 200:
            _cache["error"] = f"http_{resp.status_code}"
            logger.warning(f"anthropic_oauth_usage: HTTP {resp.status_code}: {resp.text[:200]}")
            return None
        data = resp.json()
        data["_meta"] = {
            "fetched_at": now,
            "tier": _read_tier(),
        }
        _cache["data"] = data
        _cache["fetched_at"] = now
        _cache["error"] = None
        return data
    except Exception as e:
        _cache["error"] = str(e)[:200]
        logger.warning(f"anthropic_oauth_usage: fetch failed: {e}")
        return None


def cached_usage() -> Optional[dict]:
    """Return the last successful response without refetching."""
    return _cache["data"]


def last_error() -> Optional[str]:
    return _cache["error"]
