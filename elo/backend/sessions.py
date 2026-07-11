"""
Elo — Session management.
"""
import uuid
from models import MessageParam

_sessions: dict[str, str] = {}


def conversation_key(messages: list[MessageParam]) -> str:
    for msg in messages:
        if msg.role == "user":
            content = msg.content if isinstance(msg.content, str) else str(msg.content)
            return str(hash(content[:200]))
    return str(uuid.uuid4())


def get_session(key: str) -> str | None:
    return _sessions.get(key)


def set_session(key: str, session_id: str) -> None:
    _sessions[key] = session_id


def delete_session(key: str) -> bool:
    return _sessions.pop(key, None) is not None


def clear_sessions() -> int:
    count = len(_sessions)
    _sessions.clear()
    return count


def list_sessions() -> dict:
    return {"sessions": dict(_sessions), "count": len(_sessions)}
