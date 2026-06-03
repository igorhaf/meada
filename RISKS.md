# RISKS — Meada WhatsApp

Registro de riscos conhecidos e pendências bloqueantes do projeto. Cada item segue
o template fixo abaixo. Auditar este arquivo antes de marcos relevantes (ativação
de cliente, deploy de produção, due diligence).

Template por item:

```
## <Título do risco>

- **Status:** <Aberto | Mitigado | Aceito>
- **Bloqueante para:** <marco que não pode passar sem resolver>
- **Razão:** <por que existe>
- **Plano de mitigação:** <ação concreta + critério de fechamento>
- **Detectado em:** <camada/momento>
```

---

## Schema do payload da Evolution validado contra Baileys, não contra fonte oficial

- **Status:** Aberto.
- **Bloqueante para:** Ativação do primeiro cliente real (não para fechar a camada 2).
- **Razão:** A Evolution API não publica schema canônico do payload de webhook. Os DTOs em `EvolutionWebhookPayload.java` foram montados a partir do código-fonte do envelope (campos `event`, `instance`, `data`, `server_url`, `date_time`, etc. — confirmados no `webhook.controller.ts` do repositório oficial). Inferências concretas que precisam ser validadas contra payload real:
  - Estrutura interna de `data` (`key.remoteJid`, `key.id`, `key.fromMe`, `pushName`, `message.conversation`, `message.extendedTextMessage.text`) — formato Baileys.
  - **Device suffix `:N` no `remoteJid` para sender em Multi-Device** (formato `5511999990000:1@s.whatsapp.net`) — assumido a partir da convenção Baileys/protocolo Multi-Device do WhatsApp; removido pelo `MessagePayloadNormalizer` antes da validação E.164. Sem esse tratamento, mensagem de device > 1 (ex. WhatsApp Web) cairia em `JidType.UNKNOWN` e seria descartada.
- **Plano de mitigação:** Antes da ativação do primeiro cliente, capturar 5+ payloads `messages.upsert` reais de uma instância Evolution + WhatsApp Web rodando, comparar campo a campo contra os DTOs, ajustar divergências, e documentar o resultado da validação neste item (mudando o Status para Mitigado). Critério de fechamento: todos os campos usados pelo `WebhookService` (instance, key.id, key.remoteJid, conteúdo de texto, fromMe) confirmados contra payloads reais.
- **Detectado em:** Camada 2 (webhook), durante o desenho dos DTOs.

---

## Secret de webhook em query param (?apikey=) pode vazar em access-log de proxy

- **Status:** Aceito (trade-off)
- **Bloqueante para:** N/A — mitigado por estratégia documentada
- **Razão:** Evolution self-hosted (vide issues #1933/#2276) não garante suporte a header customizado em webhook outbound. Para robustez, o filter aceita o secret em duas fontes: header `apikey` (preferencial) e query param `apikey` (fallback). Quando o secret cai em query param, proxies podem logar a URL completa no access-log, expondo o secret.
- **Plano de mitigação:** (a) Header tem precedência — filter só lê query param se header ausente; configurar Evolution para usar header quando a versão alvo suportar. (b) Configurar proxy/CDN do meada.app para NÃO logar query string em `/webhooks/*` no access-log (Nginx `log_format` sem `$query_string`, ou exclusão por location). (c) Quando confirmarmos que a versão Evolution alvo suporta header confirmado, remover o fallback de query param desta camada.
- **Detectado em:** Camada 2 (webhook), durante desenho do `WebhookSecretFilter`.
