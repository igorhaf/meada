package com.meada.profiles.lingerie.catalog;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.lingerie.catalog.LingerieProductService.DuplicateVariantException;
import com.meada.profiles.lingerie.catalog.LingerieProductService.InvalidCategoryException;
import com.meada.profiles.lingerie.catalog.LingerieProductService.InvalidSizeException;
import com.meada.profiles.lingerie.catalog.LingerieProductService.ProductInUseException;
import com.meada.profiles.lingerie.catalog.LingerieProductService.VariantInUseException;
import com.meada.profiles.lingerie.catalog.LingerieProductService.VariantNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o LingerieProductService (camada 8.21): create + audit, category inválida, update parcial,
 * toggle, delete em uso → 409; e o CRUD de VARIANTES (⭐ a grade tamanho×cor): add/update/toggle/
 * delete, duplicate_variant 409, invalid_size 400, variant_in_use 409, e a invalidação de cache.
 * Análogo ao AdegaMenuServiceTest, adaptado pras categorias de lingerie + a camada de variantes.
 */
class LingerieProductServiceTest extends AbstractIntegrationTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private LingerieProductService service;

    private static final UUID COMPANY = UUID.fromString("c8210000-0000-0000-0000-000000000091");
    private static final UUID USER = UUID.fromString("d8210000-0000-0000-0000-000000000091");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'lingerie')",
            COMPANY, "Lingerie Teste", "lingerie-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@lingerie.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita lingerie_product_created")
    void create_persistsAndAudits() {
        LingerieProduct p = service.create(COMPANY, USER, "Conjunto Renda", "Renda francesa", "conjuntos", 8990);
        assertThat(p.name()).isEqualTo("Conjunto Renda");
        assertThat(p.basePriceCents()).isEqualTo(8990);
        assertThat(p.available()).isTrue();

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'lingerie_product_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com categoria inválida → InvalidCategoryException")
    void create_invalidCategory() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", null, "fralda", 100))
            .isInstanceOf(InvalidCategoryException.class);
    }

    @Test
    @DisplayName("update parcial (só preço base) preserva os demais campos")
    void update_partial() {
        LingerieProduct p = service.create(COMPANY, USER, "Calcinha Cotton", "algodão", "calcinhas", 1990);
        LingerieProduct updated = service.update(COMPANY, USER, p.id(), null, null, null, 2490, null);
        assertThat(updated.basePriceCents()).isEqualTo(2490);
        assertThat(updated.name()).isEqualTo("Calcinha Cotton");   // preservado
        assertThat(updated.category()).isEqualTo("calcinhas");     // preservado
    }

    @Test
    @DisplayName("toggle desliga available")
    void toggle() {
        LingerieProduct p = service.create(COMPANY, USER, "Modelador Cinta", null, "modeladores", 12900);
        LingerieProduct off = service.toggle(COMPANY, USER, p.id(), false);
        assertThat(off.available()).isFalse();
    }

    // ---- Variantes (⭐ a grade tamanho×cor) ----------------------------------

    @Test
    @DisplayName("addVariant → persiste a variante (size válido) + audita + aparece no produto")
    void addVariant_persistsAndAudits() {
        LingerieProduct p = service.create(COMPANY, USER, "Sutiã Renda", null, "sutias", 6990);
        LingerieVariant v = service.addVariant(COMPANY, USER, p.id(), "M", "Preto", "SKU-1", 7490, 5);
        assertThat(v.size()).isEqualTo("M");
        assertThat(v.color()).isEqualTo("Preto");
        assertThat(v.priceCents()).isEqualTo(7490);
        assertThat(v.stockQty()).isEqualTo(5);
        assertThat(v.available()).isTrue();

        List<LingerieVariant> variants = service.listVariants(COMPANY, p.id());
        assertThat(variants).hasSize(1);

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'lingerie_variant_created' and entity_id = ?",
            Long.class, v.id());
        assertThat(audit).isEqualTo(1L);

        // produto hidratado já traz a variante embutida.
        assertThat(service.get(COMPANY, p.id()).orElseThrow().variants()).hasSize(1);
    }

    @Test
    @DisplayName("addVariant com tamanho inválido → InvalidSizeException")
    void addVariant_invalidSize() {
        LingerieProduct p = service.create(COMPANY, USER, "Sutiã", null, "sutias", 5000);
        assertThatThrownBy(() -> service.addVariant(COMPANY, USER, p.id(), "EXTRA", "Preto", null, null, 1))
            .isInstanceOf(InvalidSizeException.class);
    }

    @Test
    @DisplayName("addVariant duplicada (mesmo size+color) → DuplicateVariantException (409)")
    void addVariant_duplicate() {
        LingerieProduct p = service.create(COMPANY, USER, "Sutiã", null, "sutias", 5000);
        service.addVariant(COMPANY, USER, p.id(), "P", "Branco", null, null, 3);
        assertThatThrownBy(() -> service.addVariant(COMPANY, USER, p.id(), "P", "Branco", null, null, 9))
            .isInstanceOf(DuplicateVariantException.class);
    }

    @Test
    @DisplayName("updateVariant parcial (só estoque) preserva size/color; clearPrice volta a herdar o base")
    void updateVariant_partialAndClearPrice() {
        LingerieProduct p = service.create(COMPANY, USER, "Pijama", null, "pijamas", 9990);
        LingerieVariant v = service.addVariant(COMPANY, USER, p.id(), "G", "Rosa", null, 10990, 2);

        LingerieVariant stocked = service.updateVariant(COMPANY, USER, p.id(), v.id(),
            null, null, null, null, 8, null, false);
        assertThat(stocked.stockQty()).isEqualTo(8);
        assertThat(stocked.size()).isEqualTo("G");      // preservado
        assertThat(stocked.color()).isEqualTo("Rosa");  // preservado
        assertThat(stocked.priceCents()).isEqualTo(10990);

        // clearPrice=true → priceCents volta a null (herda o base do produto).
        LingerieVariant cleared = service.updateVariant(COMPANY, USER, p.id(), v.id(),
            null, null, null, null, null, null, true);
        assertThat(cleared.priceCents()).isNull();
    }

    @Test
    @DisplayName("toggleVariant desliga; deleteVariant remove; variante inexistente → VariantNotFoundException")
    void toggleAndDeleteVariant() {
        LingerieProduct p = service.create(COMPANY, USER, "Meia", null, "meias", 1990);
        LingerieVariant v = service.addVariant(COMPANY, USER, p.id(), "P", "Preto", null, null, 4);

        LingerieVariant off = service.toggleVariant(COMPANY, USER, p.id(), v.id(), false);
        assertThat(off.available()).isFalse();

        service.deleteVariant(COMPANY, USER, p.id(), v.id());
        assertThat(service.listVariants(COMPANY, p.id())).isEmpty();

        assertThatThrownBy(() -> service.deleteVariant(COMPANY, USER, p.id(), v.id()))
            .isInstanceOf(VariantNotFoundException.class);
    }

    @Test
    @DisplayName("delete de variante referenciada por pedido → VariantInUseException (409); delete do produto → ProductInUseException")
    void delete_inUse() {
        LingerieProduct p = service.create(COMPANY, USER, "Conjunto", null, "conjuntos", 8000);
        LingerieVariant v = service.addVariant(COMPANY, USER, p.id(), "M", "Vermelho", null, null, 10);

        // Semeia conversa+contato+pedido+order_item referenciando a variante.
        UUID contact = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, COMPANY, "+5511999990191", "C");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, COMPANY, contact, instance);
        UUID order = jdbcTemplate.queryForObject(
            "insert into lingerie_orders (company_id, conversation_id, contact_id, fulfillment, subtotal_cents, total_cents, delivery_address) "
                + "values (?, ?, ?, 'entrega', 8000, 8000, 'Rua X') returning id", UUID.class, COMPANY, conv, contact);
        jdbcTemplate.update("insert into lingerie_order_items (order_id, variant_id, qtd, unit_price_cents, "
            + "product_name_snapshot, size_snapshot, color_snapshot) values (?, ?, 1, 8000, 'Conjunto', 'M', 'Vermelho')",
            order, v.id());

        assertThatThrownBy(() -> service.deleteVariant(COMPANY, USER, p.id(), v.id()))
            .isInstanceOf(VariantInUseException.class);
        assertThatThrownBy(() -> service.delete(COMPANY, USER, p.id()))
            .isInstanceOf(ProductInUseException.class);
    }
}
