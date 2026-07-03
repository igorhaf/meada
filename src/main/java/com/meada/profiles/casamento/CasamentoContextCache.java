package com.meada.profiles.casamento;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.casamento.catalog.WeddingCatalogItem;
import com.meada.profiles.casamento.catalog.WeddingCatalogRepository;
import com.meada.profiles.casamento.payments.WeddingPayment;
import com.meada.profiles.casamento.payments.WeddingPaymentRepository;
import com.meada.profiles.casamento.planners.WeddingPlanner;
import com.meada.profiles.casamento.planners.WeddingPlannerRepository;
import com.meada.profiles.casamento.proposals.WeddingProposal;
import com.meada.profiles.casamento.proposals.WeddingProposalRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do CasamentoBot (camada 8.7). TTL 20s
 * (espelho eventos — a proposta não muda a cada segundo). Keyed por {@code (companyId, contactId)}.
 * Conteúdo:
 * <ul>
 *   <li>assessores/cerimonialistas ativos (id + nome) — pra IA referenciar planner_id ao abrir proposta;
 *   <li>PROPOSTAS do cliente em aberto (rascunho/orcada) com id + estilo + data + status + total —
 *       pra IA capturar a APROVAÇÃO referenciando a proposta ORÇADA certa (gate de 2 fases).
 * </ul>
 * + instruções e as 2 tags ({@code <proposta_casamento>} e {@code <aprovacao_casamento>}). NÃO injeta
 * o cronograma do dia NEM o checklist pré-casamento (organizacionais do painel). Espelho do
 * EventosContextCache.
 */
@Component
public class CasamentoContextCache {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private final WeddingPlannerRepository plannerRepository;
    private final WeddingProposalRepository proposalRepository;
    private final WeddingCatalogRepository catalogRepository;
    private final WeddingPaymentRepository paymentRepository;
    private final Cache<String, String> cache;

