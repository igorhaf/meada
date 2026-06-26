package com.meada.whatsapp.profiles.concessionaria.testdrives;

import com.meada.whatsapp.profiles.concessionaria.ConcessionariaContextCache;
import com.meada.whatsapp.profiles.concessionaria.TestDriveStatus;
import com.meada.whatsapp.profiles.concessionaria.config.ConcessionariaConfig;
import com.meada.whatsapp.profiles.concessionaria.config.ConcessionariaConfigRepository;
import com.meada.whatsapp.profiles.concessionaria.salespeople.ConcessionariaSalesperson;
import com.meada.whatsapp.profiles.concessionaria.salespeople.ConcessionariaSalespersonRepository;
import com.meada.whatsapp.profiles.concessionaria.vehicles.ConcessionariaVehicle;
import com.meada.whatsapp.profiles.concessionaria.vehicles.ConcessionariaVehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos test-drives (camada 8.17).
 *
 * <p>{@link #createTestDrive} valida que o VEÍCULO está 'disponivel' E ativo (→ 422
 * vehicle_not_available), que o vendedor existe e está ativo, que o test-drive CABE na janela de
 * funcionamento (no fuso do tenant), lê a duração do config (snapshot), tira snapshots de
 * marca/modelo/ano do veículo + nome do cliente, e delega ao repositório — que re-verifica o conflito
 * (POR salesperson_id) DENTRO da transação. SAME horário + vendedor DIFERENTE → OK.
 *
 * <p>{@link #updateStatus} valida a transição (→ 409 se inválida) e dispara a notificação outbound do
 * novo status via {@link ConcessionariaTestDriveNotifier}, best-effort.
 *
 * <p>Fuso HARDCODED America/Sao_Paulo (pendência conhecida, igual ao resto dos perfis).
 */
@Service
public class ConcessionariaTestDriveService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ConcessionariaTestDriveRepository testDriveRepository;
    private final ConcessionariaVehicleRepository vehicleRepository;
    private final ConcessionariaSalespersonRepository salespersonRepository;
    private final ConcessionariaConfigRepository configRepository;
    private final ConcessionariaTestDriveNotifier notifier;
    private final ConcessionariaContextCache contextCache;

    public ConcessionariaTestDriveService(ConcessionariaTestDriveRepository testDriveRepository,
                                          ConcessionariaVehicleRepository vehicleRepository,
                                          ConcessionariaSalespersonRepository salespersonRepository,
                                          ConcessionariaConfigRepository configRepository,
                                          ConcessionariaTestDriveNotifier notifier,
                                          ConcessionariaContextCache contextCache) {
        this.testDriveRepository = testDriveRepository;
        this.vehicleRepository = vehicleRepository;
        this.salespersonRepository = salespersonRepository;
        this.configRepository = configRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    /** Test-drive não encontrado / de outro tenant (→ 404). */
    public static class TestDriveNotFoundException extends RuntimeException {}

    /** Veículo inexistente / de outro tenant (→ 404 vehicle_not_found). */
    public static class VehicleNotFoundException extends RuntimeException {}

    /** Veículo não está 'disponivel'/ativo (→ 422 vehicle_not_available). */
    public static class VehicleNotAvailableException extends RuntimeException {}

    /** Vendedor inexistente / de outro tenant (→ 404 salesperson_not_found). */
    public static class SalespersonNotFoundException extends RuntimeException {}

    /** Vendedor inativo (→ 422 inactive_salesperson). */
    public static class InactiveSalespersonException extends RuntimeException {}

    /** Test-drive fora do horário de funcionamento (→ 400 outside_hours). */
    public static class OutsideHoursException extends RuntimeException {}

    /** Status alvo desconhecido (→ 400 invalid_status). */
    public static class InvalidStatusException extends RuntimeException {}

    /** Transição de status inválida (→ 409 invalid_status_transition). */
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Conflito de slot (→ 409 conflict_slot). Carrega o conflito p/ o controller expor detalhes. */
    public static class ConflictException extends RuntimeException {
        private final transient TestDriveConflict conflict;

        public ConflictException(TestDriveConflict conflict) {
            this.conflict = conflict;
        }

        public TestDriveConflict conflict() {
            return conflict;
        }
    }

    /**
     * Cria um test-drive (status inicial agendado). Pré-condição: veículo 'disponivel' E ativo. Valida
     * vendedor (ativo) + janela de funcionamento; a duração vem do config (snapshot). O repositório
     * re-verifica o conflito (por vendedor) na transação. Invalida o cache de contexto da IA.
     */
    @Transactional
    public ConcessionariaTestDrive createTestDrive(UUID companyId, TestDriveInput input) {
        ConcessionariaVehicle vehicle = vehicleRepository.findById(companyId, input.vehicleId())
            .orElseThrow(VehicleNotFoundException::new);
        if (!vehicle.active() || !"disponivel".equals(vehicle.status())) {
            throw new VehicleNotAvailableException();
        }
        ConcessionariaSalesperson salesperson = salespersonRepository.findById(companyId, input.salespersonId())
            .orElseThrow(SalespersonNotFoundException::new);
        if (!salesperson.active()) {
            throw new InactiveSalespersonException();
        }
        ConcessionariaConfig config = configRepository.findByCompany(companyId);
        requireInsideHours(input.startAt(), config);

        String customerName = testDriveRepository.contactName(companyId, input.contactId()).orElse(null);

        ConcessionariaTestDrive created;
        try {
            created = testDriveRepository.insertTestDrive(companyId, vehicle.id(), salesperson.id(),
                input.conversationId(), input.contactId(), customerName, vehicle.brand(), vehicle.model(),
                vehicle.modelYear(), input.startAt(), config.durationMinutes(), input.notes());
        } catch (ConcessionariaTestDriveRepository.SlotConflictException e) {
            throw new ConflictException(e.conflict());
        }
        contextCache.invalidate(companyId);
        return created;
    }

    public List<ConcessionariaTestDrive> list(UUID companyId, String status, Instant dateFrom,
                                              Instant dateTo, UUID salespersonId, UUID vehicleId,
                                              int limit, int offset) {
        return testDriveRepository.listByCompany(companyId, status, dateFrom, dateTo, salespersonId,
            vehicleId, limit, offset);
    }

    public long count(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                      UUID salespersonId, UUID vehicleId) {
        return testDriveRepository.countByCompany(companyId, status, dateFrom, dateTo, salespersonId, vehicleId);
    }

    public Optional<ConcessionariaTestDrive> get(UUID companyId, UUID id) {
        return testDriveRepository.findById(companyId, id);
    }

    /**
     * Transiciona o status. Valida o alvo (enum) e a transição. Persiste e notifica o cliente com o
     * texto do novo status (confirmado com veículo+vendedor+data; cancelado defensivo). A notificação
     * é best-effort (não reverte).
     */
    @Transactional
    public ConcessionariaTestDrive updateStatus(UUID companyId, UUID id, String newStatusId) {
        TestDriveStatus newStatus = TestDriveStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        ConcessionariaTestDrive current = testDriveRepository.findById(companyId, id)
            .orElseThrow(TestDriveNotFoundException::new);
        TestDriveStatus from = TestDriveStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        testDriveRepository.updateStatus(companyId, id, newStatus.id());

        ZonedDateTime z = current.startAt().atZone(TENANT_ZONE);
        String whenLabel = DATE_FMT.format(z) + " " + TIME_FMT.format(z);
        String salespersonName = salespersonRepository.findById(companyId, current.salespersonId())
            .map(ConcessionariaSalesperson::name).orElse("nosso vendedor");
        String text = newStatus.notificationText(vehicleLabel(current), salespersonName, whenLabel);
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return testDriveRepository.findById(companyId, id).orElseThrow(TestDriveNotFoundException::new);
    }

    private static String vehicleLabel(ConcessionariaTestDrive td) {
        StringBuilder sb = new StringBuilder();
        if (td.vehicleBrand() != null && !td.vehicleBrand().isBlank()) {
            sb.append(td.vehicleBrand()).append(" ");
        }
        sb.append(td.vehicleModel() == null ? "" : td.vehicleModel());
        if (td.vehicleYear() != null) {
            sb.append(" ").append(td.vehicleYear());
        }
        return sb.toString().trim();
    }

    /** Valida que o test-drive inteiro (início → início+duração) cabe na janela, no fuso do tenant. */
    private void requireInsideHours(Instant startAt, ConcessionariaConfig config) {
        ZonedDateTime start = startAt.atZone(TENANT_ZONE);
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = start.plusMinutes(config.durationMinutes()).toLocalTime();
        boolean startsOk = !startTime.isBefore(config.opensAt());
        boolean endsOk = !endTime.isAfter(config.closesAt()) && !endTime.isBefore(startTime);
        if (!startsOk || !endsOk) {
            throw new OutsideHoursException();
        }
    }
}
