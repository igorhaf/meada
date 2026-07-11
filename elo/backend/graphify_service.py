"""Graphify integration: queue + worker + status persistido em Redis.

Fluxo:
- `enqueue` cria job_id, salva metadata em hash `graphify:job:{id}`, push em list `graphify:queue`
- `worker_loop()` (chamado no lifespan do FastAPI) faz BLPOP, executa graphify CLI,
  parseia stdout, atualiza status no hash, salva paths de output.
"""
import asyncio
import json
import logging
import os
import re
import shutil
import time
from datetime import datetime
from pathlib import Path
from typing import Any
from uuid import uuid4

import redis.asyncio as aioredis

logger = logging.getLogger("elo.graphify")

REDIS_URL = os.getenv("REDIS_URL", "redis://redis:6379/2")
OUTPUT_BASE = Path(os.getenv("GRAPHIFY_OUTPUT_BASE", "/data/graphify-out"))
QUEUE_KEY = "graphify:queue"
JOB_HASH_PREFIX = "graphius:job:"  # typo intencional? não — usar consistente
JOB_HASH_PREFIX = "graphify:job:"
PROJECT_JOBS_PREFIX = "graphify:project:"  # zset por project_id pra listagem
ALL_JOBS_KEY = "graphify:jobs:all"  # zset global

# Reusa a logica de quota da elo_pipeline mas duplicada
# (elo-backend nao importa codigo do orbit-backend)
_QUOTA_PATTERNS = re.compile(
    r"(hit your (usage )?limit|usage limit reached|rate limit|"
    r"resets? (at|in|on)|quota exceeded|you've reached your|"
    r"try again (later|in \d))",
    re.IGNORECASE,
)

JOB_TIMEOUT_SECONDS = int(os.getenv("GRAPHIFY_JOB_TIMEOUT", "3600"))  # 1h default


_redis: aioredis.Redis | None = None


async def get_redis() -> aioredis.Redis:
    global _redis
    if _redis is None:
        _redis = aioredis.from_url(REDIS_URL, decode_responses=True)
    return _redis


async def enqueue(folder_path: str, project_id: str | None = None, options: dict | None = None) -> str:
    """Enfileira job de graphify. Retorna job_id."""
    path = Path(folder_path)
    if not path.exists() or not path.is_dir():
        raise ValueError(f"folder_path nao existe ou nao e diretorio: {folder_path}")

    job_id = str(uuid4())
    r = await get_redis()
    now = time.time()
    metadata = {
        "id": job_id,
        "folder_path": str(path),
        "project_id": project_id or "",
        "options": json.dumps(options or {}),
        "status": "queued",
        "created_at": str(now),
        "started_at": "",
        "finished_at": "",
        "error": "",
        "duration_ms": "0",
    }
    await r.hset(f"{JOB_HASH_PREFIX}{job_id}", mapping=metadata)
    await r.expire(f"{JOB_HASH_PREFIX}{job_id}", 60 * 60 * 24 * 30)  # 30 dias
    await r.rpush(QUEUE_KEY, job_id)
    await r.zadd(ALL_JOBS_KEY, {job_id: now})
    if project_id:
        await r.zadd(f"{PROJECT_JOBS_PREFIX}{project_id}", {job_id: now})

    logger.info(f"[graphify] enqueued job {job_id} for {folder_path}")
    return job_id


async def get_status(job_id: str) -> dict | None:
    r = await get_redis()
    data = await r.hgetall(f"{JOB_HASH_PREFIX}{job_id}")
    if not data:
        return None
    return _normalize_job(data)


async def list_jobs(project_id: str | None = None, limit: int = 50) -> list[dict]:
    r = await get_redis()
    key = f"{PROJECT_JOBS_PREFIX}{project_id}" if project_id else ALL_JOBS_KEY
    ids = await r.zrevrange(key, 0, limit - 1)
    jobs = []
    for jid in ids:
        data = await r.hgetall(f"{JOB_HASH_PREFIX}{jid}")
        if data:
            jobs.append(_normalize_job(data))
    return jobs


