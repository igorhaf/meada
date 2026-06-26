package com.meada.whatsapp.profiles.concessionaria.leads;

import com.meada.whatsapp.profiles.concessionaria.ConcessionariaContextCache;
import com.meada.whatsapp.profiles.concessionaria.LeadStatus;
import com.meada.whatsapp.profiles.concessionaria.salespeople.ConcessionariaSalespersonRepository;
import com.meada.whatsapp.profiles.concessionaria.vehicles.ConcessionariaVehicle;
import com.meada.whatsapp.profiles.concessionaria.vehicles.ConcessionariaVehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos leads de compra (camada 8.17).
 *
 * <p>{@link #createLead} valida que o VEÍCULO está 'disponivel' (→ 422 vehicle_not_available), valida
 * a condição de pagamento (avista|financiado; default avista; inválida → reject), tira snapshots de
 * marca/modelo/ano + PREÇO do veículo (do CATÁLOGO — NUNCA da tag) + nome/telefone do cliente, e cria
 * o lead em 'novo'. A IA cria e NÃO move — a equipe trabalha o funil.
 *
 * <p>{@link #updateStatus} valida a transição (→ 409 se inválida); lost_reason ao mover p/ 'perdido'.
 * SEM auto-notificação nesta SM. {@link #assignSalesperson} é um UPDATE simples.
 */
@Service
public class ConcessionariaLeadService {

    private final ConcessionariaLeadRepository leadRepository;
    private final ConcessionariaVehicleRepository vehicleRepository;
    private final ConcessionariaSalespersonRepository salespersonRepository;
    private final ConcessionariaContextCache contextCache;

    public ConcessionariaLeadService(ConcessionariaLeadRepository leadRepository,
                                     ConcessionariaVehicleRepository vehicleRepository,
                                     ConcessionariaSalespersonRepository salespersonRepository,
                                     ConcessionariaContextCache contextCache) {
        this.leadRepository = leadRepository;
        this.vehicleRepository = vehicleRepository;
        this.salespersonRepository = salespersonRepository;
        this.contextCache = contextCache;
    }

    public static class LeadNotFoundException extends RuntimeException {}
    public static class VehicleNotFoundException extends RuntimeException {}
    public static class VehicleNotAvailableException extends RuntimeException {}
    public static class InvalidPaymentConditionException extends RuntimeException {}
    public static class SalespersonNotFoundException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    private static String normalizePaymentCondition(String raw) {
        String pc = raw == null || raw.isBlank() ? "avista" : raw.trim();
        if (!"avista".equals(pc) && !"financiado".equals(pc)) {
            throw new InvalidPaymentConditionException();
        }
        return pc;
    }

    /**
     * Cria um lead (status inicial novo). Pré-condição: veículo 'disponivel'. Snapshots de veículo +
     * PREÇO do catálogo (nunca da tag) + cliente. Invalida o cache de contexto da IA.
     */
    @Transactional
    public ConcessionariaLead createLead(UUID companyId, LeadInput input) {
        ConcessionariaVehicle vehicle = vehicleRepository.findById(companyId, input.vehicleId())
            .orElseThrow(VehicleNotFoundException::new);
        if (!vehicle.active() || !"disponivel".equals(vehicle.status())) {
            throw new VehicleNotAvailableException();
        }
        String paymentCondition = normalizePaymentCondition(input.paymentCondition());

        String customerName = leadRepository.contactName(companyId, input.contactId()).orElse(null);
        String customerPhone = leadRepository.contactPhone(companyId, input.contactId()).orElse(null);

        ConcessionariaLead created = leadRepository.insertLead(companyId, vehicle.id(),
            input.conversationId(), input.contactId(), customerName, customerPhone, vehicle.brand(),
            vehicle.model(), vehicle.modelYear(), vehicle.priceCents(), paymentCondition, input.notes());
        contextCache.invalidate(companyId);
        return created;
    }

    public List<ConcessionariaLead> list(UUID companyId, String status, UUID vehicleId, UUID contactId,
                                         UUID salespersonId, int limit, int offset) {
        return leadRepository.listByCompany(companyId, status, vehicleId, contactId, salespersonId, limit, offset);
    }

    public long count(UUID companyId, String status, UUID vehicleId, UUID contactId, UUID salespersonId) {
        return leadRepository.countByCompany(companyId, status, vehicleId, contactId, salespersonId);
    }

    public Optional<ConcessionariaLead> get(UUID companyId, UUID id) {
        return leadRepository.findById(companyId, id);
    }

    /** Transiciona o status do funil. Valida o alvo (enum) e a transição. lost_reason ao 'perdido'. */
    @Transactional
    public ConcessionariaLead updateStatus(UUID companyId, UUID id, String newStatusId, String lostReason) {
        LeadStatus newStatus = LeadStatus.fromId(newStatusId).orElseThrow(InvalidStatusException::new);
        ConcessionariaLead current = leadRepository.findById(companyId, id).orElseThrow(LeadNotFoundException::new);
        LeadStatus from = LeadStatus.fromId(current.status()).orElseThrow(InvalidStatusException::new);
        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }
        String reason = newStatus == LeadStatus.PERDIDO ? lostReason : current.lostReason();
        ConcessionariaLead updated = leadRepository.updateStatus(companyId, id, newStatus.id(), reason)
            .orElseThrow(LeadNotFoundException::new);
        contextCache.invalidate(companyId);
        return updated;
    }

    /** Atribui o vendedor ao lead (UPDATE). salespersonId null desvincula. */
    @Transactional
    public ConcessionariaLead assignSalesperson(UUID companyId, UUID id, UUID salespersonId) {
        if (salespersonId != null
                && salespersonRepository.findById(companyId, salespersonId).isEmpty()) {
            // existência basta (vendedor inativo ainda pode ser dono histórico de um lead).
            throw new SalespersonNotFoundException();
        }
        ConcessionariaLead updated = leadRepository.assignSalesperson(companyId, id, salespersonId)
            .orElseThrow(LeadNotFoundException::new);
        contextCache.invalidate(companyId);
        return updated;
    }
}
