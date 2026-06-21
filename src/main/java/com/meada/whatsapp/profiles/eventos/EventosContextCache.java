package com.meada.whatsapp.profiles.eventos;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.whatsapp.profiles.eventos.planners.EventPlanner;
import com.meada.whatsapp.profiles.eventos.planners.EventPlannerRepository;
import com.meada.whatsapp.profiles.eventos.proposals.EventProposal;
import com.meada.whatsapp.profiles.eventos.proposals.EventProposalRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do EventosBot (camada 8.2). TTL 20s
 * (espelho oficina — a proposta não muda a cada segundo como a fila da barbearia). Keyed por
 * {@code (companyId, contactId)}. Conteúdo:
 * <ul>
 *   <li>cerimonialistas ativos (id + nome) — pra IA referenciar planner_id ao abrir proposta;
 *   <li>PROPOSTAS do cliente em aberto (rascunho/orcada) com id + tipo + data + status + total —
 *       pra IA capturar a APROVAÇÃO referenciando a proposta ORÇADA certa (gate de 2 fases).
 * </ul>
 * + instruções e as 2 tags ({@code <proposta_evento>} e {@code <aprovacao_proposta>}). NÃO injeta
 * o cronograma inteiro (organizacional do painel).
 */
@Component
public class EventosContextCache {

    private final EventPlannerRepository plannerRepository;
    private final EventProposalRepository proposalRepository;
    private final Cache<String, String> cache;

    public EventosContextCache(EventPlannerRepository plannerRepository,
                               EventProposalRepository proposalRepository) {
        this.plannerRepository = plannerRepository;
        this.proposalRepository = proposalRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(20))
            .maximumSize(1000)
            .build();
    }

    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de cerimonialista/proposta/item/config). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static String brl(int cents) {
        return "R$ " + String.format("%d,%02d", cents / 100, cents % 100);
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        StringBuilder sb = new StringBuilder();

        // --- CERIMONIALISTAS ---
        List<EventPlanner> planners = plannerRepository.listByCompany(companyId, true);
        if (planners.isEmpty()) {
            sb.append("CERIMONIALISTAS: (nenhum ativo no momento.)\n\n");
        } else {
            sb.append("CERIMONIALISTAS (use o planner_id EXATO; atribuição é OPCIONAL):\n");
            for (EventPlanner p : planners) {
                sb.append("- ").append(p.id()).append(" · ").append(p.name());
                if (p.specialty() != null && !p.specialty().isBlank()) {
                    sb.append(" (").append(p.specialty()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- PROPOSTAS DO CLIENTE EM ABERTO (pra capturar aprovação) ---
        if (contactId != null) {
            List<EventProposal> openProposals = proposalRepository.listByCompany(companyId, null, null,
                contactId, null, null, 20, 0);
            StringBuilder block = new StringBuilder();
            for (EventProposal p : openProposals) {
                if ("orcada".equals(p.status())) {
                    block.append("- ").append(p.id())
                        .append(" · ").append(p.eventType() == null ? "evento" : p.eventType())
                        .append(p.eventDate() == null ? "" : " em " + p.eventDate())
                        .append(" · ORÇADA · total ").append(brl(p.totalCents()))
                        .append(" (aguardando aprovação do cliente)\n");
                } else if ("rascunho".equals(p.status())) {
                    block.append("- ").append(p.id())
                        .append(" · ").append(p.eventType() == null ? "evento" : p.eventType())
                        .append(p.eventDate() == null ? "" : " em " + p.eventDate())
                        .append(" · RASCUNHO (ainda sem orçamento)\n");
                }
            }
            if (block.length() > 0) {
                sb.append("PROPOSTAS DO CLIENTE EM ABERTO:\n").append(block)
                    .append("Quando o cliente responder se aprova/recusa um ORÇAMENTO, use a tag "
                        + "<aprovacao_proposta> com o proposal_id da proposta ORÇADA correspondente.\n\n");
            }
        } else {
            sb.append("CLIENTE NÃO IDENTIFICADO pelo telefone. Peça os dados do evento (tipo, data, "
                + "número de convidados) para abrir a proposta.\n\n");
        }

        // --- INSTRUÇÕES + TAGS ---
        sb.append("INSTRUÇÕES:\n")
            .append("Você ABRE a proposta a partir do briefing do cliente (tipo de evento, data prevista, "
                + "número de convidados, o que ele imagina). NÃO fecha contrato, preço ou desconto — quem "
                + "orça e fecha é a equipe no painel. NÃO confirma disponibilidade de data não confirmada "
                + "('vou verificar a disponibilidade com a equipe'). NUNCA invente item de pacote, valor "
                + "ou serviço, nem prometa estrutura do espaço não informada.\n")
            .append("Para ABRIR uma proposta, sua ÚLTIMA mensagem deve TERMINAR com a tag (linha própria, "
                + "sem markdown):\n")
            .append("<proposta_evento>{\"event_type\":\"...\",\"event_date\":\"YYYY-MM-DD|null\","
                + "\"guest_count\":N|null,\"briefing\":\"...\",\"planner_id\":\"UUID|null\",\"notes\":\"...\"}"
                + "</proposta_evento>\n")
            .append("Para CAPTURAR a resposta do cliente a um ORÇAMENTO (proposta já orçada), termine com:\n")
            .append("<aprovacao_proposta>{\"proposal_id\":\"UUID\",\"decisao\":\"aprovada|recusada\"}"
                + "</aprovacao_proposta>\n")
            .append("Use ids EXATOS. Só emita a tag de aprovação se houver uma proposta ORÇADA do cliente.\n\n");

        return sb.toString();
    }
}
