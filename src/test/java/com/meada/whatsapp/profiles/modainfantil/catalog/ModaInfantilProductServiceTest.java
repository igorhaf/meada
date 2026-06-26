package com.meada.whatsapp.profiles.modainfantil.catalog;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.modainfantil.catalog.ModaInfantilProductService.DuplicateVariantException;
import com.meada.whatsapp.profiles.modainfantil.catalog.ModaInfantilProductService.InvalidCategoryException;
import com.meada.whatsapp.profiles.modainfantil.catalog.ModaInfantilProductService.InvalidSizeException;
import com.meada.whatsapp.profiles.modainfantil.catalog.ModaInfantilProductService.ProductInUseException;
import com.meada.whatsapp.profiles.modainfantil.catalog.ModaInfantilProductService.VariantInUseException;
import com.meada.whatsapp.profiles.modainfantil.catalog.ModaInfantilProductService.VariantNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o ModaInfantilProductService (camada 8.22): create + audit, category inválida, update parcial,
 * toggle, delete em uso → 409; e o CRUD de VARIANTES (a grade faixa-etária×cor): add/update/toggle/
 * delete, duplicate_variant 409, invalid_size 400 (⭐ faixa etária KidsSize), variant_in_use 409, e a
 * invalidação de cache. Clone do LingerieProductServiceTest, adaptado às categorias + faixas etárias.
 */
class ModaInfantilProductServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ModaInfantilProductService service;

    private static final UUID COMPANY = UUID.fromString("c8220000-0000-0000-0000-000000000091");
    private static final UUID USER = UUID.fromString("d8220000-0000-0000-0000-000000000091");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'moda_infantil')",
            COMPANY, "Moda Infantil Teste", "moda-infantil-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@moda-infantil.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita moda_infantil_product_created")
    void create_persistsAndAudits() {
        ModaInfantilProduct p = service.create(COMPANY, USER, "Body Manga Longa", "100% algodão", "bebe", 5990);
        assertThat(p.name()).isEqualTo("Body Manga Longa");
        assertThat(p.basePriceCents()).isEqualTo(5990);
        assertThat(p.available()).isTrue();

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'moda_infantil_product_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com categoria inválida → InvalidCategoryException")
    void create_invalidCategory() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", null, "lingerie", 100))
            .isInstanceOf(InvalidCategoryException.class);
    }

    @Test
    @DisplayName("update parcial (só preço base) preserva os demais campos")
    void update_partial() {
        ModaInfantilProduct p = service.create(COMPANY, USER, "Camiseta", "fio penteado", "menino", 1990);
        ModaInfantilProduct updated = service.update(COMPANY, USER, p.id(), null, null, null, 2490, null);
        assertThat(updated.basePriceCents()).isEqualTo(2490);
        assertThat(updated.name()).isEqualTo("Camiseta");   // preservado
        assertThat(updated.category()).isEqualTo("menino");  // preservado
    }

    @Test
    @DisplayName("toggle desliga available")
    void toggle() {
        ModaInfantilProduct p = service.create(COMPANY, USER, "Tênis", null, "calcados", 12900);
        ModaInfantilProduct off = service.toggle(COMPANY, USER, p.id(), false);
        assertThat(off.available()).isFalse();
    }

    // ---- Variantes (a grade faixa-etária×cor) -------------------------------

    @Test
    @DisplayName("addVariant → persiste a variante (faixa etária válida) + audita + aparece no produto")
    void addVariant_persistsAndAudits() {
        ModaInfantilProduct p = service.create(COMPANY, USER, "Conjunto Verão", null, "menina", 6990);
        ModaInfantilVariant v = service.addVariant(COMPANY, USER, p.id(), "2a", "Rosa", "SKU-1", 7490, 5);
        assertThat(v.size()).isEqualTo("2a");
        assertThat(v.color()).isEqualTo("Rosa");
        assertThat(v.priceCents()).isEqualTo(7490);
        assertThat(v.stockQty()).isEqualTo(5);
        assertThat(v.available()).isTrue();

        List<ModaInfantilVariant> variants = service.listVariants(COMPANY, p.id());
        assertThat(variants).hasSize(1);

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'moda_infantil_variant_created' and entity_id = ?",
            Long.class, v.id());
        assertThat(audit).isEqualTo(1L);

        // produto hidratado já traz a variante embutida.
        assertThat(service.get(COMPANY, p.id()).orElseThrow().variants()).hasSize(1);
    }

    @Test
    @DisplayName("addVariant com faixa etária inválida → InvalidSizeException")
    void addVariant_invalidSize() {
        ModaInfantilProduct p = service.create(COMPANY, USER, "Body", null, "bebe", 5000);
        // 'M' é tamanho de lingerie, não faixa etária de moda infantil.
        assertThatThrownBy(() -> service.addVariant(COMPANY, USER, p.id(), "M", "Branco", null, null, 1))
            .isInstanceOf(InvalidSizeException.class);
    }

    @Test
    @DisplayName("addVariant duplicada (mesma faixa+cor) → DuplicateVariantException (409)")
    void addVariant_duplicate() {
        ModaInfantilProduct p = service.create(COMPANY, USER, "Body", null, "bebe", 5000);
        service.addVariant(COMPANY, USER, p.id(), "RN", "Branco", null, null, 3);
        assertThatThrownBy(() -> service.addVariant(COMPANY, USER, p.id(), "RN", "Branco", null, null, 9))
            .isInstanceOf(DuplicateVariantException.class);
    }

    @Test
    @DisplayName("updateVariant parcial (só estoque) preserva faixa/cor; clearPrice volta a herdar o base")
    void updateVariant_partialAndClearPrice() {
        ModaInfantilProduct p = service.create(COMPANY, USER, "Pijama", null, "pijamas", 9990);
        ModaInfantilVariant v = service.addVariant(COMPANY, USER, p.id(), "4a", "Azul", null, 10990, 2);

        ModaInfantilVariant stocked = service.updateVariant(COMPANY, USER, p.id(), v.id(),
            null, null, null, null, 8, null, false);
        assertThat(stocked.stockQty()).isEqualTo(8);
        assertThat(stocked.size()).isEqualTo("4a");     // preservado
        assertThat(stocked.color()).isEqualTo("Azul");  // preservado
        assertThat(stocked.priceCents()).isEqualTo(10990);

        // clearPrice=true → priceCents volta a null (herda o base do produto).
        ModaInfantilVariant cleared = service.updateVariant(COMPANY, USER, p.id(), v.id(),
            null, null, null, null, null, null, true);
        assertThat(cleared.priceCents()).isNull();
    }

    @Test
    @DisplayName("toggleVariant desliga; deleteVariant remove; variante inexistente → VariantNotFoundException")
    void toggleAndDeleteVariant() {
        ModaInfantilProduct p = service.create(COMPANY, USER, "Meia", null, "acessorios", 1990);
        ModaInfantilVariant v = service.addVariant(COMPANY, USER, p.id(), "1a", "Preto", null, null, 4);

        ModaInfantilVariant off = service.toggleVariant(COMPANY, USER, p.id(), v.id(), false);
        assertThat(off.available()).isFalse();

        service.deleteVariant(COMPANY, USER, p.id(), v.id());
        assertThat(service.listVariants(COMPANY, p.id())).isEmpty();

        assertThatThrownBy(() -> service.deleteVariant(COMPANY, USER, p.id(), v.id()))
            .isInstanceOf(VariantNotFoundException.class);
    }

    @Test
    @DisplayName("delete de variante referenciada por pedido → VariantInUseException (409); delete do produto → ProductInUseException")
    void delete_inUse() {
        ModaInfantilProduct p = service.create(COMPANY, USER, "Kit Body", null, "kits", 8000);
        ModaInfantilVariant v = service.addVariant(COMPANY, USER, p.id(), "0-3m", "Cinza", null, null, 10);

        // Semeia conversa+contato+pedido+order_item referenciando a variante.
        UUID contact = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, COMPANY, "+5511999990291", "C");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, COMPANY, contact, instance);
        UUID order = jdbcTemplate.queryForObject(
            "insert into moda_infantil_orders (company_id, conversation_id, contact_id, fulfillment, subtotal_cents, total_cents, delivery_address) "
                + "values (?, ?, ?, 'entrega', 8000, 8000, 'Rua X') returning id", UUID.class, COMPANY, conv, contact);
        jdbcTemplate.update("insert into moda_infantil_order_items (order_id, variant_id, qtd, unit_price_cents, "
            + "product_name_snapshot, size_snapshot, color_snapshot) values (?, ?, 1, 8000, 'Kit Body', '0-3m', 'Cinza')",
            order, v.id());

        assertThatThrownBy(() -> service.deleteVariant(COMPANY, USER, p.id(), v.id()))
            .isInstanceOf(VariantInUseException.class);
        assertThatThrownBy(() -> service.delete(COMPANY, USER, p.id()))
            .isInstanceOf(ProductInUseException.class);
    }
}
