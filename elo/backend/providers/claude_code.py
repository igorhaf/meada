"""
Elo — Claude Code CLI Provider (primary).
Executes Claude CLI as subprocess to process requests.
"""
import asyncio
import json
import logging
import os
import time
import uuid
from subprocess import DEVNULL
from typing import AsyncGenerator

from config import (
    WRAPPER_PATH, CLI_ENV, MODEL_CLI_ALIAS, MODEL_MAX_OUTPUT,
    MODEL_TIMEOUT, LARGE_ARG_THRESHOLD,
)
from models import MessagesRequest, ProviderResponse, MessageParam
from core.provider import AIProvider

logger = logging.getLogger("elo.provider.claude_code")


def _extract_text(content) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return " ".join(b.get("text", "") if isinstance(b, dict) else (b.text or "") for b in content)
    return str(content)


def _serialize_history(messages: list[MessageParam]) -> str:
    last_user_idx = -1
    for i in range(len(messages) - 1, -1, -1):
        if messages[i].role == "user":
            last_user_idx = i
            break
    if last_user_idx <= 0:
        return ""
    parts = []
    for msg in messages[:last_user_idx]:
        label = "Human" if msg.role == "user" else "Assistant"
        parts.append(f"{label}: {_extract_text(msg.content)}")
    return "\n\n".join(parts)


def _build_cli_command(request: MessagesRequest, session_id: str | None = None) -> tuple[list[str], str | None]:
    """Build the CLI command and optional stdin data."""
    alias = MODEL_CLI_ALIAS.get(request.model, "sonnet")
    cmd = [
        "setsid", WRAPPER_PATH, "-p",
        "--output-format", "stream-json",
        "--verbose",
        "--permission-mode", "bypassPermissions",
        "--model", alias,
    ]

    if session_id:
        cmd.extend(["--resume", session_id])

    # System prompt
    system_text = ""
    if request.system:
        system_text = request.system if isinstance(request.system, str) else json.dumps(request.system, ensure_ascii=False)

    # Last user message
    last_user_msg = ""
    for msg in reversed(request.messages):
        if msg.role == "user":
            last_user_msg = _extract_text(msg.content)
            break

    # Multi-turn history
    if not session_id and len(request.messages) > 1:
        history = _serialize_history(request.messages)
        if history:
            last_user_msg = f"<conversation_history>\n{history}\n</conversation_history>\n\n{last_user_msg}"

    # Tools
    if request.tools is not None and len(request.tools) == 0:
        cmd.extend(["--tools", ""])

    # Thinking / effort
    if request.thinking:
        thinking_type = request.thinking.get("type", "")
        if thinking_type in ("enabled", "adaptive"):
            budget = request.thinking.get("budget_tokens", 5000)
            cmd.extend(["--effort", "medium" if budget < 5000 else "high"])

    # Large argument handling
    total_bytes = len((system_text + last_user_msg).encode("utf-8"))
    if total_bytes > LARGE_ARG_THRESHOLD:
        cmd.extend(["--append-system-prompt", "Siga rigorosamente as instrucoes do sistema enviadas via stdin."])
        merged = f"=== INSTRUCOES DO SISTEMA ===\n{system_text}\n\n=== MENSAGEM DO USUARIO ===\n{last_user_msg}"
        return cmd, merged
    else:
        if system_text:
            cmd.extend(["--system-prompt", system_text])
        cmd.append(last_user_msg)
        return cmd, None


