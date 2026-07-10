# Framely

Controle financeiro pessoal (entradas e saídas) com lançamento por **API REST** e por **bot de Telegram**.

- **Stack:** Spring Boot 3.3 · Java 17 · Maven · JPA/Hibernate · H2 (arquivo local)
- **Domínio:** `User` → `Account` (CORRENTE/DINHEIRO/POUPANCA) → `Transaction` (ENTRADA/SAIDA) + `Category`
- A regra de negócio vive em **`TransactionService`** e é compartilhada pela API e pelo bot — o bot é só **outra porta de entrada**, não duplica lógica.

## Rodar

```bash
# 1. variáveis de ambiente (o bot só liga se houver TELEGRAM_BOT_TOKEN)
cp .env.example .env        # edite com o token do @BotFather
export $(grep -v '^#' .env | xargs)

# 2. subir
mvn spring-boot:run
```

Porta padrão: **8090**. Console H2 em `http://localhost:8090/h2-console` (JDBC `jdbc:h2:file:./data/framely`).
Sem `TELEGRAM_BOT_TOKEN` a aplicação sobe normalmente **apenas com a API REST**.

## Testes

```bash
mvn -B clean test
```

## API REST (ponto de reuso do bot)

| Método | Rota | Descrição |
|--------|------|-----------|
| POST | `/api/users` | cria usuário (+ conta padrão "Carteira") — `{name,email}` |
| GET | `/api/users/{id}` | dados do usuário |
| POST | `/api/users/{id}/accounts` | adiciona conta — `{name,type}` |
| GET | `/api/users/{id}/balance` | saldo total + por conta |
| GET | `/api/users/{id}/summary` | receitas/despesas/saldo do mês |
| POST | `/api/transactions` | lança transação — `{userId,type,amount,description,categoryName,accountId}` |

## Bot de Telegram

Configuração via env: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME` (long polling).

**Comandos fixos** (sem IA):

| Comando | Efeito |
|---------|--------|
| `/start` | boas-vindas e instruções |
| `/vincular <email>` | associa este Telegram ao usuário com o email |
| `/saldo` | saldo total e por conta |
| `/resumo` | receitas, despesas e saldo do mês |

**Lançamento por mensagem** (texto livre que não é comando): a mensagem vai para a camada de IA
(`TransactionExtractor`), que devolve os dados estruturados; o bot chama o `TransactionService`
(mesma regra da API) e confirma, por exemplo:

> ✅ Saída de R$ 50,00 em Alimentação (Nubank). Novo saldo: R$ 1.230,00

Só chatIds vinculados podem lançar; um chatId desconhecido é avisado para usar `/vincular <email>`.
Transações do bot ficam com `origin = TELEGRAM`.

## Onde a IA entra

A interpretação de linguagem natural é **plugável** e ainda **não** está implementada:

- Interface: [`TransactionExtractor`](src/main/java/com/framely/telegram/TransactionExtractor.java)
- Stub atual: [`StubTransactionExtractor`](src/main/java/com/framely/telegram/StubTransactionExtractor.java) — lança
  `UnsupportedOperationException`; enquanto isso o bot responde *"🤖 IA ainda não ativa"* a texto livre.

Para conectar a IA, basta registrar um bean que implemente `TransactionExtractor` (substituindo o stub
ou marcando-o `@Primary`). Nenhuma outra camada muda — a tubulação bot → extractor → `TransactionService`
já está pronta e coberta por teste (`TelegramBotServiceIntegrationTest` usa um extractor fake).
