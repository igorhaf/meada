"""
Elo — Quota Tracker.

Tracks Claude subscription usage in a rolling 5h window. Detects quota
exhaustion by inspecting Claude CLI responses for known patterns.

Sources of truth (Anthropic does not expose an API):
- TylerGallenbeck/claude-code-limit-tracker README for tier limits
- _QUOTA_PATTERNS regex (mirrors backend/app/services/elo_pipeline.py)

This is a heuristic. The user's actual quota may differ if they consume
the same subscription from claude.ai, Desktop, or other tools.
"""
import json
import logging
import os
import re
import sqlite3
import threading
import time
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

logger = logging.getLogger("elo.quota")

DB_PATH = Path(__file__).parent / "elo.db"

# Mirrors backend/app/services/elo_pipeline.py:62 — keep in sync
_QUOTA_PATTERNS = re.compile(
    r"(hit your (usage )?limit|usage limit reached|rate limit|"
    r"resets? (at|in|on)|quota exceeded|you've reached your|"
    r"try again (later|in \d))",
    re.IGNORECASE,
)

# Heuristic limits per Claude subscription tier (5h anchored window).
# Values are estimates — Anthropic does not publish exact numbers. tokens_in/out
# are conservative based on community reverse-engineering. Cache tokens are
# tracked separately and do NOT count against the input cap (cache reads are
# cheap on Anthropic billing).
TIER_LIMITS = {
    "pro":      {"prompts_5h": 40,  "weekly_sonnet_h": 80,  "weekly_opus_h": 0,
                  "tokens_in_5h": 250_000,   "tokens_out_5h": 50_000},
    "max_5x":   {"prompts_5h": 200, "weekly_sonnet_h": 280, "weekly_opus_h": 35,
                  "tokens_in_5h": 1_200_000, "tokens_out_5h": 250_000},
    "max_20x":  {"prompts_5h": 800, "weekly_sonnet_h": 480, "weekly_opus_h": 40,
                  "tokens_in_5h": 5_000_000, "tokens_out_5h": 1_000_000},
}

CYCLE_DURATION_SEC = 5 * 3600  # 5 hours


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(dt: datetime) -> str:
    return dt.isoformat()


@contextmanager
def _db():
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def run_migrations():
    """Create quota tracking tables. Idempotent."""
    with _db() as db:
        db.execute("""CREATE TABLE IF NOT EXISTS quota_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp TEXT NOT NULL,
            event_type TEXT NOT NULL,
            input_tokens INTEGER DEFAULT 0,
            output_tokens INTEGER DEFAULT 0,
            model TEXT DEFAULT '',
            resets_at TEXT DEFAULT '',
            raw_text TEXT DEFAULT ''
        )""")
        db.execute("CREATE INDEX IF NOT EXISTS idx_quota_events_timestamp ON quota_events(timestamp)")
        db.execute("CREATE INDEX IF NOT EXISTS idx_quota_events_type ON quota_events(event_type)")
    logger.info("quota_tracker: migrations completed")