class ClaudeCodeProvider(AIProvider):
    """Primary provider — executes Claude Code CLI as subprocess."""

    @property
    def name(self) -> str:
        return "claude_code"

    @property
    def display_name(self) -> str:
        return "Claude Code CLI"

    async def is_available(self) -> bool:
        """Check if the CLI wrapper exists and is executable."""
        try:
            return os.path.isfile(WRAPPER_PATH) and os.access(WRAPPER_PATH, os.X_OK)
        except Exception:
            return False

    def supports_model(self, model: str) -> bool:
        return model in MODEL_CLI_ALIAS

    async def complete(self, request: MessagesRequest) -> ProviderResponse:
        """Run CLI synchronously and return full response."""
        from sessions import get_session, set_session, conversation_key

        conv_key = request.session_key or conversation_key(request.messages)
        session_id = get_session(conv_key) if (request.session_key or len(request.messages) > 1) else None

        cmd, stdin_data = _build_cli_command(request, session_id)
        alias = MODEL_CLI_ALIAS.get(request.model, "sonnet")
        timeout = MODEL_TIMEOUT.get(alias, 600)
        max_output = MODEL_MAX_OUTPUT.get(request.model, 64000)

        if request.max_tokens:
            max_output = min(request.max_tokens, max_output)

        logger.debug(f"CLI command: {' '.join(cmd[:8])}... timeout={timeout}s")

        process = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            stdin=asyncio.subprocess.PIPE if stdin_data else DEVNULL,
            cwd=request.cwd,
            env=CLI_ENV,
        )

        try:
            stdout_bytes, stderr_bytes = await asyncio.wait_for(
                process.communicate(input=stdin_data.encode("utf-8") if stdin_data else None),
                timeout=timeout,
            )
        except asyncio.TimeoutError:
            process.kill()
            raise RuntimeError(f"CLI timeout after {timeout}s")

        stdout = stdout_bytes.decode("utf-8", errors="replace")
        if process.returncode != 0 and not stdout.strip():
            stderr = stderr_bytes.decode("utf-8", errors="replace")
            raise RuntimeError(f"CLI exited with code {process.returncode}: {stderr[:500]}")

        # Parse output
        full_text = ""
        thinking_blocks = []
        usage = {"input_tokens": 0, "output_tokens": 0}
        stop_reason = "end_turn"
        result_session_id = session_id
        model_used = request.model

        for raw_line in stdout.splitlines():
            raw_line = raw_line.strip()
            if not raw_line:
                continue
            try:
                event = json.loads(raw_line)
            except json.JSONDecodeError:
                continue

            etype = event.get("type", "")

            if etype == "system":
                sub = event.get("subtype", "")
                if sub == "init":
                    result_session_id = event.get("session_id", result_session_id)
                    model_used = event.get("model", model_used)

            elif etype == "assistant":
                msg = event.get("message", {})
                content = msg.get("content", "")
                if isinstance(content, list):
                    for block in content:
                        if block.get("type") == "thinking":
                            thinking_blocks.append(block)
                        elif block.get("type") == "text":
                            full_text += block.get("text", "")
                elif isinstance(content, str):
                    full_text += content

                msg_usage = msg.get("usage") or event.get("usage")
                if msg_usage:
                    usage["input_tokens"] = max(usage["input_tokens"], msg_usage.get("input_tokens", 0))
                    usage["output_tokens"] = max(usage["output_tokens"], msg_usage.get("output_tokens", 0))

                sr = msg.get("stop_reason") or event.get("stop_reason")
                if sr:
                    stop_reason = sr

            elif etype == "result":
                result_text = event.get("result", "")
                if result_text and not full_text:
                    full_text = result_text
                result_session_id = event.get("session_id", result_session_id)
                if event.get("usage"):
                    u = event["usage"]
                    usage["input_tokens"] = max(usage["input_tokens"], u.get("input_tokens", 0))
                    usage["output_tokens"] = max(usage["output_tokens"], u.get("output_tokens", 0))

        # Store session
        if result_session_id:
            set_session(conv_key, result_session_id)

        return ProviderResponse(
            text=full_text,
            model=model_used,
            provider=self.name,
            thinking_blocks=thinking_blocks,
            usage=usage,
            stop_reason=stop_reason,
            session_id=result_session_id,
        )

    async def stream(self, request: MessagesRequest) -> AsyncGenerator[str, None]:
        """Stream CLI output as SSE events."""
        from sessions import get_session, set_session, conversation_key

        conv_key = request.session_key or conversation_key(request.messages)
        session_id = get_session(conv_key) if (request.session_key or len(request.messages) > 1) else None

        cmd, stdin_data = _build_cli_command(request, session_id)
        max_output = MODEL_MAX_OUTPUT.get(request.model, 64000)
        if request.max_tokens:
            max_output = min(request.max_tokens, max_output)

        msg_id = f"msg_{uuid.uuid4().hex[:24]}"
        model_used = request.model

        process = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            stdin=asyncio.subprocess.PIPE if stdin_data else DEVNULL,
            cwd=request.cwd,
            env=CLI_ENV,
        )

        try:
            if stdin_data and process.stdin:
                process.stdin.write(stdin_data.encode("utf-8"))
                await process.stdin.drain()
                process.stdin.close()

            # message_start
            yield (
                f"event: message_start\n"
                f"data: {json.dumps({'type': 'message_start', 'message': {'id': msg_id, 'type': 'message', 'role': 'assistant', 'model': model_used, 'content': [], 'stop_reason': None, 'usage': {'input_tokens': 0, 'output_tokens': 0}}})}\n\n"
            )
            yield "event: ping\ndata: {\"type\": \"ping\"}\n\n"

            block_idx = 0
            in_thinking = False
            in_text = False
            thinking_requested = request.thinking is not None
            usage = {"input_tokens": 0, "output_tokens": 0}
            stop_reason = "end_turn"

            async for line in process.stdout:
                raw = line.decode("utf-8", errors="replace").strip()
                if not raw:
                    continue
                try:
                    event = json.loads(raw)
                except json.JSONDecodeError:
                    continue

                etype = event.get("type", "")

                if etype == "system":
                    sub = event.get("subtype", "")
                    if sub == "init":
                        sid = event.get("session_id")
                        if sid:
                            set_session(conv_key, sid)
                        model_used = event.get("model", model_used)

                elif etype == "assistant":
                    msg = event.get("message", {})
                    content = msg.get("content", "")

                    if isinstance(content, list):
                        for block in content:
                            btype = block.get("type", "")

                            if btype == "thinking" and thinking_requested:
                                if not in_thinking:
                                    yield f"event: content_block_start\ndata: {json.dumps({'type': 'content_block_start', 'index': block_idx, 'content_block': {'type': 'thinking', 'thinking': ''}})}\n\n"
                                    in_thinking = True
                                text = block.get("thinking", "")
                                if text:
                                    yield f"event: content_block_delta\ndata: {json.dumps({'type': 'content_block_delta', 'index': block_idx, 'delta': {'type': 'thinking_delta', 'thinking': text}})}\n\n"

                            elif btype == "text":
                                if in_thinking:
                                    yield f"event: content_block_stop\ndata: {json.dumps({'type': 'content_block_stop', 'index': block_idx})}\n\n"
                                    block_idx += 1
                                    in_thinking = False
                                if not in_text:
                                    yield f"event: content_block_start\ndata: {json.dumps({'type': 'content_block_start', 'index': block_idx, 'content_block': {'type': 'text', 'text': ''}})}\n\n"
                                    in_text = True
                                text = block.get("text", "")
                                if text:
                                    yield f"event: content_block_delta\ndata: {json.dumps({'type': 'content_block_delta', 'index': block_idx, 'delta': {'type': 'text_delta', 'text': text}})}\n\n"

                    elif isinstance(content, str) and content:
                        if not in_text:
                            if in_thinking:
                                yield f"event: content_block_stop\ndata: {json.dumps({'type': 'content_block_stop', 'index': block_idx})}\n\n"
                                block_idx += 1
                                in_thinking = False
                            yield f"event: content_block_start\ndata: {json.dumps({'type': 'content_block_start', 'index': block_idx, 'content_block': {'type': 'text', 'text': ''}})}\n\n"
                            in_text = True
                        yield f"event: content_block_delta\ndata: {json.dumps({'type': 'content_block_delta', 'index': block_idx, 'delta': {'type': 'text_delta', 'text': content}})}\n\n"

                    msg_usage = msg.get("usage") or event.get("usage")
                    if msg_usage:
                        usage["input_tokens"] = max(usage["input_tokens"], msg_usage.get("input_tokens", 0))
                        usage["output_tokens"] = max(usage["output_tokens"], msg_usage.get("output_tokens", 0))

                    sr = msg.get("stop_reason") or event.get("stop_reason")
                    if sr:
                        stop_reason = sr

                elif etype == "result":
                    result_text = event.get("result", "")
                    if result_text:
                        if not in_text:
                            if in_thinking:
                                yield f"event: content_block_stop\ndata: {json.dumps({'type': 'content_block_stop', 'index': block_idx})}\n\n"
                                block_idx += 1
                                in_thinking = False
                            yield f"event: content_block_start\ndata: {json.dumps({'type': 'content_block_start', 'index': block_idx, 'content_block': {'type': 'text', 'text': ''}})}\n\n"
                            in_text = True
                        yield f"event: content_block_delta\ndata: {json.dumps({'type': 'content_block_delta', 'index': block_idx, 'delta': {'type': 'text_delta', 'text': result_text}})}\n\n"

                    sid = event.get("session_id")
                    if sid:
                        set_session(conv_key, sid)
                    if event.get("usage"):
                        u = event["usage"]
                        usage["input_tokens"] = max(usage["input_tokens"], u.get("input_tokens", 0))
                        usage["output_tokens"] = max(usage["output_tokens"], u.get("output_tokens", 0))

            # Close open blocks
            if in_thinking:
                yield f"event: content_block_stop\ndata: {json.dumps({'type': 'content_block_stop', 'index': block_idx})}\n\n"
                block_idx += 1
            if in_text:
                yield f"event: content_block_stop\ndata: {json.dumps({'type': 'content_block_stop', 'index': block_idx})}\n\n"

            # message_delta + message_stop
            yield f"event: message_delta\ndata: {json.dumps({'type': 'message_delta', 'delta': {'stop_reason': stop_reason}, 'usage': usage})}\n\n"
            yield "event: message_stop\ndata: {\"type\": \"message_stop\"}\n\n"

        finally:
            if process.returncode is None:
                try:
                    process.kill()
                except ProcessLookupError:
                    pass
