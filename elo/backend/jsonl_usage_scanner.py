"""
Elo — JSONL Usage Scanner.

Reads ~/.claude/projects/**.jsonl (written by Claude CLI itself) and extracts
token usage. This is the SHARED source of truth: it sees consumption from
the orbit pipeline AND any other Claude CLI session (terminal, IDE, etc).

Why this matters:
  Anthropic does NOT expose a quota API. The local quota_tracker only saw
  calls that went through elo's orchestrator. The user's Max plan is
  shared across all CLI sessions, so the tracker underestimated usage.

Mechanism:
  - Caminha recursivamente em ~/.claude/projects
  - Filtra arquivos com mtime nas últimas N horas (default 5h, cobre janela)
  - Para cada arquivo: lê do último offset salvo, parseia linha-a-linha
  - Linhas tipo {"type":"assistant","message":{...,"usage":{...}}} extraem
  - Persiste offset+mtime em elo.db (tabela jsonl_cursors)
  - Idempotente: chamar scan_now() múltiplas vezes não duplica

Performance:
  ~13MB em 5h de uso intenso; full re-scan da janela ~200ms.
  Tail incremental ~10-50ms por tick.
"""
import asyncio
import json
import logging
import os
import sqlite3
import threading
import time
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Iterable, Optional

logger = logging.getLogger("elo.jsonl_scanner")

# bind-mount: docker-compose volume /home/igorhaf/.claude -> /opt/elo/.claude
CLAUDE_HOME = Path(os.environ.get("CLAUDE_HOME") or os.path.expanduser("~/.claude"))
PROJECTS_DIR = CLAUDE_HOME / "projects"

DB_PATH = Path(__file__).parent / "elo.db"

# Inactivity threshold to anchor a new cycle: if there's no usage event for
# more than this many seconds, the next event opens a new fixed 5h window.
ANCHOR_GAP_SEC = 5 * 3600  # 5h


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
    with _db() as db:
        db.execute("""CREATE TABLE IF NOT EXISTS jsonl_cursors (
            path TEXT PRIMARY KEY,
            offset INTEGER NOT NULL DEFAULT 0,
            mtime REAL NOT NULL DEFAULT 0,
            updated_at TEXT DEFAULT CURRENT_TIMESTAMP
        )""")
        db.execute("""CREATE TABLE IF NOT EXISTS jsonl_usage_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp TEXT NOT NULL,
            session_id TEXT,
            project TEXT,
            model TEXT,
            input_tokens INTEGER DEFAULT 0,
            output_tokens INTEGER DEFAULT 0,
            cache_read_input_tokens INTEGER DEFAULT 0,
            cache_creation_input_tokens INTEGER DEFAULT 0,
            request_id TEXT,
            UNIQUE(request_id, timestamp)
        )""")
        db.execute("CREATE INDEX IF NOT EXISTS idx_juse_ts ON jsonl_usage_events(timestamp)")
        db.execute("CREATE INDEX IF NOT EXISTS idx_juse_model ON jsonl_usage_events(model)")
    logger.info("jsonl_scanner: migrations completed")


@dataclass
class WindowSummary:
    """Aggregate of a single 5h anchored window."""
    cycle_start: datetime
    cycle_end: datetime
    input_tokens: int = 0
    output_tokens: int = 0
    cache_read_tokens: int = 0
    cache_creation_tokens: int = 0
    prompts: int = 0
    models: dict[str, int] = field(default_factory=dict)