    public CasamentoContextCache(WeddingPlannerRepository plannerRepository,
                                 WeddingProposalRepository proposalRepository,
                                 WeddingCatalogRepository catalogRepository,
                                 WeddingPaymentRepository paymentRepository) {
        this.plannerRepository = plannerRepository;
        this.proposalRepository = proposalRepository;
        this.catalogRepository = catalogRepository;
        this.paymentRepository = paymentRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(20))
            .maximumSize(1000)
            .build();
    }

    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de assessor/proposta/item/config). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static String brl(int cents) {
        return "R$ " + String.format("%d,%02d", cents / 100, cents % 100);
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        StringBuilder sb = new StringBuilder();

        // --- ASSESSORES / CERIMONIALISTAS ---
        List<WeddingPlanner> planners = plannerRepository.listByCompany(companyId, true);
        if (planners.isEmpty()) {
            sb.append("ASSESSORES: (nenhum ativo no momento.)\n\n");
        } else {
            sb.append("ASSESSORES (use o planner_id EXATO; atribuição é OPCIONAL):\n");
            for (WeddingPlanner p : planners) {
                sb.append("- ").append(p.id()).append(" · ").append(p.name());
                if (p.specialty() != null && !p.specialty().isBlank()) {
                    sb.append(" (").append(p.specialty()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- CATÁLOGO DE PACOTES + ADICIONAIS (onda 1, backlog #3) — preço DO CATÁLOGO ---
        List<WeddingCatalogItem> catalog = catalogRepository.listByCompany(companyId, true);
        if (!catalog.isEmpty()) {
            sb.append("PACOTES E ADICIONAIS DO CATÁLOGO (preços OFICIAIS do catálogo — apresente-os "
                + "quando fizer sentido no briefing; quem fecha o orçamento é a equipe):\n");
            for (WeddingCatalogItem item : catalog) {
                sb.append("- [").append("pacote".equals(item.kind()) ? "PACOTE" : "ADICIONAL").append("] ")
                    .append(item.name()).append(": ").append(brl(item.priceCents()));
                if (item.description() != null && !item.description().isBlank()) {
                    sb.append(" — ").append(item.description());
                }
                sb.append("\n");
            }
            sb.append("Você PODE sugerir NO MÁXIMO UMA VEZ um ADICIONAL desta lista quando combinar com o "
                + "briefing (sem insistir se recusarem). NUNCA cite pacote/valor fora do catálogo, NUNCA dê "
                + "desconto.\n\n");
        }

        // --- PROPOSTAS DO CLIENTE EM ABERTO (pra capturar aprovação) ---
        if (contactId != null) {
            List<WeddingProposal> openProposals = proposalRepository.listByCompany(companyId, null, null,
                contactId, null, null, 20, 0);
            StringBuilder block = new StringBuilder();
            for (WeddingProposal p : openProposals) {
                // plano de pagamento (#1): a IA INFORMA valor/vencimento/status — nunca inventa condição.
                if ("aprovada".equals(p.status()) || "fechada".equals(p.status())) {
                    List<WeddingPayment> plan = paymentRepository.listByProposal(companyId, p.id());
                    if (!plan.isEmpty()) {
                        block.append("- ").append(p.id())
                            .append(" · ").append(p.weddingStyle() == null ? "casamento" : p.weddingStyle())
                            .append(p.weddingDate() == null ? "" : " em " + p.weddingDate())
                            .append(" · ").append(p.status().toUpperCase()).append(" · PLANO DE PAGAMENTO:\n");
                        for (WeddingPayment pay : plan) {
                            block.append("    · ")
                                .append(pay.label() != null && !pay.label().isBlank() ? pay.label()
                                    : ("sinal".equals(pay.kind()) ? "Sinal" : "Parcela"))
                                .append(" — ").append(brl(pay.amountCents()))
                                .append(" — vence ").append(DAY_MONTH.format(pay.dueDate()))
                                .append(pay.paid() ? " — PAGO" : " — em aberto").append("\n");
                        }
                        block.append("  Se perguntarem, INFORME estes valores/vencimentos exatamente como "
                            + "estão. Você NÃO confirma pagamento nem negocia condição — a equipe cuida "
                            + "disso.\n");
                    }
                }
                if ("orcada".equals(p.status())) {
                    block.append("- ").append(p.id())
                        .append(" · ").append(p.weddingStyle() == null ? "casamento" : p.weddingStyle())
                        .append(p.weddingDate() == null ? "" : " em " + p.weddingDate())
                        .append(" · ORÇADA · total ").append(brl(p.totalCents() - p.discountCents()))
                        .append(" (aguardando aprovação dos noivos)\n");
                } else if ("rascunho".equals(p.status())) {
                    block.append("- ").append(p.id())
                        .append(" · ").append(p.weddingStyle() == null ? "casamento" : p.weddingStyle())
                        .append(p.weddingDate() == null ? "" : " em " + p.weddingDate())
                        .append(" · RASCUNHO (ainda sem orçamento)\n");
                }
            }
            if (block.length() > 0) {
                sb.append("PROPOSTAS DO CLIENTE EM ABERTO:\n").append(block)
                    .append("Quando os noivos responderem se aprovam/recusam um ORÇAMENTO, use a tag "
                        + "<aprovacao_casamento> com o proposal_id da proposta ORÇADA correspondente.\n\n");
            }
        } else {
            sb.append("CLIENTE NÃO IDENTIFICADO pelo telefone. Peça os dados do casamento (estilo, data "
                + "prevista, número de convidados) para abrir a proposta.\n\n");
        }

        // --- INSTRUÇÕES + TAGS ---
        sb.append("INSTRUÇÕES:\n")
            .append("Você ABRE a proposta a partir do briefing dos noivos (estilo do casamento, data prevista, "
                + "número de convidados, o que eles sonham). NÃO fecha contrato, preço ou desconto — quem orça e "
                + "fecha é a equipe no painel. NÃO confirma disponibilidade de data não confirmada ('vou verificar "
                + "a disponibilidade com a equipe'). NUNCA invente item de pacote, valor ou serviço, nem prometa "
                + "estrutura do espaço não informada.\n")
            .append("Para ABRIR uma proposta, sua ÚLTIMA mensagem deve TERMINAR com a tag (linha própria, "
                + "sem markdown):\n")
            .append("<proposta_casamento>{\"wedding_style\":\"...\",\"wedding_date\":\"YYYY-MM-DD|null\","
                + "\"guest_count\":N|null,\"briefing\":\"...\",\"planner_id\":\"UUID|null\",\"notes\":\"...\"}"
                + "</proposta_casamento>\n")
            .append("Para CAPTURAR a resposta dos noivos a um ORÇAMENTO (proposta já orçada), termine com:\n")
            .append("<aprovacao_casamento>{\"proposal_id\":\"UUID\",\"decisao\":\"aprovada|recusada\"}"
                + "</aprovacao_casamento>\n")
            .append("Use ids EXATOS. Só emita a tag de aprovação se houver uma proposta ORÇADA do cliente.\n\n");

        return sb.toString();
    }
}
