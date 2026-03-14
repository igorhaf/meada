from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse, JSONResponse
from pydantic import BaseModel, Field
import asyncio
import json
import os
import uuid
import time

app = FastAPI(title="Claudio API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

CLAUDIO_WRAPPER = os.path.join(os.path.dirname(__file__), "run_claudio.sh")

# Environment for Claude CLI subprocess — must have correct HOME to find credentials
_CLAUDIO_USER = "claudio"
_CLAUDIO_HOME = os.environ.get("HOME", f"/home/{_CLAUDIO_USER}")
CLAUDIO_ENV = {**os.environ, "HOME": _CLAUDIO_HOME}

# Store session mappings: conversation history -> CLI session_id
sessions: dict[str, str] = {}


# ─── Request Models (Claudio API) ───

class ContentBlock(BaseModel):
    type: str
    text: str | None = None
    source: dict | None = None

class MessageParam(BaseModel):
    role: str
    content: str | list[ContentBlock]

class MessagesRequest(BaseModel):
    model: str = "claude-sonnet-4-6"
    max_tokens: int | None = None  # Optional — resolved per model below
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
    session_key: str | None = None  # Explicit session key for deterministic session management
    business_mode: bool = False  # When True, translates technical output to business language


# ─── Unsupported parameter tracking ───

UNSUPPORTED_PARAMS = {
    "temperature", "top_p", "top_k", "stop_sequences",
    "metadata", "tool_choice",
}

def get_ignored_params(request: MessagesRequest) -> list[str]:
    ignored = []
    for param in UNSUPPORTED_PARAMS:
        if getattr(request, param, None) is not None:
            ignored.append(param)
    return ignored


# ─── Model output limits (Anthropic official, Feb 2026) ───

MODEL_MAX_OUTPUT = {
    "claude-opus-4-6": 128000,
    "claude-sonnet-4-6": 64000,
    "claude-haiku-4-5": 64000,
    "claude-haiku-4-5-20251001": 64000,
}

DEFAULT_MAX_OUTPUT = 64000  # Safe default for unknown models


def resolve_max_tokens(model: str, requested: int | None) -> int:
    """Resolve max_tokens: use model limit if not specified, clamp if over limit."""
    limit = MODEL_MAX_OUTPUT.get(model, DEFAULT_MAX_OUTPUT)
    if requested is None:
        return limit
    return min(requested, limit)


# ─── Business Mode: translate technical output to business language ───

BUSINESS_TRANSLATE_PROMPT = (
    "Voce e um tradutor de linguagem tecnica para linguagem de negocio. "
    "Recebera uma saida tecnica de um sistema de software. "
    "Sua tarefa e reescrever essa saida de forma funcional, "
    "descrevendo O QUE o sistema faz e QUAL o impacto, "
    "sem mencionar detalhes de implementacao como nomes de funcoes, "
    "variaveis, tipos de dados, frameworks, ou trechos de codigo. "
    "Mantenha o mesmo idioma do texto original. "
    "Seja conciso e direto. Nao adicione introducoes como 'Aqui esta a traducao'. "
    "Apenas reescreva o conteudo."
)

BUSINESS_TRANSLATE_TIMEOUT = 120  # seconds


async def translate_to_business(technical_text: str) -> str:
    """Call CLI to translate technical output into business language."""
    cmd = [
        "setsid",
        CLAUDIO_WRAPPER,
        "-p",
        "--output-format", "stream-json",
        "--verbose",
        "--model", "haiku",
        "--permission-mode", "bypassPermissions",
        "--system-prompt", BUSINESS_TRANSLATE_PROMPT,
        technical_text,
    ]

    process = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
        stdin=asyncio.subprocess.DEVNULL,
        env=CLAUDIO_ENV,
    )

    try:
        stdout, _ = await asyncio.wait_for(
            process.communicate(), timeout=BUSINESS_TRANSLATE_TIMEOUT
        )
    except asyncio.TimeoutError:
        process.kill()
        await process.wait()
        return technical_text  # Fallback: return original on timeout

    translated = ""
    for raw_line in stdout.decode("utf-8").splitlines():
        raw_line = raw_line.strip()
        if not raw_line:
            continue
        try:
            event = json.loads(raw_line)
        except json.JSONDecodeError:
            continue

        etype = event.get("type")
        if etype == "assistant":
            for block in event.get("message", {}).get("content", []):
                if block.get("type") == "text":
                    translated = block.get("text", "")
        elif etype == "result":
            result_text = event.get("result", "")
            if result_text and not translated:
                translated = result_text

    return translated if translated else technical_text


