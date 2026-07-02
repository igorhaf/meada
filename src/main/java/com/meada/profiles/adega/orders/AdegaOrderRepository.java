package com.meada.profiles.adega.orders;

import com.meada.profiles.adega.coupons.AdegaCoupon;
import com.meada.profiles.adega.coupons.AdegaCouponRepository;
import com.meada.profiles.adega.loyalty.AdegaLoyaltyConfig;
import com.meada.profiles.adega.loyalty.AdegaLoyaltyConfigRepository;
import com.meada.profiles.adega.menu.AdegaMenuOption;
import com.meada.profiles.adega.menu.AdegaMenuOptionRepository;
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
 * Acesso a {@code adega_orders} + {@code adega_order_items} + {@code adega_order_item_options}
 * (camada 8.9). Clone do chassi comida: {@code rejection_reason} no gate de aceite + opções/modifiers
 * por item, com recálculo do unit_price = base + Σ deltas (descarta o total da IA) + a ESCAPADA +18
 * ({@code age_confirmed} persistido no pedido — o service já barrou pedido sem o flag). Opera via
 * service_role; o escopo por company_id no WHERE é a defesa.
 */
@Repository
public class AdegaOrderRepository {

    /** Alguma opção pedida é inválida/indisponível/de outro item — o pedido NÃO é criado. */
    public static class InvalidOptionException extends RuntimeException {}

    private static final ZoneId BR = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbcTemplate;
    private final AdegaMenuOptionRepository optionRepository;
    private final AdegaCouponRepository couponRepository;
    private final AdegaLoyaltyConfigRepository loyaltyRepository;

    public AdegaOrderRepository(JdbcTemplate jdbcTemplate,
                                 AdegaMenuOptionRepository optionRepository,
                                 AdegaCouponRepository couponRepository,
                                 AdegaLoyaltyConfigRepository loyaltyRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.optionRepository = optionRepository;
        this.couponRepository = couponRepository;
        this.loyaltyRepository = loyaltyRepository;
    }

    private final RowMapper<AdegaOrderItemOption> ITEM_OPTION_MAPPER = (rs, rn) -> new AdegaOrderItemOption(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("menu_option_id"),
        rs.getString("group_label_snapshot"),
        rs.getString("option_label_snapshot"),
        rs.getInt("price_delta_cents"));

    /** Mapeia a row de order_item SEM as opções (carregadas à parte). */
    private final RowMapper<AdegaOrderItem> ITEM_MAPPER = (rs, rn) -> new AdegaOrderItem(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("menu_item_id"),
        rs.getString("item_name_snapshot"),
        rs.getInt("qtd"),
        rs.getInt("unit_price_cents"),
        List.of());

