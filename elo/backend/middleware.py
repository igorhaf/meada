"""
Elo — Request logging middleware.
Logs every AI request to the database for monitoring and analytics.
"""
import time
import logging
from database import log_request

logger = logging.getLogger("elo.middleware")


class RequestLogger:
    """Tracks request timing and logs to database on completion."""

    def __init__(self):
        self._start: float = 0
        self._provider: str = ""
        self._model: str = ""
        self._fallback_chain: list[str] = []
        self._api_key_label: str = ""
        self._session_key: str = ""

    def start(self, model: str = "", api_key_label: str = "", session_key: str = ""):
        self._start = time.monotonic()
        self._model = model
        self._api_key_label = api_key_label
        self._session_key = session_key
        self._fallback_chain = []

    def record_attempt(self, provider: str):
        self._fallback_chain.append(provider)

    def finish_success(self, provider: str, input_tokens: int = 0, output_tokens: int = 0):
        latency = int((time.monotonic() - self._start) * 1000) if self._start else 0
        status = "fallback" if len(self._fallback_chain) > 1 else "success"
        try:
            log_request(
                provider=provider,
                model=self._model,
                input_tokens=input_tokens,
                output_tokens=output_tokens,
                latency_ms=latency,
                status=status,
                fallback_chain=self._fallback_chain,
                api_key_label=self._api_key_label,
                session_key=self._session_key,
            )
        except Exception as e:
            logger.warning(f"Failed to log request: {e}")

    def finish_error(self, error_message: str):
        latency = int((time.monotonic() - self._start) * 1000) if self._start else 0
        try:
            log_request(
                provider=self._fallback_chain[-1] if self._fallback_chain else "unknown",
                model=self._model,
                input_tokens=0,
                output_tokens=0,
                latency_ms=latency,
                status="error",
                fallback_chain=self._fallback_chain,
                error_message=error_message[:500],
                api_key_label=self._api_key_label,
                session_key=self._session_key,
            )
        except Exception as e:
            logger.warning(f"Failed to log error: {e}")
