"""
Elo — Authentication.
Validates API keys against database and admin password for panel access.
"""
from fastapi import Depends, HTTPException, Request
from fastapi.security import APIKeyHeader, HTTPBearer, HTTPAuthorizationCredentials
from database import validate_api_key, get_setting

_api_key_header = APIKeyHeader(name="x-api-key", auto_error=False)
_bearer = HTTPBearer(auto_error=False)


async def require_auth(
    api_key: str | None = Depends(_api_key_header),
    bearer: HTTPAuthorizationCredentials | None = Depends(_bearer),
):
    """Validate API key for /v1/messages and /api/chat endpoints."""
    token = api_key or (bearer.credentials if bearer else None)
    if not token:
        raise HTTPException(status_code=401, detail=_auth_error("Missing API key"))

    key_info = validate_api_key(token)
    if not key_info:
        raise HTTPException(status_code=401, detail=_auth_error("Invalid API key"))

    return key_info


async def require_admin(
    request: Request,
    api_key: str | None = Depends(_api_key_header),
    bearer: HTTPAuthorizationCredentials | None = Depends(_bearer),
):
    """Validate admin access for /api/admin/* endpoints."""
    token = api_key or (bearer.credentials if bearer else None)

    # Check admin password
    admin_pwd = get_setting("admin_password", "meada2024")
    if token == admin_pwd:
        return {"role": "admin"}

    # Also accept valid API keys (system keys)
    if token:
        key_info = validate_api_key(token)
        if key_info and key_info.get("project") == "system":
            return {"role": "admin", **key_info}

    raise HTTPException(status_code=403, detail={"error": "Admin access required"})


def _auth_error(msg: str) -> dict:
    return {
        "type": "error",
        "error": {
            "type": "authentication_error",
            "message": f"{msg}. Pass via 'x-api-key' header or 'Authorization: Bearer {{key}}'.",
        },
    }