class JsonlUsageScanner:
    """Scans ~/.claude/projects/**.jsonl and aggregates usage into anchored 5h windows."""

    _instance: Optional["JsonlUsageScanner"] = None
    _instance_lock = threading.Lock()

    def __new__(cls):
        if cls._instance is None:
            with cls._instance_lock:
                if cls._instance is None:
                    cls._instance = super().__new__(cls)
                    cls._instance._initialized = False
        return cls._instance

    def __init__(self):
        if self._initialized:
            return
        self._initialized = True
        self._scan_lock = threading.Lock()
        run_migrations()
        logger.info(f"jsonl_scanner: initialized, CLAUDE_HOME={CLAUDE_HOME}")

    # ────────────────────────────────────────────────────────────────────
    # Public API
    # ────────────────────────────────────────────────────────────────────

    def scan_now(self, window_sec: int = 18000) -> dict:
        """Synchronous scan of recent jsonl files. Returns small stats dict."""
        with self._scan_lock:
            return self._do_scan(window_sec)

    def current_window(self) -> WindowSummary:
        """Compute the anchored 5h window from accumulated events."""
        anchor = self._find_anchor()
        return self._window_from(anchor)

    # ────────────────────────────────────────────────────────────────────
    # Internals
    # ────────────────────────────────────────────────────────────────────

    def _do_scan(self, window_sec: int) -> dict:
        if not PROJECTS_DIR.exists():
            logger.warning(f"jsonl_scanner: PROJECTS_DIR not found: {PROJECTS_DIR}")
            return {"scanned_files": 0, "new_events": 0, "errors": 0}

        cutoff = time.time() - window_sec
        cursors = self._load_cursors()
        scanned_files = 0
        new_events = 0
        errors = 0

        for jsonl in self._iter_recent_files(cutoff):
            scanned_files += 1
            try:
                events = self._tail_parse(jsonl, cursors.get(str(jsonl), (0, 0.0)))
                if events:
                    new_events += self._persist_events(events)
                # Cursor updated inside _tail_parse via return value
            except Exception:
                errors += 1
                logger.exception(f"jsonl_scanner: error reading {jsonl}")

        return {
            "scanned_files": scanned_files,
            "new_events": new_events,
            "errors": errors,
            "window_sec": window_sec,
        }

    def _iter_recent_files(self, cutoff_ts: float) -> Iterable[Path]:
        try:
            for sub in PROJECTS_DIR.iterdir():
                if not sub.is_dir():
                    continue
                for jsonl in sub.glob("*.jsonl"):
                    try:
                        st = jsonl.stat()
                        if st.st_mtime >= cutoff_ts:
                            yield jsonl
                    except OSError:
                        continue
        except OSError as e:
            logger.warning(f"jsonl_scanner: iter failed: {e}")

    def _tail_parse(self, path: Path, cursor: tuple[int, float]) -> list[dict]:
        """Read from saved offset to EOF, but stop at last complete line.
        Returns list of usage event dicts. Updates cursor in DB on success.
        """
        saved_offset, saved_mtime = cursor
        try:
            st = path.stat()
        except OSError:
            return []

        # If file was truncated/rotated (size < saved_offset), restart
        start_offset = saved_offset
        if st.st_size < saved_offset:
            start_offset = 0

        if st.st_size == start_offset:
            # No new bytes
            self._save_cursor(path, start_offset, st.st_mtime)
            return []

        with path.open("rb") as f:
            f.seek(start_offset)
            chunk = f.read(st.st_size - start_offset)

        # Only process up to last newline (avoid partial line)
        last_nl = chunk.rfind(b"\n")
        if last_nl < 0:
            # No complete line yet
            return []
        processed = chunk[: last_nl + 1]
        new_offset = start_offset + len(processed)

        events: list[dict] = []
        project_label = path.parent.name
        for raw_line in processed.splitlines():
            line = raw_line.decode("utf-8", errors="replace").strip()
            if not line or not line.startswith("{"):
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            evt = self._extract_usage(obj, project_label)
            if evt:
                events.append(evt)

        self._save_cursor(path, new_offset, st.st_mtime)
        return events

    @staticmethod
    def _extract_usage(obj: dict, project_label: str) -> Optional[dict]:
        # Claude CLI assistant message format:
        # {"type":"assistant","message":{"model":"...","usage":{...}}, "timestamp":"...","sessionId":"...","requestId":"..."}
        if obj.get("type") != "assistant":
            return None
        msg = obj.get("message") or {}
        usage = msg.get("usage") or {}
        if not usage:
            return None
        return {
            "timestamp": obj.get("timestamp") or msg.get("created_at") or "",
            "session_id": obj.get("sessionId") or msg.get("id") or "",
            "project": project_label,
            "model": msg.get("model") or "",
            "input_tokens": int(usage.get("input_tokens", 0) or 0),
            "output_tokens": int(usage.get("output_tokens", 0) or 0),
            "cache_read_input_tokens": int(usage.get("cache_read_input_tokens", 0) or 0),
            "cache_creation_input_tokens": int(usage.get("cache_creation_input_tokens", 0) or 0),
            "request_id": obj.get("requestId") or "",
        }

    # ────────────────────────────────────────────────────────────────────
    # DB helpers
    # ────────────────────────────────────────────────────────────────────

    def _load_cursors(self) -> dict[str, tuple[int, float]]:
        with _db() as db:
            rows = db.execute("SELECT path, offset, mtime FROM jsonl_cursors").fetchall()
            return {r["path"]: (int(r["offset"]), float(r["mtime"])) for r in rows}

    def _save_cursor(self, path: Path, offset: int, mtime: float):
        try:
            with _db() as db:
                db.execute(
                    "INSERT OR REPLACE INTO jsonl_cursors (path, offset, mtime, updated_at) "
                    "VALUES (?, ?, ?, datetime('now'))",
                    (str(path), int(offset), float(mtime)),
                )
        except Exception:
            logger.warning(f"jsonl_scanner: save cursor failed for {path}")

    def _persist_events(self, events: list[dict]) -> int:
        if not events:
            return 0
        inserted = 0
        with _db() as db:
            for e in events:
                try:
                    db.execute(
                        "INSERT OR IGNORE INTO jsonl_usage_events (timestamp, session_id, "
                        "project, model, input_tokens, output_tokens, "
                        "cache_read_input_tokens, cache_creation_input_tokens, request_id) "
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        (
                            e["timestamp"], e["session_id"], e["project"], e["model"],
                            e["input_tokens"], e["output_tokens"],
                            e["cache_read_input_tokens"], e["cache_creation_input_tokens"],
                            e["request_id"],
                        ),
                    )
                    if db.total_changes:
                        inserted += 1
                except sqlite3.IntegrityError:
                    pass
        return inserted

    # ────────────────────────────────────────────────────────────────────
    # Window aggregation (anchored, not rolling)
    # ────────────────────────────────────────────────────────────────────

    def _find_anchor(self) -> Optional[datetime]:
        """Find the start of the current anchored 5h window.

        Algorithm: walk events ordered ascending. The anchor moves forward
        every time we see a gap > ANCHOR_GAP_SEC (new window starts).
        After the walk, if the last anchor is already > 5h old, we fall back
        to the FIRST event within the last 5h — that's the effective window
        for sustained activity that crossed the natural 5h boundary.
        """
        now = datetime.now(timezone.utc)
        cutoff = now - timedelta(seconds=ANCHOR_GAP_SEC * 2)
        with _db() as db:
            rows = db.execute(
                "SELECT timestamp FROM jsonl_usage_events WHERE timestamp >= ? "
                "ORDER BY timestamp ASC",
                (cutoff.isoformat(),),
            ).fetchall()
        if not rows:
            return None

        parsed: list[datetime] = []
        for r in rows:
            try:
                parsed.append(datetime.fromisoformat(r["timestamp"].replace("Z", "+00:00")))
            except Exception:
                continue
        if not parsed:
            return None

        # Walk: anchor = first event after each gap > ANCHOR_GAP_SEC
        anchor: Optional[datetime] = None
        prev: Optional[datetime] = None
        for ts in parsed:
            if prev is None or (ts - prev).total_seconds() > ANCHOR_GAP_SEC:
                anchor = ts
            prev = ts

        # If the anchored window has expired (anchor older than 5h), the
        # subscription has effectively rolled into a new window even though
        # we never observed a gap. Anchor to the oldest event still within
        # the live 5h window.
        if anchor and (now - anchor).total_seconds() > ANCHOR_GAP_SEC:
            live_cutoff = now - timedelta(seconds=ANCHOR_GAP_SEC)
            in_window = [ts for ts in parsed if ts >= live_cutoff]
            anchor = in_window[0] if in_window else None
        return anchor

    def _window_from(self, anchor: Optional[datetime]) -> WindowSummary:
        """Aggregate all events between anchor and anchor+5h."""
        if anchor is None:
            now = datetime.now(timezone.utc)
            return WindowSummary(cycle_start=now, cycle_end=now + timedelta(seconds=ANCHOR_GAP_SEC))
        end = anchor + timedelta(seconds=ANCHOR_GAP_SEC)
        with _db() as db:
            rows = db.execute(
                "SELECT model, input_tokens, output_tokens, cache_read_input_tokens, "
                "cache_creation_input_tokens FROM jsonl_usage_events "
                "WHERE timestamp >= ? AND timestamp < ?",
                (anchor.isoformat(), end.isoformat()),
            ).fetchall()
        ws = WindowSummary(cycle_start=anchor, cycle_end=end)
        for r in rows:
            ws.prompts += 1
            ws.input_tokens += int(r["input_tokens"] or 0)
            ws.output_tokens += int(r["output_tokens"] or 0)
            ws.cache_read_tokens += int(r["cache_read_input_tokens"] or 0)
            ws.cache_creation_tokens += int(r["cache_creation_input_tokens"] or 0)
            m = r["model"] or "unknown"
            ws.models[m] = ws.models.get(m, 0) + 1
        return ws