    /** Mapeia a row de order (sem os itens — carregados à parte). */
    private AdegaOrder mapOrder(java.sql.ResultSet rs, List<AdegaOrderItem> items) throws java.sql.SQLException {
        return new AdegaOrder(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("status"),
            rs.getInt("subtotal_cents"),
            rs.getInt("discount_cents"),
            rs.getInt("delivery_fee_cents"),
            rs.getInt("total_cents"),
            rs.getString("coupon_code_snapshot"),
            rs.getBoolean("loyalty_applied"),
            rs.getString("delivery_address"),
            rs.getString("notes"),
            rs.getString("rejection_reason"),
            rs.getBoolean("age_confirmed"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("status_updated_at").toInstant(),
            rs.getString("contact_name"),
            rs.getString("contact_phone"),
            items);
    }

    private static final String ORDER_SELECT =
        "select o.id, o.conversation_id, o.status, o.subtotal_cents, o.discount_cents, "
            + "o.delivery_fee_cents, o.total_cents, o.coupon_code_snapshot, o.loyalty_applied, "
            + "o.delivery_address, o.notes, o.rejection_reason, o.age_confirmed, "
            + "o.created_at, o.status_updated_at, ct.name as contact_name, ct.phone_number as contact_phone "
            + "from adega_orders o join contacts ct on ct.id = o.contact_id ";

    /** Lista pedidos do tenant (filtro opcional por status), paginado, mais recentes primeiro. */
    public List<AdegaOrder> listByCompany(UUID companyId, String status, int limit, int offset) {
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

        List<AdegaOrder> orders = jdbcTemplate.query(sql.toString(),
            (rs, rn) -> mapOrder(rs, List.of()), args.toArray());
        // Hidrata itens (e suas opções) de cada pedido (lista pequena — N+1 aceitável no Kanban).
        List<AdegaOrder> withItems = new ArrayList<>(orders.size());
        for (AdegaOrder o : orders) {
            withItems.add(withItems(o));
        }
        return withItems;
    }

    public long countByCompany(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder("select count(*) from adega_orders where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<AdegaOrder> findById(UUID companyId, UUID id) {
        Optional<AdegaOrder> base = jdbcTemplate.query(ORDER_SELECT + "where o.company_id = ? and o.id = ?",
                (rs, rn) -> mapOrder(rs, List.of()), companyId, id)
            .stream().findFirst();
        return base.map(this::withItems);
    }

    private AdegaOrder withItems(AdegaOrder o) {
        List<AdegaOrderItem> bare = jdbcTemplate.query(
            "select id, menu_item_id, item_name_snapshot, qtd, unit_price_cents "
                + "from adega_order_items where order_id = ? order by id", ITEM_MAPPER, o.id());
        List<AdegaOrderItem> withOpts = new ArrayList<>(bare.size());
        for (AdegaOrderItem it : bare) {
            List<AdegaOrderItemOption> options = jdbcTemplate.query(
                "select id, menu_option_id, group_label_snapshot, option_label_snapshot, price_delta_cents "
                    + "from adega_order_item_options where order_item_id = ? order by id",
                ITEM_OPTION_MAPPER, it.id());
            withOpts.add(new AdegaOrderItem(it.id(), it.menuItemId(), it.itemName(), it.qtd(),
                it.unitPriceCents(), options));
        }
        return new AdegaOrder(o.id(), o.conversationId(), o.status(), o.subtotalCents(),
            o.discountCents(), o.deliveryFeeCents(), o.totalCents(), o.couponCode(), o.loyaltyApplied(),
            o.deliveryAddress(), o.notes(), o.rejectionReason(),
            o.ageConfirmed(), o.createdAt(), o.statusUpdatedAt(), o.contactName(), o.contactPhone(), withOpts);
    }

    /**
     * Cria o pedido + itens + opções numa transação. Os preços/nomes são lidos do cardápio AGORA
     * (snapshot); para cada linha, {@code unit_price = base + Σ deltas} das opções escolhidas. O
     * subtotal é a soma de unit_price × qtd. Aplica cupom (best-effort: cupom inválido NÃO aborta —
     * apenas não desconta) + fidelidade (conta os entregues do contato ANTES de inserir o novo);
     * discount = min(subtotal, cupom+fidelidade); total = subtotal − discount + delivery_fee. Linhas
     * cujo menu_item não existe/não é do tenant são IGNORADAS (o handler já validou). Se alguma opção
     * pedida é inválida/indisponível/de outro item, lança {@link InvalidOptionException} (pedido NÃO
     * criado com opção fantasma). Lança IllegalArgumentException se, após filtrar, não sobrar linha.
     */
    @Transactional
    public AdegaOrder createOrder(UUID companyId, UUID conversationId, UUID contactId,
                                   String deliveryAddress, List<OrderLineInput> lines,
                                   String couponCode, int deliveryFeeCents, boolean ageConfirmed,
                                   String notes) {
        // Snapshot de preço+nome+opções por linha (lê do cardápio do tenant).
        record OptSnap(UUID menuOptionId, String groupLabel, String optionLabel, int delta) {}
        record Snap(UUID menuItemId, String name, int unitPrice, int qtd, List<OptSnap> options) {}

        List<Snap> snaps = new ArrayList<>();
        int subtotal = 0;
        for (OrderLineInput line : lines) {
            record Base(String name, int price) {}
            List<Base> found = jdbcTemplate.query(
                "select name, price_cents from adega_menu_items where company_id = ? and id = ?",
                (rs, rn) -> new Base(rs.getString("name"), rs.getInt("price_cents")),
                companyId, line.menuItemId());
            if (found.isEmpty()) {
                continue;   // item inexistente/de outro tenant: ignora a linha (defesa).
            }
            Base base = found.get(0);

            // Resolve as opções escolhidas (ESCAPADA 2). Tamanho divergente → opção fantasma.
            List<UUID> optionIds = line.optionIds() == null ? List.of() : line.optionIds();
            List<OptSnap> optSnaps = new ArrayList<>();
            int deltaSum = 0;
            if (!optionIds.isEmpty()) {
                List<AdegaMenuOption> resolved =
                    optionRepository.findByIdsForItem(companyId, line.menuItemId(), optionIds);
                if (resolved.size() != optionIds.size()) {
                    throw new InvalidOptionException();
                }
                for (AdegaMenuOption opt : resolved) {
                    optSnaps.add(new OptSnap(opt.id(), opt.groupLabel(), opt.optionLabel(), opt.priceDeltaCents()));
                    deltaSum += opt.priceDeltaCents();
                }
            }

            int unitPrice = base.price() + deltaSum;
            snaps.add(new Snap(line.menuItemId(), base.name(), unitPrice, line.qtd(), optSnaps));
            subtotal += unitPrice * line.qtd();
        }
        if (snaps.isEmpty()) {
            throw new IllegalArgumentException("nenhum item válido no pedido");
        }

        // Cupom (backlog #1, best-effort — inválido NÃO aborta, o pedido sai sem o desconto).
        UUID couponId = null;
        String couponSnapshot = null;
        int couponDiscount = 0;
        if (couponCode != null && !couponCode.isBlank()) {
            Optional<AdegaCoupon> maybe = couponRepository.findByCode(companyId, couponCode);
            if (maybe.isPresent()) {
                AdegaCoupon c = maybe.get();
                LocalDate today = LocalDate.now(BR);
                boolean valid = c.active()
                    && (c.validUntil() == null || !c.validUntil().isBefore(today))
                    && subtotal >= c.minOrderCents()
                    && (c.maxUses() == null || c.uses() < c.maxUses());
                if (valid) {
                    couponDiscount = "percent".equals(c.kind())
                        ? subtotal * c.value() / 100
                        : c.value();
                    couponId = c.id();
                    couponSnapshot = c.code();
                }
            }
        }

        // Fidelidade (backlog #2) — conta os pedidos ENTREGUES do contato ANTES de inserir o novo
        // (o novo não conta a si mesmo).
        boolean loyaltyApplied = false;
        int loyaltyDiscount = 0;
        AdegaLoyaltyConfig loyalty = loyaltyRepository.findByCompany(companyId);
        if (loyalty.enabled()) {
            long deliveredCount = countDeliveredForContact(companyId, contactId);
            if (deliveredCount > 0 && deliveredCount % loyalty.thresholdOrders() == 0) {
                loyaltyApplied = true;
                loyaltyDiscount = "percent".equals(loyalty.rewardKind())
                    ? subtotal * loyalty.rewardValue() / 100
                    : loyalty.rewardValue();
            }
        }

        // Desconto total clampado ao subtotal (total nunca negativo).
        int discount = Math.min(subtotal, couponDiscount + loyaltyDiscount);
        int total = subtotal - discount + deliveryFeeCents;

        // status default 'aguardando' (gate de aceite). age_confirmed persistido (ESCAPADA +18 —
        // o service já garantiu true; o banco tem NOT NULL como defesa final).
        UUID orderId = jdbcTemplate.queryForObject(
            "insert into adega_orders (company_id, conversation_id, contact_id, subtotal_cents, "
                + "discount_cents, delivery_fee_cents, total_cents, coupon_id, coupon_code_snapshot, "
                + "loyalty_applied, delivery_address, age_confirmed, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, conversationId, contactId, subtotal, discount, deliveryFeeCents,
            total, couponId, couponSnapshot, loyaltyApplied, deliveryAddress, ageConfirmed, notes);

        for (Snap s : snaps) {
            UUID orderItemId = jdbcTemplate.queryForObject(
                "insert into adega_order_items (order_id, menu_item_id, qtd, unit_price_cents, "
                    + "item_name_snapshot) values (?, ?, ?, ?, ?) returning id",
                UUID.class, orderId, s.menuItemId(), s.qtd(), s.unitPrice(), s.name());
            for (OptSnap opt : s.options()) {
                jdbcTemplate.update(
                    "insert into adega_order_item_options (order_item_id, menu_option_id, "
                        + "group_label_snapshot, option_label_snapshot, price_delta_cents) "
                        + "values (?, ?, ?, ?, ?)",
                    orderItemId, opt.menuOptionId(), opt.groupLabel(), opt.optionLabel(), opt.delta());
            }
        }

        // Incrementa uses do cupom aplicado (mesma transação).
        if (couponId != null) {
            couponRepository.incrementUses(companyId, couponId);
        }

        return findById(companyId, orderId).orElseThrow();
    }

    /**
     * Conta os pedidos ENTREGUES de um contato ({@code status = 'entregue'} — o terminal
     * não-recusado/não-cancelado do chassi adega). Usado pela fidelidade.
     */
    public long countDeliveredForContact(UUID companyId, UUID contactId) {
        Long n = jdbcTemplate.queryForObject(
            "select count(*) from adega_orders "
                + "where company_id = ? and contact_id = ? and status = 'entregue'",
            Long.class, companyId, contactId);
        return n == null ? 0L : n;
    }

    /**
     * Persiste a transição de status + status_updated_at. Service já validou a transição. Quando
     * {@code rejectionReason != null} (recusa — ESCAPADA 1), grava também o motivo.
     */
    public void updateStatus(UUID companyId, UUID id, String newStatus, String rejectionReason) {
        if (rejectionReason != null) {
            jdbcTemplate.update(
                "update adega_orders set status = ?, rejection_reason = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, rejectionReason, companyId, id);
        } else {
            jdbcTemplate.update(
                "update adega_orders set status = ?, status_updated_at = now() "
                    + "where company_id = ? and id = ?",
                newStatus, companyId, id);
        }
    }
}