# ─── Build CLI command from API request ───

# Threshold for passing data via file instead of CLI argument (bytes)
# OS ARG_MAX is ~2MB but individual args have practical limits (~128KB)
LARGE_ARG_THRESHOLD = 80_000


def build_cli_command(request: MessagesRequest, session_id: str | None) -> tuple[list[str], str | None]:
    """Build CLI command. Returns (cmd, stdin_data).

    When system prompt or user message exceeds LARGE_ARG_THRESHOLD,
    the system prompt is prepended to the user message and passed via stdin
    to avoid OS ARG_MAX limits on CLI arguments.
    """
    cmd = [
        "setsid",
        CLAUDIO_WRAPPER,
        "-p",
        "--output-format", "stream-json",
        "--verbose",
        "--permission-mode", "bypassPermissions",
    ]

    # Model mapping
    model_map = {
        "claude-opus-4-6": "opus",
        "claude-sonnet-4-6": "sonnet",
        "claude-haiku-4-5": "haiku",
        "claude-haiku-4-5-20251001": "haiku",
    }
    model_alias = model_map.get(request.model, request.model)
    cmd.extend(["--model", model_alias])

    # Note: Claude CLI does not support --max-tokens flag.
    # Output length is controlled by the model's default limits.
    # resolve_max_tokens() is still used for API response metadata only.

    # Tools: when tools=[] (empty list), disable ALL tools for pure text generation
    if request.tools is not None and len(request.tools) == 0:
        cmd.extend(["--tools", ""])

    # Thinking → --effort mapping with budget granularity
    if request.thinking:
        thinking_type = request.thinking.get("type", "")
        if thinking_type in ("enabled", "adaptive"):
            budget = request.thinking.get("budget_tokens", 5000)
            if budget < 5000:
                cmd.extend(["--effort", "medium"])
            else:
                cmd.extend(["--effort", "high"])

    # Extract system prompt text
    system_text = ""
    if request.system:
        if isinstance(request.system, str):
            system_text = request.system
        elif isinstance(request.system, list):
            text_parts = [b.get("text", "") for b in request.system if b.get("type") == "text"]
            system_text = "\n".join(text_parts)

    # Resume session for multi-turn
    if session_id:
        cmd.extend(["--resume", session_id])

    # Build the user message (last user message from messages array)
    last_user_msg = ""
    for msg in reversed(request.messages):
        if msg.role == "user":
            if isinstance(msg.content, str):
                last_user_msg = msg.content
            elif isinstance(msg.content, list):
                parts = []
                for block in msg.content:
                    if block.type == "text" and block.text:
                        parts.append(block.text)
                last_user_msg = "\n".join(parts)
            break

    # Determine if any argument is too large for CLI
    system_bytes = len(system_text.encode("utf-8")) if system_text else 0
    user_bytes = len(last_user_msg.encode("utf-8"))
    total_arg_bytes = system_bytes + user_bytes

    if total_arg_bytes > LARGE_ARG_THRESHOLD:
        # Large payload: pass everything via stdin to avoid ARG_MAX limits.
        # Use --append-system-prompt for a short instruction that frames the context,
        # then pass the full system prompt + user message together via stdin.
        if system_text:
            cmd.extend(["--append-system-prompt",
                "Siga rigorosamente TODAS as instrucoes fornecidas pelo usuario "
                "na secao INSTRUCOES DO SISTEMA abaixo. Responda conforme solicitado."
            ])
            merged = (
                f"=== INSTRUCOES DO SISTEMA ===\n{system_text}\n"
                f"=== FIM DAS INSTRUCOES ===\n\n"
                f"{last_user_msg}"
            )
        else:
            merged = last_user_msg
        return cmd, merged
    else:
        # Small payload: use CLI arguments normally
        if system_text:
            cmd.extend(["--system-prompt", system_text])
        cmd.append(last_user_msg)
        return cmd, None


