# embedding-sidecar

Sidecar de embeddings self-hosted (camada 5.13.a do bloco RAG do Meada).

FastAPI + `sentence-transformers` servindo o modelo **`intfloat/multilingual-e5-small`**
(384 dimensões, ~600MB de RAM, boa qualidade em pt-BR). Usado pelo backend Java (a partir
da sub-fase 5.13.c) para gerar embeddings de chunks de documentos (ingestão) e da consulta
do usuário (retrieval).

Roda **só em Docker** (regra do projeto: serviços auxiliares via compose, sem dependências
no host). O backend, que roda no host, fala com ele via `localhost:7080`.

## Subir

Do diretório raiz do projeto (`meada/meada`):

```bash
docker compose up -d --build embedding-sidecar
```

A primeira build demora alguns minutos (baixa torch CPU + o modelo, que é pré-baixado na
imagem). O cache do modelo fica no volume nomeado `embedding-models`, então rebuilds
seguintes não re-baixam.

## Verificar

```bash
# saúde (model_loaded deve ser true após o start_period)
curl -fsS localhost:7080/health
# -> {"status":"ok","model_loaded":true,"device":"cpu"}

# status do container (deve estar "healthy")
docker compose ps
```

## Smoke

```bash
curl -X POST localhost:7080/embed \
  -H 'Content-Type: application/json' \
  -d '{"texts":["teste de embedding"]}'
# -> {"vectors":[[...384 floats...]],"model":"multilingual-e5-small","dim":384}
```

## API

- `POST /embed` — corpo `{"texts": [string], "kind": "query"|"passage"}` (máx. 32 textos).
  `kind` aplica o prefixo exigido pelo E5 (`"query: "` para consultas, `"passage: "` para
  trechos indexados — default `passage`). Retorna `{"vectors", "model", "dim": 384}`.
  Vetores normalizados (unitários) → cosine = dot product no retrieval.
- `GET /health` — `{"status", "model_loaded", "device"}`.

## Derrubar

```bash
docker compose down            # para o(s) serviço(s)
docker compose down -v         # idem + remove o volume do cache do modelo
```

## Notas

- Latência típica: ~200–400ms por chunk em CPU comum (batch ajuda).
- Modelo cacheado em `embedding-models` (sobrevive a `down`/`up`, removido só com `down -v`).
- Refatorar para um provider de embedding externo (API) depois é trivial — o backend
  esconde isto atrás de uma interface `EmbeddingProvider` (sub-fase 5.13.c).
