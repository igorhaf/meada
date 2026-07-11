"""
Elo — Database layer (SQLite).
Manages schema, migrations, and query helpers for all tables.
"""
import sqlite3
import json
import time
import uuid
import logging
from pathlib import Path
from contextlib import contextmanager

logger = logging.getLogger("elo.database")

DB_PATH = Path(__file__).parent / "elo.db"


@contextmanager
def get_db():
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def run_migrations():
    """Create all tables if they don't exist."""
    with get_db() as db:
        # Personas (expand from single-row to multi)
        db.execute("""CREATE TABLE IF NOT EXISTS personas (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL DEFAULT 'Assistente',
            description TEXT NOT NULL DEFAULT '',
            is_active INTEGER NOT NULL DEFAULT 0,
            is_default INTEGER NOT NULL DEFAULT 0,
            tone TEXT NOT NULL DEFAULT 'friendly',
            response_length INTEGER NOT NULL DEFAULT 3,
            use_emojis TEXT NOT NULL DEFAULT 'few',
            customer_address TEXT NOT NULL DEFAULT 'voce',
            behaviors TEXT NOT NULL DEFAULT '[]',
            restrictions TEXT NOT NULL DEFAULT '[]',
            escalation_triggers TEXT NOT NULL DEFAULT '[]',
            greeting_message TEXT NOT NULL DEFAULT '',
            closing_message TEXT NOT NULL DEFAULT '',
            words_to_avoid TEXT NOT NULL DEFAULT '[]',
            custom_instructions TEXT NOT NULL DEFAULT '',
            created_at TEXT DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT DEFAULT CURRENT_TIMESTAMP
        )""")

        # Migrate old persona table if exists
        old_exists = db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='persona'").fetchone()
        new_empty = db.execute("SELECT COUNT(*) FROM personas").fetchone()[0] == 0
        if old_exists and new_empty:
            try:
                row = db.execute("SELECT * FROM persona WHERE id = 1").fetchone()
                if row:
                    data = dict(row)
                    data.pop("id", None)
                    data["is_default"] = 1
                    cols = ", ".join(data.keys())
                    placeholders = ", ".join("?" for _ in data)
                    db.execute(f"INSERT INTO personas ({cols}) VALUES ({placeholders})", list(data.values()))
                    logger.info("Migrated legacy persona to new personas table")
            except Exception as e:
                logger.warning(f"Legacy persona migration skipped: {e}")

        # API Keys
        db.execute("""CREATE TABLE IF NOT EXISTS api_keys (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            key TEXT UNIQUE NOT NULL,
            label TEXT DEFAULT '',
            project TEXT DEFAULT '',
            is_active INTEGER DEFAULT 1,
            rate_limit INTEGER DEFAULT 60,
            last_used_at TEXT,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT DEFAULT CURRENT_TIMESTAMP
        )""")

        # Ensure default API key exists
        default_key = db.execute("SELECT id FROM api_keys WHERE key = '123456789'").fetchone()
        if not default_key:
            db.execute("INSERT INTO api_keys (key, label, project) VALUES ('123456789', 'Default Key', 'system')")

        # Request Logs
        db.execute("""CREATE TABLE IF NOT EXISTS request_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            api_key_label TEXT DEFAULT '',
            provider TEXT NOT NULL,
            model TEXT NOT NULL,
            input_tokens INTEGER DEFAULT 0,
            output_tokens INTEGER DEFAULT 0,
            latency_ms INTEGER DEFAULT 0,
            status TEXT NOT NULL DEFAULT 'success',
            fallback_chain TEXT DEFAULT '[]',
            error_message TEXT DEFAULT '',
            session_key TEXT DEFAULT '',
            created_at TEXT DEFAULT CURRENT_TIMESTAMP
        )""")

        # Provider Configs
        db.execute("""CREATE TABLE IF NOT EXISTS provider_configs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE NOT NULL,
            display_name TEXT DEFAULT '',
            is_enabled INTEGER DEFAULT 1,
            priority INTEGER DEFAULT 0,
            api_key TEXT DEFAULT '',
            base_url TEXT DEFAULT '',
            config_json TEXT DEFAULT '{}',
            updated_at TEXT DEFAULT CURRENT_TIMESTAMP
        )""")

        # Seed default providers
        for name, display, priority in [("claude_code", "Claude Code CLI", 0), ("deepseek", "DeepSeek API", 1)]:
            exists = db.execute("SELECT id FROM provider_configs WHERE name = ?", (name,)).fetchone()
            if not exists:
                db.execute("INSERT INTO provider_configs (name, display_name, priority) VALUES (?, ?, ?)",
                           (name, display, priority))

        # Settings (key-value)
        db.execute("""CREATE TABLE IF NOT EXISTS settings (
            key TEXT PRIMARY KEY,
            value TEXT DEFAULT '',
            updated_at TEXT DEFAULT CURRENT_TIMESTAMP
        )""")

        # Seed default settings
        defaults = {
            "log_level": "INFO",
            "cors_origins": "*",
            "large_arg_threshold": "80000",
            "default_model": "claude-sonnet-4-6",
            "admin_password": "meada2024",
        }
        for k, v in defaults.items():
            exists = db.execute("SELECT key FROM settings WHERE key = ?", (k,)).fetchone()
            if not exists:
                db.execute("INSERT INTO settings (key, value) VALUES (?, ?)", (k, v))

    logger.info("Database migrations completed")