# ─── Generate conversation key for session tracking ───

def conversation_key(messages: list[MessageParam]) -> str:
    """Create a stable key from the first user message to track sessions."""
    for msg in messages:
        if msg.role == "user":
            content = msg.content if isinstance(msg.content, str) else str(msg.content)
            return str(hash(content[:200]))
    return str(uuid.uuid4())


# ─── Run CLI and collect full response (non-streaming, Claudio) ───

    # Timeout per model for sync calls (seconds)
SYNC_TIMEOUTS = {
    "claude-opus-4-6": 900,      # 15 min for Opus (deep reasoning)
    "claude-sonnet-4-6": 600,    # 10 min for Sonnet
    "claude-haiku-4-5": 300,     # 5 min for Haiku
    "claude-haiku-4-5-20251001": 300,
}
DEFAULT_SYNC_TIMEOUT = 600


async def run_claudio_sync(request: MessagesRequest) -> dict:
    conv_key = request.session_key or conversation_key(request.messages)
    session_id = sessions.get(conv_key) if (request.session_key or len(request.messages) > 1) else None

    cmd, stdin_data = build_cli_command(request, session_id)
    cli_timeout = SYNC_TIMEOUTS.get(request.model, DEFAULT_SYNC_TIMEOUT)

    process = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
        stdin=asyncio.subprocess.PIPE if stdin_data else asyncio.subprocess.DEVNULL,
        cwd=request.cwd,
        env=CLAUDIO_ENV,
    )

    try:
        stdout, stderr = await asyncio.wait_for(
            process.communicate(
                input=stdin_data.encode("utf-8") if stdin_data else None
            ),
            timeout=cli_timeout,
        )
    except asyncio.TimeoutError:
        process.kill()
        await process.wait()
        return {
            "id": f"msg_{uuid.uuid4().hex[:24]}",
            "type": "message",
            "role": "assistant",
            "model": request.model,
            "content": [{"type": "text", "text": ""}],
            "stop_reason": "timeout",
            "stop_sequence": None,
            "usage": {"input_tokens": 0, "output_tokens": 0},
            "_error": f"CLI process timed out after {cli_timeout}s",
        }
    full_text = ""
    thinking_blocks = []
    result_session_id = ""
    input_tokens = 0
    output_tokens = 0
    model_used = request.model
    stop_reason = "end_turn"

    for raw_line in stdout.decode("utf-8").splitlines():
        raw_line = raw_line.strip()
        if not raw_line:
            continue
        try:
            event = json.loads(raw_line)
        except json.JSONDecodeError:
            continue

        etype = event.get("type")

        if etype == "system" and event.get("subtype") == "init":
            result_session_id = event.get("session_id", "")
            model_used = event.get("model", model_used)

        elif etype == "assistant":
            content_blocks = event.get("message", {}).get("content", [])
            for block in content_blocks:
                if block.get("type") == "text":
                    full_text = block.get("text", "")
                elif block.get("type") == "thinking":
                    thinking_blocks = [{
                        "type": "thinking",
                        "thinking": block.get("thinking", ""),
                    }]
            usage = event.get("message", {}).get("usage", {})
            input_tokens = usage.get("input_tokens", input_tokens)
            output_tokens = usage.get("output_tokens", output_tokens)
            sr = event.get("message", {}).get("stop_reason")
            if sr:
                stop_reason = sr

        elif etype == "result":
            result_session_id = event.get("session_id", result_session_id)
            # Extract final text from result event (agent mode puts text here)
            result_text = event.get("result", "")
            if result_text and not full_text:
                full_text = result_text
            usage = event.get("usage", {})
            input_tokens = usage.get("input_tokens", input_tokens)
            output_tokens = usage.get("output_tokens", output_tokens)

    if result_session_id:
        sessions[conv_key] = result_session_id

    # Business mode: translate technical output to business language
    if request.business_mode and full_text:
        full_text = await translate_to_business(full_text)

    msg_id = f"msg_{uuid.uuid4().hex[:24]}"

    # Build content array: include thinking blocks only if thinking was requested
    content = []
    if request.thinking and thinking_blocks:
        for tb in thinking_blocks:
            content.append(tb)
    if full_text:
        content.append({"type": "text", "text": full_text})

    return {
        "id": msg_id,
        "type": "message",
        "role": "assistant",
        "model": model_used,
        "content": content,
        "stop_reason": stop_reason,
        "stop_sequence": None,
        "usage": {
            "input_tokens": input_tokens,
            "output_tokens": output_tokens,
        },
    }


