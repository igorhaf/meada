# Sidecar de embeddings (camada 5.13.a). Python CPU-only, modelo pré-baixado no build.
FROM python:3.11-slim

# curl para o healthcheck do compose; build-essential NÃO é necessário (wheels prontas).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Dependências primeiro (camada cacheável): só reinstala se requirements mudar.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

# Pré-baixa o modelo no BUILD (vai pro cache do HF em /root/.cache/huggingface). Sem isto,
# o 1º boot baixaria ~600MB e demoraria. No runtime, o volume embedding-models cobre o
# cache entre rebuilds; aqui garantimos que a imagem já contém o modelo de partida.
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('intfloat/multilingual-e5-small')"

EXPOSE 7080

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "7080"]