async def delete_job(job_id: str) -> bool:
    r = await get_redis()
    data = await r.hgetall(f"{JOB_HASH_PREFIX}{job_id}")
    if not data:
        return False
    project_id = data.get("project_id") or ""
    await r.delete(f"{JOB_HASH_PREFIX}{job_id}")
    await r.zrem(ALL_JOBS_KEY, job_id)
    if project_id:
        await r.zrem(f"{PROJECT_JOBS_PREFIX}{project_id}", job_id)
    out_dir = output_dir(job_id)
    if out_dir.exists():
        shutil.rmtree(out_dir, ignore_errors=True)
    return True


def output_dir(job_id: str) -> Path:
    """Pasta onde os arquivos finais ficam (graph.html, graph.json, GRAPH_REPORT.md).
    Graphify sempre adiciona o subdir 'graphify-out/' quando recebe --out."""
    return OUTPUT_BASE / job_id / "graphify-out"


def output_root(job_id: str) -> Path:
    """Pasta raiz do job (parent do 'graphify-out')."""
    return OUTPUT_BASE / job_id


def _normalize_job(raw: dict) -> dict:
    """Converte tipos do hash Redis (tudo str) pra formato de resposta."""
    def _opt_float(v: str) -> float | None:
        try:
            return float(v) if v else None
        except ValueError:
            return None

    out_dir = output_dir(raw.get("id", ""))
    html = out_dir / "graph.html"
    graph_json = out_dir / "graph.json"
    report = out_dir / "GRAPH_REPORT.md"
    return {
        "id": raw.get("id", ""),
        "folder_path": raw.get("folder_path", ""),
        "project_id": raw.get("project_id") or None,
        "options": json.loads(raw.get("options") or "{}"),
        "status": raw.get("status", "unknown"),
        "created_at": _opt_float(raw.get("created_at", "")),
        "started_at": _opt_float(raw.get("started_at", "")),
        "finished_at": _opt_float(raw.get("finished_at", "")),
        "duration_ms": int(raw.get("duration_ms", "0") or 0),
        "error": raw.get("error") or None,
        "output_paths": {
            "html": str(html) if html.exists() else None,
            "graph_json": str(graph_json) if graph_json.exists() else None,
            "report_md": str(report) if report.exists() else None,
        },
    }


async def _set_status(r: aioredis.Redis, job_id: str, **fields: Any) -> None:
    await r.hset(f"{JOB_HASH_PREFIX}{job_id}", mapping={k: str(v) for k, v in fields.items()})