# ─── Run CLI and stream response (SSE, Claudio API format) ───

async def stream_claudio_api(request: MessagesRequest):
    conv_key = request.session_key or conversation_key(request.messages)
    session_id = sessions.get(conv_key) if (request.session_key or len(request.messages) > 1) else None

    cmd, stdin_data = build_cli_command(request, session_id)

    process = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
        stdin=asyncio.subprocess.PIPE if stdin_data else asyncio.subprocess.DEVNULL,
        cwd=request.cwd,
        env=CLAUDIO_ENV,
    )

    # Write large prompt via stdin and close to signal EOF
    if stdin_data:
        process.stdin.write(stdin_data.encode("utf-8"))
        await process.stdin.drain()
        process.stdin.close()
        await process.stdin.wait_closed()

    msg_id = f"msg_{uuid.uuid4().hex[:24]}"
    model_used = request.model
    last_text_length = 0
    last_thinking_length = 0
    result_session_id = ""
    input_tokens = 0
    output_tokens = 0
    thinking_started = False
    thinking_stopped = False
    content_started = False
    block_index = 0
    accumulated_text = ""  # Used by business_mode to buffer text before translation

    # message_start
    message_start = {
        "type": "message_start",
        "message": {
            "id": msg_id,
            "type": "message",
            "role": "assistant",
            "content": [],
            "model": model_used,
            "stop_reason": None,
            "stop_sequence": None,
            "usage": {"input_tokens": 0, "output_tokens": 0},
        },
    }
    yield f"event: message_start\ndata: {json.dumps(message_start)}\n\n"

    # ping
    yield f"event: ping\ndata: {json.dumps({'type': 'ping'})}\n\n"

    try:
        async for line in process.stdout:
            line = line.decode("utf-8").strip()
            if not line:
                continue

            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue

            etype = event.get("type")

            if etype == "system" and event.get("subtype") == "init":
                result_session_id = event.get("session_id", "")
                model_used = event.get("model", model_used)

            elif etype == "assistant":
                msg_data = event.get("message", {})
                content_blocks = msg_data.get("content", [])

                # Extract usage
                usage = msg_data.get("usage", {})
                input_tokens = usage.get("input_tokens", input_tokens)
                output_tokens = usage.get("output_tokens", output_tokens)

                # Process thinking blocks (only if thinking was requested)
                if request.thinking:
                    full_thinking = ""
                    for block in content_blocks:
                        if block.get("type") == "thinking":
                            full_thinking += block.get("thinking", "")

                    if full_thinking and len(full_thinking) > last_thinking_length:
                        if not thinking_started:
                            cbs = {
                                "type": "content_block_start",
                                "index": block_index,
                                "content_block": {"type": "thinking", "thinking": ""},
                            }
                            yield f"event: content_block_start\ndata: {json.dumps(cbs)}\n\n"
                            thinking_started = True

                        delta = full_thinking[last_thinking_length:]
                        last_thinking_length = len(full_thinking)

                        cbd = {
                            "type": "content_block_delta",
                            "index": block_index,
                            "delta": {"type": "thinking_delta", "thinking": delta},
                        }
                        yield f"event: content_block_delta\ndata: {json.dumps(cbd)}\n\n"

                # Process text blocks
                full_text = ""
                for block in content_blocks:
                    if block.get("type") == "text":
                        full_text += block.get("text", "")

                if len(full_text) > last_text_length:
                    if request.business_mode:
                        # Business mode: just accumulate, don't emit yet
                        accumulated_text = full_text
                        last_text_length = len(full_text)
                    else:
                        # Normal mode: emit deltas immediately
                        # Close thinking block if it was open
                        if thinking_started and not thinking_stopped:
                            yield f"event: content_block_stop\ndata: {json.dumps({'type': 'content_block_stop', 'index': block_index})}\n\n"
                            thinking_stopped = True
                            block_index += 1

                        if not content_started:
                            cbs = {
                                "type": "content_block_start",
                                "index": block_index,
                                "content_block": {"type": "text", "text": ""},
                            }
                            yield f"event: content_block_start\ndata: {json.dumps(cbs)}\n\n"
                            content_started = True

                        delta = full_text[last_text_length:]
                        last_text_length = len(full_text)

                        cbd = {
                            "type": "content_block_delta",
                            "index": block_index,
                            "delta": {"type": "text_delta", "text": delta},
                        }
                        yield f"event: content_block_delta\ndata: {json.dumps(cbd)}\n\n"

            elif etype == "result":
                result_session_id = event.get("session_id", result_session_id)
                # Extract final text from result event (agent mode puts text here)
                result_text = event.get("result", "")
                if result_text and last_text_length == 0:
                    if request.business_mode:
                        accumulated_text = result_text
                        last_text_length = len(result_text)
                    else:
                        # Emit as text content block since no text was streamed
                        if thinking_started and not thinking_stopped:
                            yield f"event: content_block_stop\ndata: {json.dumps({'type': 'content_block_stop', 'index': block_index})}\n\n"
                            thinking_stopped = True
                            block_index += 1
                        if not content_started:
                            cbs = {
                                "type": "content_block_start",
                                "index": block_index,
                                "content_block": {"type": "text", "text": ""},
                            }
                            yield f"event: content_block_start\ndata: {json.dumps(cbs)}\n\n"
                            content_started = True
                        cbd = {
                            "type": "content_block_delta",
                            "index": block_index,
                            "delta": {"type": "text_delta", "text": result_text},
                        }
                        yield f"event: content_block_delta\ndata: {json.dumps(cbd)}\n\n"
                        last_text_length = len(result_text)
                usage = event.get("usage", {})
                input_tokens = usage.get("input_tokens", input_tokens)
                output_tokens = usage.get("output_tokens", output_tokens)

    except Exception as e:
        err = {
            "type": "error",
            "error": {"type": "api_error", "message": str(e)},
        }
        yield f"event: error\ndata: {json.dumps(err)}\n\n"
    finally:
        if process.returncode is None:
            process.kill()
            await process.wait()

    if result_session_id:
        sessions[conv_key] = result_session_id

    # Business mode: translate accumulated text and emit as a single block
    if request.business_mode and accumulated_text:
        translated = await translate_to_business(accumulated_text)

        # Close thinking block if still open
        if thinking_started and not thinking_stopped:
            yield f"event: content_block_stop\ndata: {json.dumps({'type': 'content_block_stop', 'index': block_index})}\n\n"
            thinking_stopped = True
            block_index += 1

        # Emit translated text as a single content block
        cbs = {
            "type": "content_block_start",
            "index": block_index,
            "content_block": {"type": "text", "text": ""},
        }
        yield f"event: content_block_start\ndata: {json.dumps(cbs)}\n\n"
        content_started = True

        cbd = {
            "type": "content_block_delta",
            "index": block_index,
            "delta": {"type": "text_delta", "text": translated},
        }
        yield f"event: content_block_delta\ndata: {json.dumps(cbd)}\n\n"

    # Close thinking block if still open
    if thinking_started and not thinking_stopped:
        yield f"event: content_block_stop\ndata: {json.dumps({'type': 'content_block_stop', 'index': block_index})}\n\n"
        thinking_stopped = True
        block_index += 1

    # content_block_stop for text
    if content_started:
        yield f"event: content_block_stop\ndata: {json.dumps({'type': 'content_block_stop', 'index': block_index})}\n\n"

    # message_delta
    md = {
        "type": "message_delta",
        "delta": {"stop_reason": "end_turn", "stop_sequence": None},
        "usage": {"output_tokens": output_tokens},
    }
    yield f"event: message_delta\ndata: {json.dumps(md)}\n\n"

    # message_stop
    yield f"event: message_stop\ndata: {json.dumps({'type': 'message_stop'})}\n\n"


