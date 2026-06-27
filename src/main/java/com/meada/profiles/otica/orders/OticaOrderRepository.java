package com.meada.profiles.otica.orders;

import com.meada.profiles.otica.catalog.OticaCatalogOption;
import com.meada.profiles.otica.catalog.OticaCatalogOptionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code otica_orders} + {@code otica_order_items} + {@code otica_order_item_options} (camada
 * 8.12, FLUXO B). Clone de {@link com.meada.profiles.floricultura.orders.FloriculturaOrderRepository}
 * com as escapadas da ótica: (1) recálculo unit_price = base + Σ deltas, total = subtotal (RETIRADA,
 * sem taxa); (2) VALIDAÇÃO DE PRAZO (ready_date condicional — só obrigatória se houver item sob
 * encomenda — e ready_date >= hoje + MAX(lead dos itens sob encomenda, fallback default)); (3) dados
 * de RECEITA persistidos AS-IS (a IA registra, NÃO interpreta o grau). Opera via service_role; o
 * escopo por company_id no WHERE é a defesa.
 */
@Repository
public class OticaOrderRepository {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    /** Alguma opção pedida é inválida/indisponível/de outro item — o pedido NÃO é criado. */
    public static class InvalidOptionException extends RuntimeException {}

    /**
     * Há item sob encomenda mas a ready_date é nula ou anterior à primeira data possível
     * (hoje + MAX(lead)). Carrega a {@code earliest} (primeira data possível) p/ a resposta 422.
     */
    public static class LeadTimeViolationException extends RuntimeException {
        private final transient LocalDate earliest;

        public LeadTimeViolationException(LocalDate earliest) {
            this.earliest = earliest;
        }

        public LocalDate earliest() {
            return earliest;
        }
    }

    private final JdbcTemplate jdbcTemplate;
    private final OticaCatalogOptionRepository optionRepository;

    public OticaOrderRepository(JdbcTemplate jdbcTemplate, OticaCatalogOptionRepository optionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.optionRepository = optionRepository;
    }

    private final RowMapper<OticaOrderItemOption> ITEM_OPTION_MAPPER = (rs, rn) -> new OticaOrderItemOption(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("catalog_option_id"),
        rs.getString("group_label_snapshot"),
        rs.getString("option_label_snapshot"),
        rs.getInt("price_delta_cents"));