def get_scanner() -> JsonlUsageScanner:
    return JsonlUsageScanner()


# ────────────────────────────────────────────────────────────────────
# Async background loop (called from main.py lifespan)
# ────────────────────────────────────────────────────────────────────

async def scanner_loop(interval_sec: int = 60, shutdown_event: Optional[asyncio.Event] = None):
    """Periodic scanner. Cheap (~10-50ms per tick after warm cache)."""
    scanner = get_scanner()
    logger.info(f"jsonl_scanner: loop starting (interval={interval_sec}s)")
    # Prime cache on startup with a full window scan
    try:
        stats = scanner.scan_now()
        logger.info(f"jsonl_scanner: initial scan {stats}")
    except Exception:
        logger.exception("jsonl_scanner: initial scan failed")

    while True:
        try:
            if shutdown_event and shutdown_event.is_set():
                logger.info("jsonl_scanner: shutdown")
                return
            try:
                if shutdown_event:
                    await asyncio.wait_for(shutdown_event.wait(), timeout=interval_sec)
                else:
                    await asyncio.sleep(interval_sec)
            except asyncio.TimeoutError:
                pass
            scanner.scan_now()
        except asyncio.CancelledError:
            return
        except Exception:
            logger.exception("jsonl_scanner: loop error, retrying")
            await asyncio.sleep(10)
