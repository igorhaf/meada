# Dockerfile do Framely (Spring Boot 3.3 / Java 17) — controle financeiro pessoal.
# API REST + bot de Telegram (opcional). Banco H2 em arquivo (volume /app/data).
#
# 2 estágios: builder (Maven → jar) e runtime (JRE fino + jar). Sem estágio dev — o framely
# roda como SERVIÇO no docker do meada (acesso via Caddy), não é hot-reloadado aqui.
# A imagem maven:*-eclipse-temurin-17 traz o mvn (o temurin puro NÃO — lição do backend meada).

# ---- builder -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
# -DskipTests: os testes rodam como gate no host; o build da imagem só empacota o jar.
RUN mvn -B -DskipTests package

# ---- runtime -------------------------------------------------------------
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
COPY --from=builder /build/target/*.jar /app/app.jar
# H2 grava em ./data/framely (relativo ao WORKDIR /app) — montado como volume p/ persistir.
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
