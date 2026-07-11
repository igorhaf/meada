"""
Elo — Provider orchestrator with automatic fallback.
Tries providers in priority order, falling back on any failure.
"""
import logging
import time
import traceback
from typing import AsyncGenerator
from models import MessagesRequest, ProviderResponse
from core.provider import AIProvider

logger = logging.getLogger("elo.orchestrator")


class ProviderError(Exception):
    """Raised when a provider fails."""
    def __init__(self, provider: str, reason: str, original: Exception | None = None):
        self.provider = provider
        self.reason = reason
        self.original = original
        super().__init__(f"[{provider}] {reason}")


class Orchestrator:
    """
    Manages AI providers with automatic fallback.

    Usage:
        orchestrator = Orchestrator()
        orchestrator.register(ClaudeCodeProvider())
        orchestrator.register(DeepSeekProvider())

        # Non-streaming
        response = await orchestrator.complete(request)

        # Streaming
        async for chunk in orchestrator.stream(request):
            yield chunk
    """

    def __init__(self):
        self._providers: list[AIProvider] = []
        self._stats: dict[str, dict] = {}

    def register(self, provider: AIProvider) -> None:
        """Register a provider. Order of registration = priority order."""
        self._providers.append(provider)
        self._stats[provider.name] = {
            "requests": 0,
            "successes": 0,
            "failures": 0,
            "fallbacks_triggered": 0,
            "last_error": None,
            "last_error_time": None,
        }
        logger.info(f"Provider registered: {provider.display_name} (priority {len(self._providers)})")

    @property
    def providers(self) -> list[AIProvider]:
        return list(self._providers)

    @property
    def stats(self) -> dict:
        return dict(self._stats)

    async def complete(self, request: MessagesRequest) -> ProviderResponse:
        """
        Non-streaming completion with automatic fallback.
        Tries each provider in order until one succeeds.
        """
        if not self._providers:
            raise ProviderError("none", "No providers registered")

        errors = []

        for i, provider in enumerate(self._providers):
            is_fallback = i > 0
            self._stats[provider.name]["requests"] += 1

            if is_fallback:
                self._stats[provider.name]["fallbacks_triggered"] += 1
                logger.warning(
                    f"FALLBACK ACTIVATED: switching to {provider.display_name} "
                    f"(provider #{i+1}) after {len(errors)} failure(s)"
                )

            try:
                # Check availability
                if not await provider.is_available():
                    raise ProviderError(provider.name, "Provider not available")

                # Check model support
                if not provider.supports_model(request.model):
                    raise ProviderError(provider.name, f"Model {request.model} not supported")

                logger.info(f"Attempting {provider.display_name} for model={request.model} stream=false")
                start = time.monotonic()

                response = await provider.complete(request)

                elapsed = time.monotonic() - start
                self._stats[provider.name]["successes"] += 1

                logger.info(
                    f"SUCCESS via {provider.display_name} "
                    f"({elapsed:.2f}s, {response.usage.get('output_tokens', 0)} tokens)"
                )

                # Quota tracking (only for claude_code provider — others have own billing)
                if provider.name == "claude_code":
                    try:
                        from quota_tracker import get_tracker
                        tracker = get_tracker()
                        in_tok = response.usage.get("input_tokens", 0) or 0
                        out_tok = response.usage.get("output_tokens", 0) or 0
                        if tracker.looks_like_quota_response(response.text, in_tok, out_tok):
                            tracker.record_exhausted(response.text, request.model)
                        else:
                            tracker.record_call(in_tok, out_tok, request.model)
                    except Exception as qerr:
                        logger.warning(f"quota_tracker hook failed: {qerr}")

                if is_fallback:
                    logger.info(
                        f"Fallback chain: {' -> '.join(e.provider for e in errors)} -> {provider.name} (success)"
                    )

                return response

            except Exception as e:
                elapsed = time.monotonic() - (start if 'start' in dir() else time.monotonic())
                reason = str(e) if not isinstance(e, ProviderError) else e.reason
                self._stats[provider.name]["failures"] += 1
                self._stats[provider.name]["last_error"] = reason
                self._stats[provider.name]["last_error_time"] = time.time()

                error = ProviderError(provider.name, reason, e if not isinstance(e, ProviderError) else e.original)
                errors.append(error)

                logger.error(
                    f"FAILED {provider.display_name}: {reason} "
                    f"(attempt {i+1}/{len(self._providers)})",
                    exc_info=isinstance(e, Exception) and not isinstance(e, ProviderError),
                )

        # All providers failed
        error_summary = "; ".join(f"{e.provider}: {e.reason}" for e in errors)
        logger.critical(f"ALL PROVIDERS FAILED: {error_summary}")
        raise ProviderError("all", f"All {len(self._providers)} providers failed: {error_summary}")

    async def stream(self, request: MessagesRequest) -> AsyncGenerator[str, None]:
        """
        Streaming completion with automatic fallback.
        If a provider fails during streaming setup, falls back to next.
        Note: Once streaming starts successfully, mid-stream failures are not retried.
        """
        if not self._providers:
            yield f"event: error\ndata: {{\"error\": \"No providers registered\"}}\n\n"
            return

        errors = []

        for i, provider in enumerate(self._providers):
            is_fallback = i > 0
            self._stats[provider.name]["requests"] += 1

            if is_fallback:
                self._stats[provider.name]["fallbacks_triggered"] += 1
                logger.warning(
                    f"FALLBACK ACTIVATED (stream): switching to {provider.display_name} "
                    f"after {len(errors)} failure(s)"
                )

            try:
                if not await provider.is_available():
                    raise ProviderError(provider.name, "Provider not available")

                if not provider.supports_model(request.model):
                    raise ProviderError(provider.name, f"Model {request.model} not supported")

                logger.info(f"Attempting {provider.display_name} for model={request.model} stream=true")

                # Try to get the first chunk — if this fails, we can still fallback
                gen = provider.stream(request)
                first_chunk = await gen.__anext__()

                self._stats[provider.name]["successes"] += 1
                logger.info(f"Stream started via {provider.display_name}")

                # Yield first chunk and then rest of stream
                yield first_chunk
                async for chunk in gen:
                    yield chunk

                if is_fallback:
                    logger.info(f"Fallback stream completed via {provider.display_name}")
                return

            except StopAsyncIteration:
                # Empty stream is still success
                self._stats[provider.name]["successes"] += 1
                return

            except Exception as e:
                reason = str(e) if not isinstance(e, ProviderError) else e.reason
                self._stats[provider.name]["failures"] += 1
                self._stats[provider.name]["last_error"] = reason
                self._stats[provider.name]["last_error_time"] = time.time()

                errors.append(ProviderError(provider.name, reason, e if not isinstance(e, ProviderError) else None))
                logger.error(f"FAILED {provider.display_name} (stream): {reason}")

        # All failed
        error_summary = "; ".join(f"{e.provider}: {e.reason}" for e in errors)
        logger.critical(f"ALL PROVIDERS FAILED (stream): {error_summary}")
        yield f"event: error\ndata: {{\"error\": \"All providers failed: {error_summary}\"}}\n\n"
