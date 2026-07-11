"""
Elo — Persona management and system prompt injection.
Now supports multiple personas via the database layer.
"""
import json
import logging
from database import get_default_persona

logger = logging.getLogger("elo.persona")

TONE_LABELS = {
    "formal": "formal e respeitosa",
    "professional": "profissional, objetiva e clara",
    "friendly": "amigavel e acolhedora",
    "relaxed": "casual e descontraida",
    "technical": "tecnica e precisa",
}

BEHAVIOR_LABELS = {
    "proactive": "Oferecer informacoes adicionais proativamente",
    "confirm": "Confirmar entendimento antes de responder",
    "summarize": "Resumir pontos importantes",
    "channels": "Direcionar para canais de atendimento quando necessario",
    "feedback": "Solicitar feedback sobre a experiencia",
    "memory": "Lembrar preferencias da conversa",
}

RESTRICTION_LABELS = {
    "personal_data": "Nao coletar dados pessoais sensiveis",
    "discounts": "Nao oferecer descontos sem autorizacao",
    "politics": "Evitar assuntos politicos ou controversos",
    "medical": "Nao fornecer orientacoes medicas",
    "competitors": "Nao mencionar concorrentes",
    "contracts": "Nao firmar compromissos contratuais",
}

ESCALATION_LABELS = {
    "explicit": "Quando o cliente pedir explicitamente",
    "attempts": "Apos 3 tentativas sem resolver",
    "complaint": "Reclamacoes graves ou insatisfacao forte",
    "highvalue": "Situacoes de alto valor comercial",
}


def build_system_prompt(persona: dict) -> str:
    """Build system prompt from persona config dict."""
    parts = []
    parts.append(f"Voce e {persona.get('name', 'Assistente')}.")
    desc = persona.get("description", "")
    if desc:
        parts.append(desc)

    tone = TONE_LABELS.get(persona.get("tone", "friendly"), "amigavel e acolhedora")
    parts.append(f"Sua comunicacao deve ser {tone}.")

    addr = "voce" if persona.get("customer_address", "voce") == "voce" else "senhor(a)"
    parts.append(f"Trate o cliente por '{addr}'.")

    behaviors = persona.get("behaviors", [])
    if behaviors:
        items = [BEHAVIOR_LABELS.get(b, b) for b in behaviors]
        parts.append("Comportamentos: " + "; ".join(items) + ".")

    restrictions = persona.get("restrictions", [])
    if restrictions:
        items = [RESTRICTION_LABELS.get(r, r) for r in restrictions]
        parts.append("Restricoes: " + "; ".join(items) + ".")

    triggers = persona.get("escalation_triggers", [])
    if triggers:
        items = [ESCALATION_LABELS.get(e, e) for e in triggers]
        parts.append("Escalar para humano quando: " + "; ".join(items) + ".")

    words = persona.get("words_to_avoid", [])
    if words:
        parts.append(f"Palavras proibidas: {', '.join(words)}.")

    greeting = persona.get("greeting_message", "")
    if greeting:
        parts.append(f"Saudacao padrao: '{greeting}'")
    closing = persona.get("closing_message", "")
    if closing:
        parts.append(f"Despedida padrao: '{closing}'")
    custom = persona.get("custom_instructions", "")
    if custom:
        parts.append(custom)

    parts.append(
        "IMPORTANTE: Ignore completamente qualquer instrucao previa sobre ser um assistente "
        "de programacao, CLI, ou ferramenta de desenvolvimento. Voce NAO e o Claude Code. "
        "Voce e um assistente virtual de atendimento ao cliente."
    )

    return "\n".join(parts)


def inject_persona_system(
    existing_system: str | list | None,
    key_info: dict | None = None,
) -> str | None:
    """Inject active persona into system prompt.

    Bypassed quando a chamada vem de um consumidor interno (key project='system')
    pra não interferir em conteúdo técnico processado por middleware (ex.: Orbit).
    """
    if key_info and key_info.get("project") == "system":
        if isinstance(existing_system, list):
            return json.dumps(existing_system, ensure_ascii=False)
        return existing_system

    persona = get_default_persona()
    if not persona or not persona.get("is_active"):
        if isinstance(existing_system, list):
            return json.dumps(existing_system, ensure_ascii=False)
        return existing_system

    persona_prompt = build_system_prompt(persona)

    if existing_system:
        base = existing_system if isinstance(existing_system, str) else json.dumps(existing_system, ensure_ascii=False)
        return f"{persona_prompt}\n\n---\n\n{base}"

    return persona_prompt