    /** Mapeia a row de order_item SEM as opções (carregadas à parte). */
    private final RowMapper<OticaOrderItem> ITEM_MAPPER = (rs, rn) -> new OticaOrderItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("catalog_item_id"),
        rs.getString("item_name_snapshot"),
        rs.getInt("qtd"),
        rs.getInt("unit_price_cents"),
        rs.getBoolean("made_to_order_snapshot"),
        List.of());

    /** Mapeia a row de order (sem os itens — carregados à parte). */
    private OticaOrder mapOrder(java.sql.ResultSet rs, List<OticaOrderItem> items) throws java.sql.SQLException {
        java.sql.Date rd = rs.getDate("ready_date");
        return new OticaOrder(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("status"),
            rs.getInt("subtotal_cents"),
            rs.getInt("total_cents"),
            rd == null ? null : rd.toLocalDate(),
            rs.getString("notes"),
            rs.getString("rejection_reason"),
            rs.getBigDecimal("rx_od_spherical"),
            rs.getBigDecimal("rx_od_cylindrical"),
            (Integer) rs.getObject("rx_od_axis"),
            rs.getBigDecimal("rx_oe_spherical"),
            rs.getBigDecimal("rx_oe_cylindrical"),
            (Integer) rs.getObject("rx_oe_axis"),
            rs.getBigDecimal("rx_pd"),
            rs.getBoolean("prescription_pending"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("status_updated_at").toInstant(),
            rs.getString("contact_name"),
            rs.getString("contact_phone"),
            items);
    }

    private static final String ORDER_SELECT =
        "select o.id, o.conversation_id, o.status, o.subtotal_cents, o.total_cents, o.ready_date, "
            + "o.notes, o.rejection_reason, o.rx_od_spherical, o.rx_od_cylindrical, o.rx_od_axis, "
            + "o.rx_oe_spherical, o.rx_oe_cylindrical, o.rx_oe_axis, o.rx_pd, o.prescription_pending, "
            + "o.created_at, o.status_updated_at, ct.name as contact_name, ct.phone_number as contact_phone "
            + "from otica_orders o join contacts ct on ct.id = o.contact_id ";

    /** Lista pedidos do tenant (filtro opcional por status), paginado, mais recentes primeiro. */
    public List<OticaOrder> listByCompany(UUID companyId, String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder(ORDER_SELECT + "where o.company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and o.status = ?");
            args.add(status);
        }
        sql.append(" order by o.created_at desc limit ? offset ?");
        args.add(limit);
        args.add(offset);

        List<OticaOrder> orders = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapOrder(rs, List.of()), args.toArray());
        List<OticaOrder> withItems = new ArrayList<>(orders.size());
        for (OticaOrder o : orders) {
            withItems.add(withItems(o));
        }
        return withItems;
    }

    public long countByCompany(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder("select count(*) from otica_orders where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<OticaOrder> findById(UUID companyId, UUID id) {
        Optional<OticaOrder> base = jdbcTemplate.query(ORDER_SELECT + "where o.company_id = ? and o.id = ?",
                (rs, rn) -> mapOrder(rs, List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withItems);
    }

    private OticaOrder withItems(OticaOrder o) {
        List<OticaOrderItem> bare = jdbcTemplate.query(
            "select id, catalog_item_id, item_name_snapshot, qtd, unit_price_cents, made_to_order_snapshot "
                + "from otica_order_items where order_id = ? order by id", ITEM_MAPPER, o.id());
        List<OticaOrderItem> withOpts = new ArrayList<>(bare.size());
        for (OticaOrderItem it : bare) {
            List<OticaOrderItemOption> options = jdbcTemplate.query(
                "select id, catalog_option_id, group_label_snapshot, option_label_snapshot, price_delta_cents "
                    + "from otica_order_item_options where order_item_id = ? order by id",
                ITEM_OPTION_MAPPER, it.id());
            withOpts.add(new OticaOrderItem(it.id(), it.catalogItemId(), it.itemName(), it.qtd(),
                it.unitPriceCents(), it.madeToOrder(), options));
        }
        return new OticaOrder(o.id(), o.conversationId(), o.status(), o.subtotalCents(), o.totalCents(),
            o.readyDate(), o.notes(), o.rejectionReason(), o.rxOdSpherical(), o.rxOdCylindrical(), o.rxOdAxis(),
            o.rxOeSpherical(), o.rxOeCylindrical(), o.rxOeAxis(), o.rxPd(), o.prescriptionPending(),
            o.createdAt(), o.statusUpdatedAt(), o.contactName(), o.contactPhone(), withOpts);
    }

    /**
     * Cria o pedido + itens + opções numa transação. Snapshot de preço/nome/made_to_order por linha;
     * para cada linha, {@code unit_price = base + Σ deltas} das opções escolhidas. subtotal = Σ
     * unit_price × qtd; total = subtotal (RETIRADA, sem taxa nesta SM). Lança
     * {@link InvalidOptionException} se alguma opção pedida é inválida; {@link IllegalArgumentException}
     * se, após filtrar, não sobrar linha.
     *
     * <p>VALIDAÇÃO DE PRAZO: se ALGUM item é sob encomenda, computa earliest = hoje + MAX(lead dos
     * itens sob encomenda, com fallback {@code leadDefault}); se {@code readyDate} for null ou anterior
     * a earliest → {@link LeadTimeViolationException}. Se só houver acessório (nenhum sob encomenda),
     * {@code readyDate} pode ser null (vai como null).
     *
     * <p>Os campos de RECEITA são persistidos AS-IS (a IA registra, NÃO interpreta o grau).
     */
    @Transactional
    public OticaOrder createOrder(UUID companyId, UUID conversationId, UUID contactId,
                                  List<OticaOrderLineInput> lines, String notes, LocalDate readyDate,
                                  OticaPrescription rx, int leadDefault) {
        record OptSnap(UUID catalogOptionId, String groupLabel, String optionLabel, int delta) {}
        record Snap(UUID catalogItemId, String name, int unitPrice, int qtd, boolean madeToOrder,
                    Integer leadTimeDays, List<OptSnap> options) {}

        List<Snap> snaps = new ArrayList<>();
        int subtotal = 0;
        for (OticaOrderLineInput line : lines) {
            record Base(String name, int price, boolean madeToOrder, Integer leadTimeDays) {}
            List<Base> found = jdbcTemplate.query(
                "select name, price_cents, made_to_order, lead_time_days from otica_catalog_items "
                    + "where company_id = ? and id = ?",
                (rs, rn) -> new Base(rs.getString("name"), rs.getInt("price_cents"),
                    rs.getBoolean("made_to_order"),
                    rs.getObject("lead_time_days") == null ? null : rs.getInt("lead_time_days")),
                companyId, line.catalogItemId());
            if (found.isEmpty()) {
                continue;   // item inexistente/de outro tenant: ignora a linha (defesa).
            }
            Base base = found.get(0);

            List<UUID> optionIds = line.optionIds() == null ? List.of() : line.optionIds();
            List<OptSnap> optSnaps = new ArrayList<>();
            int deltaSum = 0;
            if (!optionIds.isEmpty()) {
                List<OticaCatalogOption> resolved =
                    optionRepository.findByIdsForItem(companyId, line.catalogItemId(), optionIds);
                if (resolved.size() != optionIds.size()) {
                    throw new InvalidOptionException();
                }
                for (OticaCatalogOption opt : resolved) {
                    optSnaps.add(new OptSnap(opt.id(), opt.groupLabel(), opt.optionLabel(), opt.priceDeltaCents()));
                    deltaSum += opt.priceDeltaCents();
                }
            }

            int unitPrice = base.price() + deltaSum;
            snaps.add(new Snap(line.catalogItemId(), base.name(), unitPrice, line.qtd(),
                base.madeToOrder(), base.leadTimeDays(), optSnaps));
            subtotal += unitPrice * line.qtd();
        }
        if (snaps.isEmpty()) {
            throw new IllegalArgumentException("nenhum item válido no pedido");
        }

        // VALIDAÇÃO DE PRAZO (escapada): só se algum item é sob encomenda.
        boolean anyMadeToOrder = snaps.stream().anyMatch(Snap::madeToOrder);
        LocalDate persistedReadyDate = null;
        if (anyMadeToOrder) {
            int maxLead = snaps.stream()
                .filter(Snap::madeToOrder)
                .mapToInt(s -> s.leadTimeDays() == null ? leadDefault : s.leadTimeDays())
                .max()
                .orElse(leadDefault);
            LocalDate today = LocalDate.now(TENANT_ZONE);
            LocalDate earliest = today.plusDays(maxLead);
            if (readyDate == null || readyDate.isBefore(earliest)) {
                throw new LeadTimeViolationException(earliest);
            }
            persistedReadyDate = readyDate;
        } else if (readyDate != null) {
            // Só acessório, mas o cliente sugeriu uma data → respeita (informativo).
            persistedReadyDate = readyDate;
        }

        int total = subtotal;   // RETIRADA — sem taxa de entrega nesta SM.

        UUID orderId = jdbcTemplate.queryForObject(
            "insert into otica_orders (company_id, conversation_id, contact_id, subtotal_cents, total_cents, "
                + "ready_date, notes, rx_od_spherical, rx_od_cylindrical, rx_od_axis, rx_oe_spherical, "
                + "rx_oe_cylindrical, rx_oe_axis, rx_pd, prescription_pending) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, conversationId, contactId, subtotal, total,
            persistedReadyDate == null ? null : java.sql.Date.valueOf(persistedReadyDate), notes,
            rx.odSpherical(), rx.odCylindrical(), rx.odAxis(),
            rx.oeSpherical(), rx.oeCylindrical(), rx.oeAxis(), rx.pd(), rx.pending());

        for (Snap s : snaps) {
            UUID orderItemId = jdbcTemplate.queryForObject(
                "insert into otica_order_items (order_id, catalog_item_id, qtd, unit_price_cents, "
                    + "item_name_snapshot, made_to_order_snapshot) values (?, ?, ?, ?, ?, ?) returning id",
                UUID.class, orderId, s.catalogItemId(), s.qtd(), s.unitPrice(), s.name(), s.madeToOrder());
            for (OptSnap opt : s.options()) {
                jdbcTemplate.update(
                    "insert into otica_order_item_options (order_item_id, catalog_option_id, "
                        + "group_label_snapshot, option_label_snapshot, price_delta_cents) "
                        + "values (?, ?, ?, ?, ?)",
                    orderItemId, opt.catalogOptionId(), opt.groupLabel(), opt.optionLabel(), opt.delta());
            }
        }
        return findById(companyId, orderId).orElseThrow();
    }

    /**
     * Persiste a transição de status + status_updated_at. Service já validou a transição. Quando
     * {@code rejectionReason != null} (recusa), grava também o motivo.
     */
    public void updateStatus(UUID companyId, UUID id, String newStatus, String rejectionReason) {
        if (rejectionReason != null) {
            jdbcTemplate.update(
                "update otica_orders set status = ?, rejection_reason = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, rejectionReason, companyId, id);
        } else {
            jdbcTemplate.update(
                "update otica_orders set status = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }
}