class QuotaTracker:
    """Singleton tracker for Claude subscription quota."""

    _instance: Optional["QuotaTracker"] = None
    _lock = threading.Lock()

    def __new__(cls):
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = super().__new__(cls)
                    cls._instance._initialized = False
        return cls._instance

    def __init__(self):
        if self._initialized:
            return
        self._initialized = True
        self._lock_state = threading.Lock()

        # Plan tier (from env, default max_5x)
        self.tier = os.environ.get("ELO_PLAN_TIER", "max_5x").lower()
        if self.tier not in TIER_LIMITS:
            logger.warning(f"Unknown tier '{self.tier}', falling back to max_5x")
            self.tier = "max_5x"

        # In-memory state — rebuild from DB on init
        self._exhausted = False
        self._resets_at_iso: Optional[str] = None  # parsed reset time from Claude's message
        self._raw_exhausted_msg: str = ""

        run_migrations()
        self._reload_recent_state()
        logger.info(
            f"quota_tracker: initialized tier={self.tier} "
            f"limits={TIER_LIMITS[self.tier]}"
        )

    def _reload_recent_state(self):
        """Check if the most recent quota_exhausted event is still active."""
        with _db() as db:
            row = db.execute(
                "SELECT * FROM quota_events WHERE event_type='exhausted' "
                "ORDER BY id DESC LIMIT 1"
            ).fetchone()
            if not row:
                return
            self._raw_exhausted_msg = row["raw_text"] or ""
            self._resets_at_iso = row["resets_at"] or None
            # Consider still exhausted if event recorded less than 5h ago AND
            # parsed reset time has not passed.
            event_ts = datetime.fromisoformat(row["timestamp"])
            age = (_now() - event_ts).total_seconds()
            if age < CYCLE_DURATION_SEC:
                self._exhausted = True

    # ────────────────────────────────────────────────────────────────────
    # Event recording
    # ────────────────────────────────────────────────────────────────────

    def record_call(self, input_tokens: int = 0, output_tokens: int = 0,
                    model: str = "") -> None:
        """Record a successful Claude call (consumes from current cycle)."""
        try:
            with _db() as db:
                db.execute(
                    "INSERT INTO quota_events (timestamp, event_type, input_tokens, "
                    "output_tokens, model) VALUES (?, ?, ?, ?, ?)",
                    (_iso(_now()), "call", input_tokens or 0, output_tokens or 0, model or ""),
                )
        except Exception as e:
            logger.warning(f"quota_tracker.record_call failed: {e}")

    def record_exhausted(self, raw_text: str, model: str = "") -> Optional[str]:
        """Record quota-exhausted response. Returns parsed resets_at string or None."""
        resets_at = self._parse_resets_at(raw_text)
        try:
            with _db() as db:
                db.execute(
                    "INSERT INTO quota_events (timestamp, event_type, model, "
                    "resets_at, raw_text) VALUES (?, ?, ?, ?, ?)",
                    (_iso(_now()), "exhausted", model or "", resets_at or "", raw_text[:500]),
                )
            with self._lock_state:
                self._exhausted = True
                self._resets_at_iso = resets_at
                self._raw_exhausted_msg = raw_text[:500]
            logger.warning(f"quota_tracker: EXHAUSTED detected, resets_at={resets_at}")
        except Exception as e:
            logger.warning(f"quota_tracker.record_exhausted failed: {e}")
        return resets_at

    def record_available(self) -> None:
        """Record that quota came back (e.g. after a successful probe post-exhaustion)."""
        with self._lock_state:
            was_exhausted = self._exhausted
            self._exhausted = False
            self._resets_at_iso = None
            self._raw_exhausted_msg = ""
        if was_exhausted:
            try:
                with _db() as db:
                    db.execute(
                        "INSERT INTO quota_events (timestamp, event_type) VALUES (?, ?)",
                        (_iso(_now()), "available"),
                    )
                logger.info("quota_tracker: quota AVAILABLE recorded")
            except Exception as e:
                logger.warning(f"quota_tracker.record_available failed: {e}")

    # ────────────────────────────────────────────────────────────────────
    # Detection helpers
    # ────────────────────────────────────────────────────────────────────

    @staticmethod
    def looks_like_quota_response(raw_text: str, input_tokens: int = 0,
                                  output_tokens: int = 0) -> bool:
        """Detect a Claude CLI response that signals quota exhaustion."""
        if not raw_text or len(raw_text) > 500:
            return False
        if input_tokens > 0 or output_tokens > 0:
            return False
        return bool(_QUOTA_PATTERNS.search(raw_text))

    @staticmethod
    def _parse_resets_at(raw_text: str) -> Optional[str]:
        """Extract 'resets at 5:10am (UTC)' style timestamp from Claude's message."""
        if not raw_text:
            return None
        m = re.search(
            r"resets?\s+(?:at\s+)?(\d{1,2}(?::\d{2})?\s*[ap]m\s*\([A-Z]+\))",
            raw_text, re.IGNORECASE,
        )
        return m.group(1) if m else None

    # ────────────────────────────────────────────────────────────────────
    # Snapshot / queries
    # ────────────────────────────────────────────────────────────────────

    def _cycle_window(self) -> tuple[datetime, datetime]:
        """Rolling 5h cycle: from (now-5h) to now."""
        end = _now()
        start = datetime.fromtimestamp(end.timestamp() - CYCLE_DURATION_SEC, tz=timezone.utc)
        return start, end

    def snapshot(self) -> dict:
        """Return current quota state. Cheap — no Claude calls.

        Primary source: Anthropic's official /api/oauth/usage endpoint
        (cached for 60s). Falls back to the jsonl scanner if the endpoint
        is unreachable, and finally to the local quota_events table.

        Note: this method is synchronous. Live HTTP fetches happen from
        snapshot_async() (called by the FastAPI endpoint). When the cache
        is fresh, snapshot() uses it; otherwise it falls through to jsonl.
        """
        limits = TIER_LIMITS[self.tier]

        # PRIMARY: Anthropic official endpoint (cached)
        oauth_snapshot = None
        try:
            from anthropic_oauth_usage import cached_usage
            oauth_snapshot = cached_usage()
        except Exception as e:
            logger.warning(f"quota_tracker: oauth_usage cache read failed: {e}")

        now = _now()

        if oauth_snapshot and isinstance(oauth_snapshot.get("five_hour"), dict):
            return self._snapshot_from_oauth(oauth_snapshot, limits, now)

        # FALLBACK 1: jsonl scanner
        ws = None
        try:
            from jsonl_usage_scanner import get_scanner
            ws = get_scanner().current_window()
        except Exception as e:
            logger.warning(f"quota_tracker: jsonl scanner failed: {e}")

        if ws and ws.prompts > 0:
            cycle_start = ws.cycle_start
            cycle_end = ws.cycle_end
            prompts_used = ws.prompts
            in_tokens = ws.input_tokens
            out_tokens = ws.output_tokens
            cache_read = ws.cache_read_tokens
            cache_creation = ws.cache_creation_tokens
            source = "jsonl"
        else:
            # FALLBACK 2: rolling 5h on local quota_events
            cycle_start, cycle_end = self._cycle_window()
            with _db() as db:
                row = db.execute(
                    "SELECT COUNT(*) AS prompts, COALESCE(SUM(input_tokens), 0) AS in_tok, "
                    "COALESCE(SUM(output_tokens), 0) AS out_tok "
                    "FROM quota_events WHERE event_type='call' AND timestamp >= ?",
                    (_iso(cycle_start),),
                ).fetchone()
            prompts_used = row["prompts"] if row else 0
            in_tokens = row["in_tok"] if row else 0
            out_tokens = row["out_tok"] if row else 0
            cache_read = 0
            cache_creation = 0
            source = "local_fallback"

        # Derived metrics
        tokens_in_max = limits["tokens_in_5h"]
        tokens_out_max = limits["tokens_out_5h"]
        prompts_max = limits["prompts_5h"]
        prompts_pct = round(100.0 * prompts_used / prompts_max, 1) if prompts_max else 0.0
        tokens_in_pct = round(100.0 * in_tokens / tokens_in_max, 1) if tokens_in_max else 0.0
        tokens_out_pct = round(100.0 * out_tokens / tokens_out_max, 1) if tokens_out_max else 0.0
        # Worst-case pct (drives badge color)
        pct = max(prompts_pct, tokens_in_pct, tokens_out_pct)

        # Time window math
        cycle_duration = (cycle_end - cycle_start).total_seconds()
        elapsed = max(0.0, (now - cycle_start).total_seconds())
        time_elapsed_pct = round(100.0 * min(elapsed, cycle_duration) / cycle_duration, 1) if cycle_duration > 0 else 0.0
        time_remaining_sec = max(0, int(cycle_duration - elapsed))

        # If we have a stored "exhausted" event but the rolling window has moved
        # past its reset time, consider it cleared (will be confirmed by next probe).
        is_exhausted = self._exhausted
        if is_exhausted and self._resets_at_iso is None:
            with _db() as db:
                last_exh = db.execute(
                    "SELECT timestamp FROM quota_events WHERE event_type='exhausted' "
                    "ORDER BY id DESC LIMIT 1"
                ).fetchone()
                if last_exh:
                    age = (_now() - datetime.fromisoformat(last_exh["timestamp"])).total_seconds()
                    if age > 1800:
                        is_exhausted = False

        return {
            "tier": self.tier,
            "limits": limits,
            "source": source,  # 'jsonl' or 'local_fallback'
            "cycle_start": _iso(cycle_start),
            "cycle_end_estimate": _iso(cycle_end),
            "cycle_start_anchored": _iso(cycle_start) if source == "jsonl" else None,
            "cycle_resets_at": _iso(cycle_end) if source == "jsonl" else None,
            "time_elapsed_pct": time_elapsed_pct,
            "time_remaining_sec": time_remaining_sec,
            "prompts_used": prompts_used,
            "prompts_max": prompts_max,
            "prompts_pct": prompts_pct,
            "pct": pct,  # worst-case % (color driver)
            "tokens": {
                "input": in_tokens,
                "output": out_tokens,
                "cache_read": cache_read,
                "cache_creation": cache_creation,
                "total": in_tokens + out_tokens,
            },
            "tokens_limits": {
                "input_5h": tokens_in_max,
                "output_5h": tokens_out_max,
            },
            "tokens_remaining": {
                "input": max(0, tokens_in_max - in_tokens),
                "output": max(0, tokens_out_max - out_tokens),
            },
            "tokens_pct": {
                "input": tokens_in_pct,
                "output": tokens_out_pct,
            },
            # Legacy fields (preserved for v2.3.0 frontend compat)
            "tokens_input": in_tokens,
            "tokens_output": out_tokens,
            "tokens_total": in_tokens + out_tokens,
            "exhausted": is_exhausted,
            "resets_at": self._resets_at_iso,
            "exhausted_message": self._raw_exhausted_msg or None,
            "available": not is_exhausted,
        }

    async def snapshot_async(self) -> dict:
        """Same as snapshot() but proactively refreshes the OAuth usage cache."""
        try:
            from anthropic_oauth_usage import fetch_usage
            await fetch_usage()
        except Exception as e:
            logger.warning(f"snapshot_async: fetch_usage failed: {e}")
        return self.snapshot()

    def _snapshot_from_oauth(self, oauth: dict, limits: dict, now: datetime) -> dict:
        """Build a snapshot dict using Anthropic's official /api/oauth/usage data.

        oauth = {
          "five_hour": {"utilization": 12.0, "resets_at": "2026-05-25T06:30:00+00:00"},
          "seven_day": {"utilization": 25.0, "resets_at": "..."},
          "seven_day_sonnet": {"utilization": 30.0, "resets_at": "..."},
          ...
        }
        """
        five = oauth.get("five_hour") or {}
        seven = oauth.get("seven_day") or {}
        seven_sonnet = oauth.get("seven_day_sonnet") or {}
        seven_opus = oauth.get("seven_day_opus") or {}

        utilization = float(five.get("utilization") or 0.0)
        resets_at_iso = five.get("resets_at")

        # Reset is anchored by Anthropic. cycle_start = resets_at - 5h.
        cycle_end = None
        cycle_start = None
        time_remaining_sec = 0
        time_elapsed_pct = 0.0
        try:
            if resets_at_iso:
                cycle_end = datetime.fromisoformat(resets_at_iso.replace("Z", "+00:00"))
                cycle_start = datetime.fromtimestamp(
                    cycle_end.timestamp() - CYCLE_DURATION_SEC, tz=timezone.utc
                )
                time_remaining_sec = max(0, int((cycle_end - now).total_seconds()))
                elapsed = max(0.0, (now - cycle_start).total_seconds())
                time_elapsed_pct = round(100.0 * min(elapsed, CYCLE_DURATION_SEC) / CYCLE_DURATION_SEC, 1)
        except Exception:
            pass

        # OAuth endpoint gives utilization as a single percent (combined cap).
        # We don't have separate in/out token counters here; expose the percent
        # under tokens_pct.output as a conservative proxy (matches what the CLI
        # /usage screen surfaces).
        is_exhausted = utilization >= 100.0

        return {
            "tier": self.tier,
            "limits": limits,
            "source": "anthropic_oauth",
            "cycle_start": _iso(cycle_start) if cycle_start else _iso(now),
            "cycle_end_estimate": _iso(cycle_end) if cycle_end else _iso(now),
            "cycle_start_anchored": _iso(cycle_start) if cycle_start else None,
            "cycle_resets_at": _iso(cycle_end) if cycle_end else None,
            "time_elapsed_pct": time_elapsed_pct,
            "time_remaining_sec": time_remaining_sec,
            # 5h utilization (single % from Anthropic — split is unavailable)
            "utilization_pct": utilization,
            "prompts_used": 0,  # not exposed by oauth endpoint
            "prompts_max": limits.get("prompts_5h", 0),
            "prompts_pct": utilization,  # mirror so UI bars still render
            "pct": utilization,
            "tokens": {
                "input": 0,
                "output": 0,
                "cache_read": 0,
                "cache_creation": 0,
                "total": 0,
            },
            "tokens_limits": {
                "input_5h": limits.get("tokens_in_5h", 0),
                "output_5h": limits.get("tokens_out_5h", 0),
            },
            "tokens_remaining": {
                "input": int(limits.get("tokens_in_5h", 0) * (1 - utilization / 100.0)),
                "output": int(limits.get("tokens_out_5h", 0) * (1 - utilization / 100.0)),
            },
            "tokens_pct": {
                "input": utilization,
                "output": utilization,
            },
            "weekly": {
                "all": {
                    "utilization": float(seven.get("utilization") or 0.0),
                    "resets_at": seven.get("resets_at"),
                },
                "sonnet": {
                    "utilization": float(seven_sonnet.get("utilization") or 0.0),
                    "resets_at": seven_sonnet.get("resets_at"),
                },
                "opus": {
                    "utilization": float(seven_opus.get("utilization") or 0.0) if seven_opus else 0.0,
                    "resets_at": (seven_opus or {}).get("resets_at"),
                },
            },
            # Legacy fields preserved for v2.3/2.4 frontend compat
            "tokens_input": 0,
            "tokens_output": 0,
            "tokens_total": 0,
            "exhausted": is_exhausted,
            "resets_at": resets_at_iso,
            "exhausted_message": None,
            "available": not is_exhausted,
        }

    def recent_cycles(self, limit: int = 20) -> list[dict]:
        """Return recent quota events for history charts."""
        with _db() as db:
            rows = db.execute(
                "SELECT * FROM quota_events ORDER BY id DESC LIMIT ?",
                (limit,),
            ).fetchall()
        return [dict(r) for r in rows]

    def set_tier(self, tier: str) -> bool:
        """Change the active plan tier. Persisted via env-style setting in settings table."""
        tier = tier.lower()
        if tier not in TIER_LIMITS:
            return False
        with self._lock_state:
            self.tier = tier
        try:
            with _db() as db:
                db.execute(
                    "INSERT OR REPLACE INTO settings (key, value, updated_at) VALUES (?, ?, ?)",
                    ("elo_plan_tier", tier, _iso(_now())),
                )
        except Exception:
            pass
        return True


# Module-level accessor (lazy singleton)
def get_tracker() -> QuotaTracker:
    return QuotaTracker()
