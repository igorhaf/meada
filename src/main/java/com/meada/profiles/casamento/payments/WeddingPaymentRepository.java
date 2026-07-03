package com.meada.profiles.casamento.payments;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code wedding_payments} (onda 1, backlog #1). service_role; escopo por company_id.
 * {@link #findDuePayments} alimenta o lembrete D-3 do WeddingReminderJob (idempotência por
 * reminded_due_date — remarcar o vencimento rearma).
 */
@Repository
public class WeddingPaymentRepository {

    private static final RowMapper<WeddingPayment> MAPPER = (rs, rn) -> {
        java.sql.Timestamp paidAt = rs.getTimestamp("paid_at");
        return new WeddingPayment(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"),
            (UUID) rs.getObject("proposal_id"),
            rs.getString("kind"),
            rs.getString("label"),
            rs.getDate("due_date").toLocalDate(),
            rs.getInt("amount_cents"),
            rs.getBoolean("paid"),
            paidAt == null ? null : paidAt.toInstant(),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    };

    private static final String COLS =
        "id, company_id, proposal_id, kind, label, due_date, amount_cents, paid, paid_at, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public WeddingPaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WeddingPayment> listByProposal(UUID companyId, UUID proposalId) {
        return jdbcTemplate.query(
            "select " + COLS + " from wedding_payments where company_id = ? and proposal_id = ? "
                + "order by due_date asc, created_at asc",
            MAPPER, companyId, proposalId);
    }

    public Optional<WeddingPayment> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from wedding_payments where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public WeddingPayment insert(UUID companyId, UUID proposalId, String kind, String label,
                                 LocalDate dueDate, int amountCents) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into wedding_payments (company_id, proposal_id, kind, label, due_date, amount_cents) "
                + "values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, proposalId, kind, label, Date.valueOf(dueDate), amountCents);
        return findById(companyId, id).orElseThrow();
    }

    /** PATCH parcial (kind/label/due_date/amount). label controlada por flag "provided". */
    public Optional<WeddingPayment> update(UUID companyId, UUID id, String kind, String label,
                                           boolean labelProvided, LocalDate dueDate, Integer amountCents) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (kind != null && !kind.isBlank()) { sets.add("kind = ?"); args.add(kind); }
        if (labelProvided) { sets.add("label = ?"); args.add(label); }
        if (dueDate != null) { sets.add("due_date = ?"); args.add(Date.valueOf(dueDate)); }
        if (amountCents != null) { sets.add("amount_cents = ?"); args.add(amountCents); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update wedding_payments set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /** Marca pago/não-pago. paid_at carimba (preserva se já pago) e zera ao desmarcar. */
    public Optional<WeddingPayment> setPaid(UUID companyId, UUID id, boolean paid) {
        int n = jdbcTemplate.update(
            "update wedding_payments set paid = ?, "
                + "paid_at = case when ? then coalesce(paid_at, now()) end, "
                + "updated_at = now() where company_id = ? and id = ?",
            paid, paid, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from wedding_payments where company_id = ? and id = ?", companyId, id) > 0;
    }

    /** GATE do fechamento (#1): existe 'sinal' NÃO pago no plano da proposta? */
    public boolean existsUnpaidSinal(UUID companyId, UUID proposalId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from wedding_payments where company_id = ? and proposal_id = ? "
                + "and kind = 'sinal' and paid = false",
            Integer.class, companyId, proposalId);
        return n != null && n > 0;
    }

    /** Registro DUE para o lembrete de parcela (janela D-3, proposta viva, toggle ligado). */
    public record DuePayment(UUID paymentId, UUID companyId, UUID conversationId, String customerName,
                             String kind, String label, LocalDate dueDate, int amountCents) {}

    /**
     * Parcelas/sinais NÃO pagos vencendo até {@code windowEnd} (inclusive), ainda não lembrados para
     * esse due_date, de proposta VIVA (não-terminal) de tenant casamento com o lembrete de parcela
     * LIGADO (ausência de config = ligado).
     */
    public List<DuePayment> findDuePayments(LocalDate windowEnd) {
        return jdbcTemplate.query(
            "select w.id as payment_id, w.company_id, p.conversation_id, p.customer_name, "
                + "w.kind, w.label, w.due_date, w.amount_cents "
                + "from wedding_payments w "
                + "join wedding_proposals p on p.id = w.proposal_id "
                + "join companies c on c.id = w.company_id "
                + "left join wedding_config cfg on cfg.company_id = w.company_id "
                + "where c.profile_id = 'casamento' "
                + "and coalesce(cfg.payment_reminder_enabled, true) "
                + "and w.paid = false "
                + "and w.due_date <= ? "
                + "and (w.reminded_due_date is null or w.reminded_due_date <> w.due_date) "
                + "and p.status not in ('realizada','recusada','cancelada') "
                + "order by w.company_id, w.due_date",
            (rs, rn) -> new DuePayment(
                (UUID) rs.getObject("payment_id"),
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"),
                rs.getString("customer_name"),
                rs.getString("kind"),
                rs.getString("label"),
                rs.getDate("due_date").toLocalDate(),
                rs.getInt("amount_cents")),
            Date.valueOf(windowEnd));
    }

    /** Marca o due_date como lembrado (idempotência por parcela+data — inclusive sem canal). */
    public void markReminded(UUID paymentId, LocalDate dueDate) {
        jdbcTemplate.update(
            "update wedding_payments set reminded_due_date = ?, updated_at = now() where id = ?",
            Date.valueOf(dueDate), paymentId);
    }
}