async def _process_job(r: aioredis.Redis, job_id: str) -> None:
    data = await r.hgetall(f"{JOB_HASH_PREFIX}{job_id}")
    if not data:
        logger.warning(f"[graphify] job {job_id} desapareceu antes de processar")
        return

    folder_path = data["folder_path"]
    root_dir = output_root(job_id)
    root_dir.mkdir(parents=True, exist_ok=True)
    started = time.time()
    await _set_status(r, job_id, status="running", started_at=started)
    logger.info(f"[graphify] running job {job_id} on {folder_path}")

    backend = os.getenv("GRAPHIFY_BACKEND", "claude-cli")
    model = os.getenv("GRAPHIFY_MODEL")

    # Step 1: extract — gera graph.json em root_dir/graphify-out/
    extract_cmd = ["graphify", "extract", folder_path, "--backend", backend, "--out", str(root_dir)]
    if model:
        extract_cmd.extend(["--model", model])
    # Step 2: cluster-only — adiciona graph.html + GRAPH_REPORT.md no mesmo subdir
    cluster_cmd = ["graphify", "cluster-only", str(root_dir)]

    async def _run(cmd: list[str]) -> tuple[int, str]:
        proc = await asyncio.create_subprocess_exec(
            *cmd, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT,
        )
        out_bytes, _ = await asyncio.wait_for(proc.communicate(), timeout=JOB_TIMEOUT_SECONDS)
        return proc.returncode, out_bytes.decode("utf-8", errors="replace")

    try:
        rc, output = await _run(extract_cmd)
        if rc != 0:
            finished = time.time()
            quota_hit = bool(_QUOTA_PATTERNS.search(output))
            await _set_status(
                r, job_id,
                status="failed_quota" if quota_hit else "failed",
                finished_at=finished,
                duration_ms=int((finished - started) * 1000),
                error=f"extract: {output[-800:]}",
            )
            logger.error(f"[graphify] job {job_id} extract falhou (rc={rc})")
            return

        rc2, output2 = await _run(cluster_cmd)
        finished = time.time()
        duration_ms = int((finished - started) * 1000)
        if rc2 == 0:
            await _set_status(
                r, job_id,
                status="done",
                finished_at=finished,
                duration_ms=duration_ms,
            )
            logger.info(f"[graphify] job {job_id} done in {duration_ms}ms")
        else:
            # cluster falhou mas extract ok — grafo existe mas sem visualizacao
            await _set_status(
                r, job_id,
                status="failed",
                finished_at=finished,
                duration_ms=duration_ms,
                error=f"cluster-only: {output2[-800:]}",
            )
            logger.error(f"[graphify] job {job_id} cluster falhou (rc={rc2})")
    except asyncio.TimeoutError:
        finished = time.time()
        await _set_status(
            r, job_id,
            status="failed",
            finished_at=finished,
            duration_ms=int((finished - started) * 1000),
            error=f"timeout apos {JOB_TIMEOUT_SECONDS}s",
        )
        logger.error(f"[graphify] job {job_id} timeout")

    except FileNotFoundError:
        finished = time.time()
        await _set_status(
            r, job_id,
            status="failed",
            finished_at=finished,
            duration_ms=int((finished - started) * 1000),
            error="comando 'graphify' nao encontrado no PATH do container",
        )
        logger.error(f"[graphify] graphify binary nao encontrado")
    except Exception as e:
        finished = time.time()
        await _set_status(
            r, job_id,
            status="failed",
            finished_at=finished,
            duration_ms=int((finished - started) * 1000),
            error=f"{type(e).__name__}: {e}"[:500],
        )
        logger.exception(f"[graphify] erro no job {job_id}")


async def _recover_stale_jobs(r: aioredis.Redis) -> None:
    """Marca jobs em status 'running' como 'failed' no startup (worker pode ter caido)."""
    ids = await r.zrange(ALL_JOBS_KEY, 0, -1)
    recovered = 0
    for jid in ids:
        data = await r.hgetall(f"{JOB_HASH_PREFIX}{jid}")
        if data and data.get("status") == "running":
            await _set_status(
                r, jid,
                status="failed",
                finished_at=time.time(),
                error="worker reiniciou enquanto o job estava em execucao",
            )
            recovered += 1
    if recovered:
        logger.warning(f"[graphify] {recovered} jobs orfaos marcados como failed na inicializacao")


async def worker_loop() -> None:
    """Loop principal do worker. Roda como asyncio.Task no lifespan do FastAPI."""
    OUTPUT_BASE.mkdir(parents=True, exist_ok=True)
    r = await get_redis()
    await _recover_stale_jobs(r)
    logger.info(f"[graphify-worker] started, listening on {REDIS_URL} queue={QUEUE_KEY}")
    while True:
        try:
            popped = await r.blpop([QUEUE_KEY], timeout=5)
            if not popped:
                continue
            _, job_id = popped
            await _process_job(r, job_id)
        except asyncio.CancelledError:
            logger.info("[graphify-worker] cancelled, exiting")
            raise
        except Exception:
            logger.exception("[graphify-worker] erro no loop, continuando")
            await asyncio.sleep(2)
