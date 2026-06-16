"""
Sidecar de embeddings (camada 5.13.a do bloco RAG).

FastAPI minimalista que expõe o modelo intfloat/multilingual-e5-small (384-dim, bom
em pt-BR) para o backend Java. Self-hosted: sem provider externo, sem custo recorrente,
sem rate limit. Latência ~200-400ms/chunk em CPU.

Endpoints:
  POST /embed  — {"texts": [...], "kind": "query"|"passage"} -> {"vectors", "model", "dim"}
  GET  /health — {"status", "model_loaded", "device"}

O modelo E5 EXIGE um prefixo por texto: "query: " para a consulta do usuário e
"passage: " para os trechos indexados. Default "passage" (ingestão é o caso dominante).
"""

import logging
import time
import uuid
from contextlib import asynccontextmanager
from typing import Literal

from fastapi import FastAPI
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer

MODEL_NAME = "intfloat/multilingual-e5-small"
MODEL_SHORT = "multilingual-e5-small"
EMBEDDING_DIM = 384
MAX_TEXTS = 32

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)
log = logging.getLogger("embedding-sidecar")

# Estado do modelo, preenchido no lifespan startup.
_state: dict = {"model": None}


@asynccontextmanager
async def lifespan(_app: FastAPI):
    log.info("loading model %s ...", MODEL_NAME)
    t0 = time.monotonic()
    # device padrão = cpu (imagem é torch CPU-only). O modelo já vem pré-baixado no
    # build (RUN no Dockerfile), então isto só carrega do cache — rápido.
    _state["model"] = SentenceTransformer(MODEL_NAME, device="cpu")
    log.info("model loaded in %.1fs", time.monotonic() - t0)
    yield
    _state["model"] = None


app = FastAPI(title="embedding-sidecar", version="5.13.a", lifespan=lifespan)


class EmbedRequest(BaseModel):
    texts: list[str] = Field(..., min_length=1, max_length=MAX_TEXTS)
    kind: Literal["query", "passage"] = "passage"


class EmbedResponse(BaseModel):
    vectors: list[list[float]]
    model: str
    dim: int


@app.post("/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest) -> EmbedResponse:
    request_id = uuid.uuid4().hex[:8]
    t0 = time.monotonic()
    model: SentenceTransformer = _state["model"]
    # Prefixo exigido pelo E5: "query: " ou "passage: ".
    prefix = f"{req.kind}: "
    prefixed = [prefix + t for t in req.texts]
    # normalize_embeddings=True → vetores unitários (cosine == dot product no retrieval).
    vectors = model.encode(
        prefixed,
        normalize_embeddings=True,
        convert_to_numpy=True,
    ).tolist()
    log.info(
        "request_id=%s kind=%s n=%d latency_ms=%d",
        request_id, req.kind, len(req.texts), int((time.monotonic() - t0) * 1000),
    )
    return EmbedResponse(vectors=vectors, model=MODEL_SHORT, dim=EMBEDDING_DIM)


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "model_loaded": _state["model"] is not None,
        "device": "cpu",
    }