# ─── Endpoints ───

@app.post("/v1/messages")
async def messages(request: MessagesRequest):
    # Resolve max_tokens based on model limits
    request.max_tokens = resolve_max_tokens(request.model, request.max_tokens)

    ignored = get_ignored_params(request)

    if request.stream:
        response = StreamingResponse(
            stream_claudio_api(request),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "Connection": "keep-alive",
                "X-Accel-Buffering": "no",
            },
        )
        if ignored:
            response.headers["X-Ignored-Params"] = ",".join(ignored)
        return response
    else:
        result = await run_claudio_sync(request)
        if ignored:
            result["_ignored_params"] = ignored
        return JSONResponse(content=result)


# ─── Project Context Registry ───
# Each entry: (project, use_case) → system prompt
# Add new project/use_case pairs here — no code changes needed elsewhere.

PROJECT_CONTEXTS: dict[tuple[str, str], str] = {
    ("meada", "homepage"): """Você é a assistente virtual da Meada Digital — uma agência especializada em desenvolvimento de software e integração de IA para empresas.

Sobre a Meada:
- Mais de 50 projetos entregues, 5+ anos no mercado, 20+ tecnologias dominadas
- Fundada por Igor Hafiz, com foco em qualidade, velocidade e resultado de negócio
- Atendemos desde startups a empresas consolidadas que precisam de tecnologia sob medida

Nossos serviços:
- Desenvolvimento personalizado: sites institucionais, sistemas complexos, plataformas SaaS
- Infraestrutura em nuvem: deploy, CI/CD, monitoramento e escalabilidade
- IA & Automação: chatbots inteligentes, automações de processos, análise de dados
- Design Mobile First: apps e interfaces fluidas em qualquer dispositivo
- Design & UX: wireframes, protótipos e Design Systems completos
- APIs & Integrações: pagamentos, CRMs, ERPs, qualquer sistema conectado

Projetos de destaque:
- FinTrack: super-app financeiro com 200k usuários no primeiro semestre, +340% retenção
- NeuralHub: plataforma de IA para automação industrial, 80% menos trabalho manual
- CloudOps Pro: orquestração multi-cloud para grande empresa, 60% redução de custos de infra

Como se comportar:
- Seja calorosa e direta — MÁXIMO 2 linhas por resposta, sem exceção
- Fale sempre em benefícios e resultados de negócio, nunca em linguagem técnica
- Responda APENAS sobre a Meada, nossos serviços, projetos e processo de trabalho
- Se a pergunta estiver fora do escopo, redirecione gentilmente em 1 linha
- Não invente preços — diga que variam conforme escopo
- Sempre encerre com um convite à ação curto (ex: "Quer agendar uma conversa?")
- Idioma: português brasileiro, tom amigável e profissional
- PROIBIDO: markdown, asteriscos, hífens de lista, hashtags, negrito — apenas texto simples corrido""",

    ("atelie", "atendimento"): """Você é a assistente virtual do Ateliê Rosendo — um ateliê de artesanato criado pela artesã Aline Rosendo, especializado em peças de crochê feitas à mão.

Sobre o Ateliê Rosendo:
- Mais de 500 peças criadas, 8+ anos de experiência em artesanato
- Cada peça é 100% artesanal, feita à mão com materiais selecionados
- Produtos: almofadas de crochê, mantas, cardigans, buquês de flores em crochê, amigurumis, tapetes, granny squares, cestas organizadoras
- Categorias: Crochê, Bordados, Decoração, Amigurumi
- Aceitamos encomendas personalizadas (cores, tamanhos, nomes bordados)
- Enviamos para todo o Brasil pelos Correios (PAC e SEDEX)
- Pagamento: Pix, cartão ou combinar pelo WhatsApp
- Faixa de preço: R$ 42 a R$ 390 dependendo da peça

Processo artesanal:
- Inspiração e projeto da peça
- Seleção de materiais (linha 100% algodão, lã, agulhas)
- Criação manual sem linha de produção
- Embalagem especial em papel kraft

Como se comportar:
- Seja calorosa, acolhedora e simpática, como se fosse a própria Aline
- MÁXIMO 2 linhas por resposta, sem exceção
- Responda APENAS sobre o Ateliê Rosendo, produtos, encomendas, frete e prazos
- Se perguntarem preço exato, diga o valor se souber, senão diga que varia conforme tamanho e complexidade
- Para encomendas, oriente a falar pelo WhatsApp
- Prazo de produção: 5 a 15 dias úteis dependendo da peça
- Se a pergunta estiver fora do escopo, redirecione gentilmente
- Sempre encerre com um convite (ex: "Quer ver nossos produtos?" ou "Posso te ajudar com mais alguma coisa?")
- Idioma: português brasileiro, tom carinhoso e artesanal
- PROIBIDO: markdown, asteriscos, hífens de lista, hashtags, negrito — apenas texto simples corrido""",
}


