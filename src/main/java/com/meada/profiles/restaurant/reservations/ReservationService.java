package com.meada.profiles.restaurant.reservations;

import com.meada.profiles.restaurant.ReservationContextCache;
import com.meada.profiles.restaurant.ReservationStatus;
import com.meada.profiles.restaurant.config.RestaurantReservationConfig;
import com.meada.profiles.restaurant.config.RestaurantReservationConfigRepository;
import com.meada.profiles.restaurant.tables.RestaurantTableRepository;
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
 * Regras das reservas (camada 7.3).
 *
 * <p>{@link #create} valida que a mesa existe e que a reserva CABE na janela de funcionamento do
 * restaurante (opens_at ≤ início E fim ≤ closes_at, no fuso do tenant), lê a duração do config
 * (snapshot), e delega ao repositório — que re-verifica o conflito DENTRO da transação (decisão 5).
 *
 * <p>{@link #updateStatus} valida a transição (decisão 2 → 409 se inválida) e dispara a notificação
 * outbound do novo status (decisão 3) via {@link ReservationNotifier}, best-effort.
 *
 * <p>Fuso HARDCODED America/Sao_Paulo (pendência conhecida, igual ao OutboundService) — o horário
 * de funcionamento e os rótulos da notificação são avaliados nesse fuso.
 */
@Service
public class ReservationService {

    /** Fuso do tenant (BR) para avaliar janela de funcionamento e formatar rótulos. HARDCODED no MVP. */
    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ReservationRepository reservationRepository;
    private final RestaurantTableRepository tableRepository;
    private final RestaurantReservationConfigRepository configRepository;
    private final ReservationNotifier notifier;
    private final ReservationContextCache contextCache;

    public ReservationService(ReservationRepository reservationRepository,
                              RestaurantTableRepository tableRepository,
                              RestaurantReservationConfigRepository configRepository,
                              ReservationNotifier notifier,
                              ReservationContextCache contextCache) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.configRepository = configRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    /** Reserva não encontrada / de outro tenant (→ 404). */
    public static class ReservationNotFoundException extends RuntimeException {}

    /** Mesa inexistente / de outro tenant (→ 404 table_not_found). */
    public static class TableNotFoundException extends RuntimeException {}

    /** Reserva fora do horário de funcionamento (→ 400 outside_hours). */
    public static class OutsideHoursException extends RuntimeException {}

    /** Status alvo desconhecido (→ 400 invalid_status). */
    public static class InvalidStatusException extends RuntimeException {}

    /** Transição de status inválida (→ 409 invalid_status_transition). */
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Conflito de slot (→ 409 conflict_slot). Carrega o conflito p/ o controller expor detalhes. */
    public static class ConflictException extends RuntimeException {
        private final transient ReservationConflict conflict;

        public ConflictException(ReservationConflict conflict) {
            this.conflict = conflict;
        }

        public ReservationConflict conflict() {
            return conflict;
        }
    }

    /**
     * Cria uma reserva (status inicial pendente). Valida mesa + janela de funcionamento; a duração
     * vem do config (snapshot). O repositório re-verifica o conflito na transação. Invalida o cache
     * de contexto da IA.
     */
    public Reservation create(UUID companyId, UUID tableId, UUID conversationId, UUID contactId,
                              String guestName, String guestPhone, Instant startAt, int numPeople,
                              String notes) {
        // Mesa precisa existir e ser do tenant.
        if (tableRepository.findById(companyId, tableId).isEmpty()) {
            throw new TableNotFoundException();
        }
        RestaurantReservationConfig config = configRepository.findByCompany(companyId);
        requireInsideHours(startAt, config);

        Reservation created;
        try {
            created = reservationRepository.insertReservation(companyId, tableId, conversationId,
                contactId, guestName, guestPhone, startAt, config.durationMinutes(), numPeople, notes);
        } catch (ReservationRepository.SlotConflictException e) {
            throw new ConflictException(e.conflict());
        }
        contextCache.invalidate(companyId);
        return created;
    }

    public List<Reservation> list(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                                  int limit, int offset) {
        return reservationRepository.listByCompany(companyId, status, dateFrom, dateTo, limit, offset);
    }

    public long count(UUID companyId, String status, Instant dateFrom, Instant dateTo) {
        return reservationRepository.countByCompany(companyId, status, dateFrom, dateTo);
    }

    public Optional<Reservation> get(UUID companyId, UUID id) {
        return reservationRepository.findById(companyId, id);
    }

    /**
     * Transiciona o status. Valida o alvo (enum) e a transição (decisão 2). Persiste e notifica o
     * cliente com o texto do novo status (decisão 3). A notificação é best-effort (não reverte).
     */
    @Transactional
    public Reservation updateStatus(UUID companyId, UUID id, String newStatusId) {
        ReservationStatus newStatus = ReservationStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        Reservation current = reservationRepository.findById(companyId, id)
            .orElseThrow(ReservationNotFoundException::new);
        ReservationStatus from = ReservationStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        reservationRepository.updateStatus(companyId, id, newStatus.id());

        // Notificação outbound do novo status (best-effort). Confirmada usa os dados da reserva.
        ZonedDateTime z = current.startAt().atZone(TENANT_ZONE);
        String text = newStatus.notificationText(
            DATE_FMT.format(z), TIME_FMT.format(z), current.tableLabel(), current.numPeople());
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return reservationRepository.findById(companyId, id).orElseThrow(ReservationNotFoundException::new);
    }

    /**
     * Valida que a reserva inteira (início → início+duração) cabe na janela de funcionamento, no
     * fuso do tenant. Não permite reserva que comece antes de opens_at nem que termine depois de
     * closes_at.
     */
    private void requireInsideHours(Instant startAt, RestaurantReservationConfig config) {
        ZonedDateTime start = startAt.atZone(TENANT_ZONE);
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = start.plusMinutes(config.durationMinutes()).toLocalTime();
        // Janela normal (não atravessa meia-noite — opens_at < closes_at garantido pelo config).
        boolean startsOk = !startTime.isBefore(config.opensAt());
        // Se a reserva termina exatamente em closes_at, ok; depois disso, não. endTime pode "voltar"
        // (passar de meia-noite) → trata como fora.
        boolean endsOk = !endTime.isAfter(config.closesAt()) && !endTime.isBefore(startTime);
        if (!startsOk || !endsOk) {
            throw new OutsideHoursException();
        }
    }
}
