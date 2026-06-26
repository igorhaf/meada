package com.meada.whatsapp.profiles.lingerie.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Acesso a {@code lingerie_variants} (camada 8.21, ⭐ chassi de varejo). Análogo ao
 * {@link com.meada.whatsapp.profiles.adega.menu.AdegaMenuOptionRepository}, mas a variante é o SKU
 * com preço+estoque próprios. A operação CHAVE desta SM é {@link #decrementStock}: o UPDATE
 * condicional {@code stock_qty >= qtd} que fecha a janela de corrida (duas compras concorrentes da
 * última unidade — só uma decrementa). Opera via service_role; o escopo por company_id no WHERE é a
 * defesa.
 */
@Repository
public class LingerieVariantRepository {

    private static final String COLS =
        "id, product_id, size, color, sku, price_cents, stock_qty, available, created_at, updated_at";

    private static final RowMapper<LingerieVariant> MAPPER = (rs, rn) -> new LingerieVariant(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("product_id"),
        rs.getString("size"),
        rs.getString("color"),
        rs.getString("sku"),
        (Integer) rs.getObject("price_cents"),
        rs.getInt("stock_qty"),
        rs.getBoolean("available"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public LingerieVariantRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Variantes de um produto (qualquer disponibilidade), ordenadas por tamanho/cor. */
    public List<LingerieVariant> listByProduct(UUID companyId, UUID productId) {
        return jdbcTemplate.query(
            "select " + COLS + " from lingerie_variants "
                + "where company_id = ? and product_id = ? order by size asc, color asc, created_at asc",
            MAPPER, companyId, productId);
    }

    public Optional<LingerieVariant> findById(UUID companyId, UUID productId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from lingerie_variants "
                    + "where company_id = ? and product_id = ? and id = ?",
                MAPPER, companyId, productId, id)
            .stream().findFirst();
    }

    /**
     * Resolve as variantes do tenant cujo id está em {@code variantIds} (qualquer produto). Usado na
     * criação do pedido para o snapshot/recálculo. A disponibilidade é validada no repositório de
     * pedido (precisa do produto também). Lista vazia de entrada → lista vazia.
     */
    public List<LingerieVariant> findByIdsForOrder(UUID companyId, Collection<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return List.of();
        }
        String placeholders = variantIds.stream().map(v -> "?").collect(Collectors.joining(", "));
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        args.addAll(variantIds);
        return jdbcTemplate.query(
            "select " + COLS + " from lingerie_variants "
                + "where company_id = ? and id in (" + placeholders + ")",
            MAPPER, args.toArray());
    }

    public LingerieVariant insert(UUID companyId, UUID productId, String size, String color,
                                   String sku, Integer priceCents, int stockQty) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into lingerie_variants (company_id, product_id, size, color, sku, price_cents, "
                + "stock_qty) values (?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, productId, size.trim(), color.trim(), sku, priceCents, stockQty);
        return findById(companyId, productId, id).orElseThrow();
    }

    /**
     * Atualiza campos não-null (PATCH parcial). Inclui ajuste de estoque ({@code stockQty}) e de
     * preço ({@code priceCents}; para HERDAR o base de novo, mandar -1 que o service traduz pra
     * "limpar" — aqui só repassamos o valor recebido). Retorna a variante atualizada, ou empty se
     * não existir/pertencer ao produto+tenant.
     */
    public Optional<LingerieVariant> update(UUID companyId, UUID productId, UUID id, String size,
                                             String color, String sku, Integer priceCents,
                                             Integer stockQty, Boolean available, boolean clearPrice) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (size != null && !size.isBlank()) { sets.add("size = ?"); args.add(size.trim()); }
        if (color != null && !color.isBlank()) { sets.add("color = ?"); args.add(color.trim()); }
        // sku: null = não mexe. Para limpar, o frontend manda string vazia (vira null aqui? não —
        // mantemos a string; o service decide). Aqui repassamos o valor recebido se não-null.
        if (sku != null) { sets.add("sku = ?"); args.add(sku.isBlank() ? null : sku.trim()); }
        if (clearPrice) {
            sets.add("price_cents = null");
        } else if (priceCents != null) {
            sets.add("price_cents = ?"); args.add(priceCents);
        }
        if (stockQty != null) { sets.add("stock_qty = ?"); args.add(stockQty); }
        if (available != null) { sets.add("available = ?"); args.add(available); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(productId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update lingerie_variants set " + String.join(", ", sets)
                    + " where company_id = ? and product_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, productId, id);
    }

    /** Atalho dedicado para o toggle de disponibilidade. */
    public Optional<LingerieVariant> toggle(UUID companyId, UUID productId, UUID id, boolean available) {
        int n = jdbcTemplate.update(
            "update lingerie_variants set available = ?, updated_at = now() "
                + "where company_id = ? and product_id = ? and id = ?",
            available, companyId, productId, id);
        return n == 0 ? Optional.empty() : findById(companyId, productId, id);
    }

    /** Hard delete. Lança DataIntegrityViolation se houver order_item referenciando (FK restrict). */
    public boolean delete(UUID companyId, UUID productId, UUID id) {
        return jdbcTemplate.update(
            "delete from lingerie_variants where company_id = ? and product_id = ? and id = ?",
            companyId, productId, id) > 0;
    }

    /**
     * ⭐ DECREMENTO TRANSACIONAL DE ESTOQUE (o coração desta SM). UPDATE condicional: só decrementa
     * se {@code stock_qty >= qtd} (a variante tem estoque suficiente). Retorna {@code true} se a
     * linha foi decrementada, {@code false} se 0 linhas afetadas (estoque insuficiente / variante
     * inexistente / de outro tenant). O {@code false} sinaliza out-of-stock ao chamador, que ABORTA o
     * pedido inteiro (rollback do @Transactional). Esta condicional fecha a janela de corrida da
     * última unidade.
     */
    public boolean decrementStock(UUID companyId, UUID variantId, int qtd) {
        int n = jdbcTemplate.update(
            "update lingerie_variants set stock_qty = stock_qty - ?, updated_at = now() "
                + "where id = ? and company_id = ? and stock_qty >= ?",
            qtd, variantId, companyId, qtd);
        return n > 0;
    }
}
