"""
Elo — DeepSeek API Provider (fallback).
Uses DeepSeek's OpenAI-compatible API as fallback when Claude Code is unavailable.
"""
import json
import logging
import time
import uuid
import httpx
from typing import AsyncGenerator

from config import DEEPSEEK_API_KEY, DEEPSEEK_BASE_URL, DEEPSEEK_MODEL_MAP
from models import MessagesRequest, ProviderResponse
from core.provider import AIProvider

logger = logging.getLogger("elo.provider.deepseek")


def _convert_messages(request: MessagesRequest) -> list[dict]:
    """Convert Anthropic-format messages to OpenAI-format for DeepSeek."""
    messages = []

    # System prompt
    system_text = ""
    if request.system:
        system_text = request.system if isinstance(request.system, str) else json.dumps(request.system, ensure_ascii=False)
    if system_text:
        messages.append({"role": "system", "content": system_text})

    # Conversation messages
    for msg in request.messages:
        content = msg.content
        if isinstance(content, list):
            text_parts = []
            for block in content:
                if isinstance(block, dict):
                    text_parts.append(block.get("text", ""))
                else:
                    text_parts.append(block.text or "")
            content = " ".join(text_parts)
        messages.append({"role": msg.role, "content": content})

    return messages


class DeepSeekProvider(AIProvider):
    """Fallback provider — DeepSeek API (OpenAI-compatible)."""

    @property
    def name(self) -> str:
        return "deepseek"

    @property
    def display_name(self) -> str:
        return "DeepSeek API"

    async def is_available(self) -> bool:
        """Check if API key is configured."""
        if not DEEPSEEK_API_KEY:
            logger.warning("DeepSeek API key not configured (DEEPSEEK_API_KEY)")
            return False
        return True

    def supports_model(self, model: str) -> bool:
        return model in DEEPSEEK_MODEL_MAP

    async def complete(self, request: MessagesRequest) -> ProviderResponse:
        """Non-streaming completion via DeepSeek API."""
        ds_model = DEEPSEEK_MODEL_MAP.get(request.model, "deepseek-chat")
        messages = _convert_messages(request)

        payload = {
            "model": ds_model,
            "messages": messages,
            "stream": False,
        }
        if request.max_tokens:
            payload["max_tokens"] = request.max_tokens
        if request.temperature is not None:
            payload["temperature"] = request.temperature

        logger.info(f"DeepSeek request: model={ds_model}, messages={len(messages)}")

        async with httpx.AsyncClient(timeout=300) as client:
            response = await client.post(
                f"{DEEPSEEK_BASE_URL}/v1/chat/completions",
                headers={
                    "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
                    "Content-Type": "application/json",
                },
                json=payload,
            )

            if response.status_code != 200:
                error_text = response.text[:500]
                logger.error(f"DeepSeek API error {response.status_code}: {error_text}")
                raise RuntimeError(f"DeepSeek API returned {response.status_code}: {error_text}")

            data = response.json()

        choice = data.get("choices", [{}])[0]
        text = choice.get("message", {}).get("content", "")
        finish_reason = choice.get("finish_reason", "stop")
        usage_data = data.get("usage", {})

        # Map DeepSeek stop reasons to Anthropic format
        stop_map = {"stop": "end_turn", "length": "max_tokens", "content_filter": "end_turn"}
        stop_reason = stop_map.get(finish_reason, "end_turn")

        return ProviderResponse(
            text=text,
            model=f"{ds_model} (via deepseek)",
            provider=self.name,
            usage={
                "input_tokens": usage_data.get("prompt_tokens", 0),
                "output_tokens": usage_data.get("completion_tokens", 0),
            },
            stop_reason=stop_reason,
        )

    async def stream(self, request: MessagesRequest) -> AsyncGenerator[str, None]:
        """Streaming completion via DeepSeek API, emitting Anthropic-compatible SSE."""
        ds_model = DEEPSEEK_MODEL_MAP.get(request.model, "deepseek-chat")
        messages = _convert_messages(request)
        msg_id = f"msg_{uuid.uuid4().hex[:24]}"

        payload = {
            "model": ds_model,
            "messages": messages,
            "stream": True,
        }
        if request.max_tokens:
            payload["max_tokens"] = request.max_tokens
        if request.temperature is not None:
            payload["temperature"] = request.temperature

        logger.info(f"DeepSeek stream: model={ds_model}")

        # message_start
        yield (
            f"event: message_start\n"
            f"data: {json.dumps({'type': 'message_start', 'message': {'id': msg_id, 'type': 'message', 'role': 'assistant', 'model': ds_model, 'content': [], 'stop_reason': None, 'usage': {'input_tokens': 0, 'output_tokens': 0}}})}\n\n"
        )
        yield "event: ping\ndata: {\"type\": \"ping\"}\n\n"

        # content_block_start
        yield f"event: content_block_start\ndata: {json.dumps({'type': 'content_block_start', 'index': 0, 'content_block': {'type': 'text', 'text': ''}})}\n\n"

        usage = {"input_tokens": 0, "output_tokens": 0}
        stop_reason = "end_turn"

        async with httpx.AsyncClient(timeout=300) as client:
            async with client.stream(
                "POST",
                f"{DEEPSEEK_BASE_URL}/v1/chat/completions",
                headers={
                    "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
                    "Content-Type": "application/json",
                },
                json=payload,
            ) as response:
                if response.status_code != 200:
                    error = await response.aread()
                    raise RuntimeError(f"DeepSeek stream error {response.status_code}: {error.decode()[:500]}")

                async for line in response.aiter_lines():
                    line = line.strip()
                    if not line or not line.startswith("data: "):
                        continue
                    data_str = line[6:]
                    if data_str == "[DONE]":
                        break

                    try:
                        chunk = json.loads(data_str)
                    except json.JSONDecodeError:
                        continue

                    delta = chunk.get("choices", [{}])[0].get("delta", {})
                    content = delta.get("content", "")
                    if content:
                        yield f"event: content_block_delta\ndata: {json.dumps({'type': 'content_block_delta', 'index': 0, 'delta': {'type': 'text_delta', 'text': content}})}\n\n"

                    finish = chunk.get("choices", [{}])[0].get("finish_reason")
                    if finish:
                        stop_map = {"stop": "end_turn", "length": "max_tokens"}
                        stop_reason = stop_map.get(finish, "end_turn")

                    if chunk.get("usage"):
                        u = chunk["usage"]
                        usage["input_tokens"] = u.get("prompt_tokens", 0)
                        usage["output_tokens"] = u.get("completion_tokens", 0)

        # Close block
        yield f"event: content_block_stop\ndata: {json.dumps({'type': 'content_block_stop', 'index': 0})}\n\n"
        yield f"event: message_delta\ndata: {json.dumps({'type': 'message_delta', 'delta': {'stop_reason': stop_reason}, 'usage': usage})}\n\n"
        yield "event: message_stop\ndata: {\"type\": \"message_stop\"}\n\n"
