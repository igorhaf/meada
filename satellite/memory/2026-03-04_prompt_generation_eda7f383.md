# prompt_generation — 2026-03-04

**Model:** ollama/qwen3:14b
**Status:** success
**Tokens:** 533 in / 655 out | Cost: $0.0038

## System Prompt

Você é um Product Owner especialista em decomposição de software.
Gere até 1 épicos de software para o projeto.

## REGRAS CRÍTICAS:

1. Gere entre 1 e 1 épicos nesta resposta (quanto mais relevantes, melhor)
2. NÃO repita épicos já gerados em batches anteriores (lista abaixo)
3. NÃO sugira épicos para funcionalidades que JÁ EXISTEM no código
4. Se não houver mais épicos RELEVANTES a sugerir, retorne "has_more": false com a lista parcial
5. Responda APENAS com JSON válido, sem texto adicional

## ÉPICOS JÁ GERADOS (NÃO REPETIR):
Nenhum ainda

## FUNCIONALIDADES JÁ EXISTENTES NO CÓDIGO (NÃO CRIAR ÉPICOS PARA ESTAS):
Nenhuma

## FORMATO DE RESPOSTA:
```json
{
    "epics": [
        {
            "title": "Título claro e conciso",
            "description": "Descrição detalhada do módulo (3-5 frases cobrindo escopo, objetivo e principais funcionalidades)",
            "priority": "high|medium|low"
        }
    ],
    "has_more": true
}
```

Se não houver mais épicos relevantes, retorne os que encontrar com "has_more": false.
Retorne lista vazia SOMENTE se realmente não existir nenhum épico novo a sugerir.

IMPORTANTE:
- Foque em: integrações, automações, melhorias de UX, relatórios, APIs, segurança
- Prioridades: high (essencial), medium (importante), low (nice-to-have)
- Cada description deve ter NO MÍNIMO 200 caracteres com detalhes do escopo
- Seja específico e prático

## User Prompt

## Projeto: Site intitucional Meada

## Descrição do Sistema:
Este projeto foi analisado em múltiplas fases. Foram encontradas 0 regras de negócio e 0 funcionalidades.

## Tarefa
Gere o lote 1 de 1 épicos para NOVAS funcionalidades.
Lembre-se: não repita épicos já gerados e não sugira funcionalidades existentes.

## Response

{
    "epics": [
        {
            "title": "Integrated Contact Management System",
            "description": "Implementar um sistema de gestão de contatos integrado a um CRM, permitindo o cadastro de leads, automação de follow-ups via e-mail e relatórios de engajamento. O módulo incluirá formulários personalizáveis, integração com ferramentas de automação de marketing e dashboards de análise de conversões, visando aumentar a eficiência no atendimento a clientes e a conversão de leads em negócios.",
            "priority": "high"
        }
    ],
    "has_more": true
}