def get_project_system_prompt(project: str | None, use_case: str | None) -> str | None:
    if not project or not use_case:
        return None
    return PROJECT_CONTEXTS.get((project.lower(), use_case.lower()))


# Keep the simple chat endpoint for the frontend
class SimpleChatRequest(BaseModel):
    message: str
    session_id: str | None = None
    project: str | None = None
    use_case: str | None = None


async def stream_simple_chat(message: str, session_id: str | None = None, project: str | None = None, use_case: str | None = None):
    system_prompt = get_project_system_prompt(project, use_case)
    cmd = [
        "setsid", CLAUDIO_WRAPPER, "-p",
        "--output-format", "stream-json", "--verbose",
        "--permission-mode", "bypassPermissions",
        "--model", "haiku",
    ]
    if system_prompt:
        cmd.extend(["--system-prompt", system_prompt])
    if session_id:
        cmd.extend(["--resume", session_id])
    cmd.append(message)

    process = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
        stdin=asyncio.subprocess.DEVNULL,
        env=CLAUDIO_ENV,
    )

    last_text_length = 0
    result_session_id = session_id or ""

    yield f"event: thinking\ndata: {{}}\n\n"

    try:
        async for line in process.stdout:
            line = line.decode("utf-8").strip()
            if not line:
                continue
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue

            etype = event.get("type")
            if etype == "system" and event.get("subtype") == "init":
                result_session_id = event.get("session_id", result_session_id)
            elif etype == "assistant":
                content_blocks = event.get("message", {}).get("content", [])
                full_text = ""
                for block in content_blocks:
                    if block.get("type") == "text":
                        full_text += block.get("text", "")
                if len(full_text) > last_text_length:
                    delta = full_text[last_text_length:]
                    last_text_length = len(full_text)
                    yield f"event: chunk\ndata: {json.dumps({'text': delta})}\n\n"
            elif etype == "result":
                sid = event.get("session_id", result_session_id)
                if sid:
                    result_session_id = sid
    except Exception as e:
        yield f"event: error\ndata: {json.dumps({'text': str(e)})}\n\n"
    finally:
        if process.returncode is None:
            process.kill()
            await process.wait()

    yield f"event: done\ndata: {json.dumps({'session_id': result_session_id})}\n\n"


@app.post("/api/chat")
async def chat(request: SimpleChatRequest):
    return StreamingResponse(
        stream_simple_chat(request.message, request.session_id, request.project, request.use_case),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive", "X-Accel-Buffering": "no"},
    )


# ─── Session management endpoints ───

@app.get("/v1/sessions")
async def list_sessions():
    """List all active session mappings (for debugging)."""
    return {"sessions": {k: v for k, v in sessions.items()}, "count": len(sessions)}


@app.delete("/v1/sessions/{session_key}")
async def delete_session(session_key: str):
    """Delete a specific session by key. Used to clean up after pipeline phases."""
    if session_key in sessions:
        del sessions[session_key]
        return {"status": "deleted", "session_key": session_key}
    return JSONResponse(status_code=404, content={"status": "not_found", "session_key": session_key})


@app.delete("/v1/sessions")
async def clear_all_sessions():
    """Clear all sessions. Use with caution."""
    count = len(sessions)
    sessions.clear()
    return {"status": "cleared", "sessions_removed": count}


@app.get("/api/health")
async def health():
    return {"status": "ok"}
