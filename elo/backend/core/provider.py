"""
Elo — Abstract provider interface.
All AI providers must implement this interface.
"""
from abc import ABC, abstractmethod
from typing import AsyncGenerator
from models import MessagesRequest, ProviderResponse


class AIProvider(ABC):
    """Base interface for all AI providers."""

    @property
    @abstractmethod
    def name(self) -> str:
        """Unique provider identifier (e.g., 'claude_code', 'deepseek')."""
        ...

    @property
    @abstractmethod
    def display_name(self) -> str:
        """Human-readable name for logging."""
        ...

    @abstractmethod
    async def is_available(self) -> bool:
        """Quick health check — can this provider accept requests right now?"""
        ...

    @abstractmethod
    async def complete(self, request: MessagesRequest) -> ProviderResponse:
        """Non-streaming completion. Returns full response."""
        ...

    @abstractmethod
    async def stream(self, request: MessagesRequest) -> AsyncGenerator[str, None]:
        """Streaming completion. Yields SSE-formatted strings."""
        ...

    def supports_model(self, model: str) -> bool:
        """Check if this provider supports the requested model."""
        return True
