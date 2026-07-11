"""
Elo — Configuration and constants.
"""
import os
from pathlib import Path

# ─── Paths ───
BASE_DIR = Path(__file__).parent
WRAPPER_PATH = str(BASE_DIR / "run_elo.sh")
PERSONA_DB = BASE_DIR / "elo.db"

# ─── Auth ───
API_KEY = os.environ.get("ELO_API_KEY", "123456789")

# ─── CLI Environment ───
ELO_USER = os.environ.get("ELO_USER", "igorhaf")
ELO_HOME = os.environ.get("HOME", f"/home/{ELO_USER}")
CLI_ENV = {**os.environ, "HOME": ELO_HOME}

# ─── DeepSeek ───
DEEPSEEK_API_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.environ.get("DEEPSEEK_BASE_URL", "https://api.deepseek.com")

# ─── Model mappings ───
MODEL_MAX_OUTPUT = {
    "claude-opus-4-7": 128000,
    "claude-opus-4-6": 128000,   # mantido por compat
    "claude-sonnet-4-6": 64000,
    "claude-haiku-4-5": 64000,
    "claude-haiku-4-5-20251001": 64000,
}

# Context window (input tokens) — Opus 4.7 e Sonnet 4.6 sao 1M nativo (sem header beta)
MODEL_CONTEXT_WINDOW = {
    "claude-opus-4-7": 1_000_000,
    "claude-opus-4-6": 200_000,
    "claude-sonnet-4-6": 1_000_000,
    "claude-haiku-4-5": 200_000,
    "claude-haiku-4-5-20251001": 200_000,
}

MODEL_CLI_ALIAS = {
    "claude-opus-4-7": "opus",
    "claude-opus-4-6": "opus",   # mantido por compat
    "claude-sonnet-4-6": "sonnet",
    "claude-haiku-4-5": "haiku",
    "claude-haiku-4-5-20251001": "haiku",
}

MODEL_TIMEOUT = {
    "opus": 900,
    "sonnet": 900,  # subido de 600: geração de JSON grande (epics/wiki) com
                    # raciocínio pode passar de 600s; o orbit já usa batches
                    # pequenos pra limitar o output, isto é a margem de segurança.
    "haiku": 300,
}

# DeepSeek model mapping — when falling back, map Claude models to DeepSeek equivalents
DEEPSEEK_MODEL_MAP = {
    "claude-opus-4-7": "deepseek-chat",
    "claude-opus-4-6": "deepseek-chat",
    "claude-sonnet-4-6": "deepseek-chat",
    "claude-haiku-4-5": "deepseek-chat",
    "claude-haiku-4-5-20251001": "deepseek-chat",
}

# ─── CLI ───
LARGE_ARG_THRESHOLD = 80_000

UNSUPPORTED_PARAMS = {"temperature", "top_p", "top_k", "stop_sequences", "metadata", "tool_choice"}

# ─── Provider ordering ───
PROVIDER_ORDER = ["claude_code", "deepseek"]