# ─── Query helpers ───

def get_setting(key: str, default: str = "") -> str:
    with get_db() as db:
        row = db.execute("SELECT value FROM settings WHERE key = ?", (key,)).fetchone()
        return row["value"] if row else default


def set_setting(key: str, value: str):
    with get_db() as db:
        db.execute("INSERT OR REPLACE INTO settings (key, value, updated_at) VALUES (?, ?, ?)",
                   (key, value, _now()))


def get_all_settings() -> dict:
    with get_db() as db:
        rows = db.execute("SELECT key, value FROM settings").fetchall()
        return {r["key"]: r["value"] for r in rows}


def log_request(provider: str, model: str, input_tokens: int, output_tokens: int,
                latency_ms: int, status: str, fallback_chain: list = None,
                error_message: str = "", api_key_label: str = "", session_key: str = ""):
    with get_db() as db:
        db.execute("""INSERT INTO request_logs
            (provider, model, input_tokens, output_tokens, latency_ms, status,
             fallback_chain, error_message, api_key_label, session_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (provider, model, input_tokens, output_tokens, latency_ms, status,
             json.dumps(fallback_chain or []), error_message, api_key_label, session_key))


def get_logs(limit: int = 50, offset: int = 0, provider: str = None,
             model: str = None, status: str = None) -> list:
    with get_db() as db:
        query = "SELECT * FROM request_logs WHERE 1=1"
        params = []
        if provider:
            query += " AND provider = ?"
            params.append(provider)
        if model:
            query += " AND model = ?"
            params.append(model)
        if status:
            query += " AND status = ?"
            params.append(status)
        query += " ORDER BY id DESC LIMIT ? OFFSET ?"
        params.extend([limit, offset])
        return [dict(r) for r in db.execute(query, params).fetchall()]


def get_log_stats() -> dict:
    with get_db() as db:
        total = db.execute("SELECT COUNT(*) FROM request_logs").fetchone()[0]
        today = db.execute("SELECT COUNT(*) FROM request_logs WHERE DATE(timestamp) = DATE('now')").fetchone()[0]
        errors = db.execute("SELECT COUNT(*) FROM request_logs WHERE status = 'error'").fetchone()[0]
        fallbacks = db.execute("SELECT COUNT(*) FROM request_logs WHERE status = 'fallback'").fetchone()[0]
        avg_latency = db.execute("SELECT AVG(latency_ms) FROM request_logs WHERE status = 'success'").fetchone()[0]
        tokens_today = db.execute("SELECT SUM(input_tokens + output_tokens) FROM request_logs WHERE DATE(timestamp) = DATE('now')").fetchone()[0]

        by_provider = {}
        for row in db.execute("SELECT provider, COUNT(*) as cnt, SUM(CASE WHEN status='success' THEN 1 ELSE 0 END) as ok FROM request_logs GROUP BY provider").fetchall():
            by_provider[row["provider"]] = {"total": row["cnt"], "success": row["ok"]}

        recent = [dict(r) for r in db.execute("SELECT * FROM request_logs ORDER BY id DESC LIMIT 5").fetchall()]

        return {
            "total_requests": total,
            "today": today,
            "errors": errors,
            "fallbacks": fallbacks,
            "fallback_rate": round(fallbacks / total * 100, 1) if total > 0 else 0,
            "avg_latency_ms": round(avg_latency or 0),
            "tokens_today": tokens_today or 0,
            "by_provider": by_provider,
            "recent": recent,
        }


# ─── API Keys ───

def validate_api_key(key: str) -> dict | None:
    with get_db() as db:
        row = db.execute("SELECT * FROM api_keys WHERE key = ? AND is_active = 1", (key,)).fetchone()
        if row:
            db.execute("UPDATE api_keys SET last_used_at = ? WHERE id = ?", (_now(), row["id"]))
            return dict(row)
        return None


def list_api_keys() -> list:
    with get_db() as db:
        return [dict(r) for r in db.execute("SELECT * FROM api_keys ORDER BY created_at DESC").fetchall()]


def create_api_key(label: str = "", project: str = "", rate_limit: int = 60) -> dict:
    key = f"mia_{uuid.uuid4().hex[:32]}"
    with get_db() as db:
        db.execute("INSERT INTO api_keys (key, label, project, rate_limit) VALUES (?, ?, ?, ?)",
                   (key, label, project, rate_limit))
        row = db.execute("SELECT * FROM api_keys WHERE key = ?", (key,)).fetchone()
        return dict(row)


def delete_api_key(key_id: int) -> bool:
    with get_db() as db:
        result = db.execute("DELETE FROM api_keys WHERE id = ?", (key_id,))
        return result.rowcount > 0


# ─── Provider Configs ───

def get_provider_configs() -> list:
    with get_db() as db:
        return [dict(r) for r in db.execute("SELECT * FROM provider_configs ORDER BY priority").fetchall()]


def update_provider_config(name: str, **kwargs) -> bool:
    with get_db() as db:
        sets = []
        vals = []
        for k, v in kwargs.items():
            if k in ("is_enabled", "priority", "api_key", "base_url", "config_json", "display_name"):
                sets.append(f"{k} = ?")
                vals.append(v)
        if not sets:
            return False
        sets.append("updated_at = ?")
        vals.append(_now())
        vals.append(name)
        db.execute(f"UPDATE provider_configs SET {', '.join(sets)} WHERE name = ?", vals)
        return True


# ─── Personas ───

def list_personas() -> list:
    with get_db() as db:
        rows = db.execute("SELECT * FROM personas ORDER BY is_default DESC, id").fetchall()
        return [_parse_persona_row(r) for r in rows]


def get_persona(persona_id: int) -> dict | None:
    with get_db() as db:
        row = db.execute("SELECT * FROM personas WHERE id = ?", (persona_id,)).fetchone()
        return _parse_persona_row(row) if row else None


def get_default_persona() -> dict | None:
    with get_db() as db:
        row = db.execute("SELECT * FROM personas WHERE is_default = 1 LIMIT 1").fetchone()
        if not row:
            row = db.execute("SELECT * FROM personas ORDER BY id LIMIT 1").fetchone()
        return _parse_persona_row(row) if row else None


def create_persona(data: dict) -> dict:
    with get_db() as db:
        _serialize_json_fields(data)
        cols = ", ".join(data.keys())
        placeholders = ", ".join("?" for _ in data)
        db.execute(f"INSERT INTO personas ({cols}) VALUES ({placeholders})", list(data.values()))
        row_id = db.execute("SELECT last_insert_rowid()").fetchone()[0]
        return get_persona(row_id)


def update_persona(persona_id: int, data: dict) -> dict | None:
    with get_db() as db:
        _serialize_json_fields(data)
        data["updated_at"] = _now()
        sets = ", ".join(f"{k} = ?" for k in data.keys())
        vals = list(data.values()) + [persona_id]
        db.execute(f"UPDATE personas SET {sets} WHERE id = ?", vals)
        return get_persona(persona_id)


def delete_persona(persona_id: int) -> bool:
    with get_db() as db:
        result = db.execute("DELETE FROM personas WHERE id = ?", (persona_id,))
        return result.rowcount > 0


def activate_persona(persona_id: int):
    with get_db() as db:
        db.execute("UPDATE personas SET is_default = 0")
        db.execute("UPDATE personas SET is_default = 1, is_active = 1 WHERE id = ?", (persona_id,))


JSON_FIELDS = ("behaviors", "restrictions", "escalation_triggers", "words_to_avoid")


def _serialize_json_fields(data: dict):
    for f in JSON_FIELDS:
        if f in data and isinstance(data[f], list):
            data[f] = json.dumps(data[f], ensure_ascii=False)


def _parse_persona_row(row) -> dict:
    if not row:
        return None
    d = dict(row)
    d["is_active"] = bool(d.get("is_active", 0))
    d["is_default"] = bool(d.get("is_default", 0))
    for f in JSON_FIELDS:
        raw = d.get(f, "[]")
        try:
            d[f] = json.loads(raw) if isinstance(raw, str) else raw
        except (json.JSONDecodeError, TypeError):
            d[f] = []
    return d


def _now() -> str:
    from datetime import datetime, timezone
    return datetime.now(timezone.utc).isoformat()


# Run migrations on import
run_migrations()
