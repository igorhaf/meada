# DEVELOPMENT — Meada WhatsApp

Notas de setup do ambiente local e de CI. Pendências ambientais (não de produto)
ficam aqui. Riscos de produto vivem em RISKS.md.

---

## docker-java.properties com api.version=1.44

- **Onde:** `src/test/resources/docker-java.properties`
- **Conteúdo:** `api.version=1.44`
- **Por quê:** Docker daemon 29.x elevou a API mínima para 1.44. Testcontainers 1.x
  (todas as versões 1.19.8 a 1.20.6 testadas, e por extensão todo o ramo 1.x)
  inicializa a docker-java com API 1.32 quando a negociação automática não ocorre
  — o daemon 29+ recusa com "client version 1.32 is too old". A property
  `api.version=1.44` força a docker-java a usar a API moderna no startup,
  contornando o bug. Fonte: testcontainers-java issue #11210 e on_failure.html.
- **Critério de remoção:** quando o BOM do Spring Boot atualizar para
  Testcontainers ≥2.x (que negocia API automaticamente). Remover o arquivo e
  re-rodar `mvn test` para confirmar.

---

## Comando padrão de teste

```
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -B clean test
```

Não requer envs adicionais — `docker-java.properties` resolve a negociação,
`@DynamicPropertySource` injeta URL/credenciais do container Testcontainers,
`WEBHOOK_SECRET=test-secret` é setado em `AbstractIntegrationTest`.

Para subir a app localmente contra Supabase descartável: ver `application.yml`
e exports de env vars (SPRING_DATASOURCE_URL/USERNAME/PASSWORD + WEBHOOK_SECRET).
A porta local pode colidir com o Orbit (8080) — usar `SERVER_PORT=8088` em dev.

---

## JDK 17 Temurin (não JDK 21 do sistema)

- Maven deve usar Temurin 17, não o JDK 21 default do Ubuntu.
- `JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64` no `~/.bashrc` ou prefixado em
  cada comando `mvn`. Confirma com `mvn -version` (espera "Eclipse Adoptium").
- Razão: Spring Boot 3.3.13 oficialmente suporta 17 e 21, mas prod roda 17 —
  build sob outra versão introduz divergência de annotation processors e
  features sintáticas do javac.

---

## Pré-requisitos de ambiente

- **Docker** acessível via `/var/run/docker.sock` (daemon 29.x OK). Os integration
  tests sobem PostgreSQL 17-alpine via Testcontainers.
- **Maven** 3.8.7+ no PATH.
- **JDK 17 Temurin** (ver acima).
