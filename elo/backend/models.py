"""
Elo — Pydantic models for request/response.
"""
from pydantic import BaseModel, Field
from typing import Optional
import uuid
import time


class ContentBlock(BaseModel):
    type: str
    text: Optional[str] = None
    source: Optional[dict] = None


class MessageParam(BaseModel):
    role: str
    content: str | list[ContentBlock]


class MessagesRequest(BaseModel):
    model: str = "claude-sonnet-4-6"
    max_tokens: int | None = None
    messages: list[MessageParam]
    system: str | list[dict] | None = None
    stream: bool = False
    temperature: float | None = None
    top_p: float | None = None
    top_k: int | None = None
    stop_sequences: list[str] | None = None
    metadata: dict | None = None
    tools: list[dict] | None = None
    tool_choice: dict | None = None
    thinking: dict | None = None
    cwd: str | None = None
    session_key: str | None = None


class SimpleChatRequest(BaseModel):
    message: str
    session_id: str | None = None
    system_prompt: str | None = None


class PersonaConfig(BaseModel):
    name: str = "Assistente"
    description: str = ""
    is_active: bool = False
    tone: str = "friendly"
    response_length: int = 3
    use_emojis: str = "few"
    customer_address: str = "voce"
    behaviors: list[str] = Field(default_factory=list)
    restrictions: list[str] = Field(default_factory=list)
    escalation_triggers: list[str] = Field(default_factory=list)
    greeting_message: str = ""
    closing_message: str = ""
    words_to_avoid: list[str] = Field(default_factory=list)
    custom_instructions: str = ""


# ─── Standardized provider response ───

class ProviderResponse:
    """Standardized response from any provider."""

    def __init__(
        self,
        text: str = "",
        model: str = "",
        provider: str = "",
        thinking_blocks: list[dict] | None = None,
        usage: dict | None = None,
        stop_reason: str = "end_turn",
        session_id: str | None = None,
    ):
        self.id = f"msg_{uuid.uuid4().hex[:24]}"
        self.text = text
        self.model = model
        self.provider = provider
        self.thinking_blocks = thinking_blocks or []
        self.usage = usage or {"input_tokens": 0, "output_tokens": 0}
        self.stop_reason = stop_reason
        self.session_id = session_id
        self.created_at = time.time()

    def to_api_response(self, include_thinking: bool = False, ignored_params: list | None = None) -> dict:
        """Convert to Anthropic-compatible API response."""
        content = []
        if include_thinking and self.thinking_blocks:
            content.extend(self.thinking_blocks)
        if self.text:
            content.append({"type": "text", "text": self.text})

        resp = {
            "id": self.id,
            "type": "message",
            "role": "assistant",
            "model": self.model,
            "content": content,
            "stop_reason": self.stop_reason,
            "stop_sequence": None,
            "usage": self.usage,
            "_provider": self.provider,
        }
        if ignored_params:
            resp["_ignored_params"] = ignored_params
        return resp
